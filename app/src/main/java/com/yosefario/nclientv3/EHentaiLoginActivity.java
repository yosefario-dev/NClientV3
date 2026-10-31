package com.yosefario.nclientv3;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiAuth;
import com.yosefario.nclientv3.components.activities.GeneralActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.StorageController;
import org.mozilla.geckoview.WebExtension;

public class EHentaiLoginActivity extends GeneralActivity {
    private static final String LOGIN_URL = "https://forums.e-hentai.org/index.php?act=Login&CODE=00";
    private static final String EXTENSION_URI = "resource://android/assets/eh_login/";
    private static final String EXTENSION_ID = "eh-login@nclientv3";
    private static final String NATIVE_APP = "ehlogin";

    private static GeckoRuntime runtime;
    private static WebExtension extension;

    private TextInputEditText usernameField;
    private TextInputEditText passwordField;
    private MaterialCheckBox invisibleField;
    private Dialog dialog;
    private GeckoView geckoView;
    private GeckoSession session;
    private View progressOverlay;
    private View cover;
    private TextView challengeTitle;

    private String username;
    private String password;
    private boolean invisible;
    private boolean submitted = false;
    private boolean captured = false;
    private boolean revealing = false;
    private boolean revealed = false;
    private int stableFrames = 0;

    private final GeckoSession.ProgressDelegate progressDelegate = new GeckoSession.ProgressDelegate() {
        @Override
        public void onPageStart(@NonNull GeckoSession s, @NonNull String url) {
            coverAgain();
        }
    };

    private final WebExtension.MessageDelegate messageDelegate = new WebExtension.MessageDelegate() {
        @Nullable
        @Override
        public GeckoResult<Object> onMessage(@NonNull String nativeApp, @NonNull Object message, @NonNull WebExtension.MessageSender sender) {
            if (!(message instanceof JSONObject) || captured || session == null) return null;
            JSONObject msg = (JSONObject) message;
            switch (msg.optString("type")) {
                case "cookies":
                    if (EHentaiAuth.storeFromCookieString(EHentaiLoginActivity.this, msg.optString("value"))) onSuccess();
                    return null;
                case "theme":
                    return GeckoResult.fromValue(themeReply());
                case "captcha":
                    runOnUiThread(() -> showWidget());
                    return null;
                case "solved":
                    runOnUiThread(() -> {
                        if (geckoView != null) geckoView.postDelayed(() -> coverAgain(), 300);
                    });
                    return null;
                case "form":
                    if (submitted) {
                        onFailure();
                        return GeckoResult.fromValue("{}");
                    }
                    submitted = true;
                    return GeckoResult.fromValue(credentials().toString());
                default:
                    return null;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ehentai_login);
        setUpToolbar();

        usernameField = findViewById(R.id.eh_username);
        passwordField = findViewById(R.id.eh_password);
        invisibleField = findViewById(R.id.eh_invisible);
        findViewById(R.id.eh_login_button).setOnClickListener(v -> startLogin());
        findViewById(R.id.eh_cookie_login_button).setOnClickListener(v -> showCookieImportDialog());
    }

    private static GeckoRuntime runtime(Context context) {
        if (runtime == null) runtime = GeckoRuntime.create(context.getApplicationContext());
        runtime.getSettings().setPreferredColorScheme(isNight(context)
            ? GeckoRuntimeSettings.COLOR_SCHEME_DARK
            : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
        return runtime;
    }

    private static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void startLogin() {
        username = usernameField.getText() == null ? "" : usernameField.getText().toString().trim();
        password = passwordField.getText() == null ? "" : passwordField.getText().toString();
        if (username.isEmpty() || password.isEmpty()) return;
        invisible = invisibleField.isChecked();
        submitted = false;
        captured = false;
        revealing = false;
        revealed = false;
        stableFrames = 0;

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_ehentai_webview, null);
        geckoView = content.findViewById(R.id.geckoview);
        progressOverlay = content.findViewById(R.id.progress_overlay);
        cover = content.findViewById(R.id.eh_cover);
        challengeTitle = content.findViewById(R.id.eh_challenge_title);

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(content);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.setOnDismissListener(d -> closeSession());
        dialog = sheet;
        dialog.show();

        GeckoRuntime rt = runtime(this);
        GeckoResult<WebExtension> ready = extension != null
            ? GeckoResult.fromValue(extension)
            : rt.getWebExtensionController().ensureBuiltIn(EXTENSION_URI, EXTENSION_ID);
        ready.accept(ext -> {
            extension = ext;
            if (dialog == null || !dialog.isShowing()) return;
            ext.setMessageDelegate(messageDelegate, NATIVE_APP);
            rt.getStorageController()
                .clearDataFromBaseDomain("e-hentai.org", StorageController.ClearFlags.COOKIES)
                .accept(v -> openSession(rt, ext), e -> openSession(rt, ext));
        }, e -> onFailure());
    }

    private void openSession(GeckoRuntime rt, WebExtension ext) {
        if (dialog == null || !dialog.isShowing() || geckoView == null) return;
        session = new GeckoSession();
        session.getWebExtensionController().setMessageDelegate(ext, messageDelegate, NATIVE_APP);
        session.setProgressDelegate(progressDelegate);
        session.open(rt);
        int surface = MaterialColors.getColor(geckoView, com.google.android.material.R.attr.colorSurfaceContainerLow);
        geckoView.coverUntilFirstPaint(surface);
        geckoView.setSession(session);
        session.getCompositorController().setClearColor(surface);
        session.loadUri(LOGIN_URL);
    }

    private String themeReply() {
        JSONObject reply = new JSONObject();
        try {
            int surface = geckoView == null ? 0xFFFFFFFF
                : MaterialColors.getColor(geckoView, com.google.android.material.R.attr.colorSurfaceContainerLow);
            reply.put("bg", String.format("#%06X", surface & 0xFFFFFF));
            reply.put("dark", isNight(this));
        } catch (JSONException ignored) {
        }
        return reply.toString();
    }

    private JSONObject credentials() {
        JSONObject reply = new JSONObject();
        try {
            reply.put("u", username);
            reply.put("p", password);
            reply.put("inv", invisible);
        } catch (JSONException ignored) {
        }
        return reply;
    }

    private void showCookieImportDialog() {
        TextInputEditText input = new TextInputEditText(this);
        input.setHint(R.string.eh_paste_cookies_hint);
        input.setSingleLine(false);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.eh_paste_cookies)
            .setView(input)
            .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
            .setPositiveButton(R.string.ok, (d, w) -> {
                CharSequence cs = input.getText();
                String cookies = cs == null ? "" : cs.toString().trim();
                if (EHentaiAuth.storeFromCookieString(this, cookies)) {
                    refreshIgneousAsync();
                    Toast.makeText(this, R.string.eh_login_success, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, R.string.eh_login_failed, Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void refreshIgneousAsync() {
        Context appCtx = getApplicationContext();
        new Thread(() -> EHentaiAuth.refreshIgneous(appCtx)).start();
    }

    private void showWidget() {
        if (geckoView == null || cover == null || revealing) return;
        revealing = true;
        pollFrame(0);
    }

    private void pollFrame(int attempt) {
        if (geckoView == null || cover == null || session == null) return;
        if (attempt > 40) {
            revealNow();
            return;
        }
        geckoView.capturePixels().accept(bitmap -> {
            boolean drawn = bitmap != null && hasWidgetPixels(bitmap);
            if (bitmap != null) bitmap.recycle();
            if (drawn) {
                if (++stableFrames >= 2) revealNow();
                else geckoView.postDelayed(() -> pollFrame(attempt + 1), 250);
            } else {
                stableFrames = 0;
                geckoView.postDelayed(() -> pollFrame(attempt + 1), 250);
            }
        }, e -> {
            if (geckoView != null) geckoView.postDelayed(() -> pollFrame(attempt + 1), 400);
        });
    }

    private static boolean hasWidgetPixels(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w < 20 || h < 20) return false;
        int bg = bitmap.getPixel(2, 2);
        int differing = 0, samples = 0;
        for (int y = h / 6; y < h - h / 6; y += Math.max(1, h / 24)) {
            for (int x = w / 20; x < w - w / 20; x += Math.max(1, w / 48)) {
                samples++;
                int p = bitmap.getPixel(x, y);
                int dr = Math.abs(((p >> 16) & 0xFF) - ((bg >> 16) & 0xFF));
                int dg = Math.abs(((p >> 8) & 0xFF) - ((bg >> 8) & 0xFF));
                int db = Math.abs((p & 0xFF) - (bg & 0xFF));
                if (dr + dg + db > 60) differing++;
            }
        }
        return samples > 0 && differing * 100 / samples >= 3;
    }

    private void revealNow() {
        if (cover == null || revealed) return;
        revealed = true;
        swapTitle(R.string.eh_verify_human);
        cover.animate().cancel();
        cover.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (cover != null && revealed) cover.setVisibility(View.GONE);
        }).start();
    }

    private void coverAgain() {
        if (cover == null || !revealed) return;
        revealed = false;
        revealing = false;
        stableFrames = 0;
        if (progressOverlay != null) progressOverlay.setVisibility(View.VISIBLE);
        cover.animate().cancel();
        cover.setVisibility(View.VISIBLE);
        cover.animate().alpha(1f).setDuration(180).start();
        swapTitle(R.string.eh_logging_in);
    }

    private void swapTitle(int res) {
        if (challengeTitle == null) return;
        CharSequence next = getText(res);
        if (next.toString().contentEquals(challengeTitle.getText())) return;
        challengeTitle.animate().cancel();
        challengeTitle.animate().alpha(0f).setDuration(90).withEndAction(() -> {
            if (challengeTitle == null) return;
            challengeTitle.setText(next);
            challengeTitle.animate().alpha(1f).setDuration(120).start();
        }).start();
    }

    private void onSuccess() {
        captured = true;
        if (!username.isEmpty()) EHentaiAuth.setDisplayName(this, username);
        refreshIgneousAsync();
        if (dialog != null) dialog.dismiss();
        Toast.makeText(this, R.string.eh_login_success, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onFailure() {
        submitted = false;
        if (dialog != null) dialog.dismiss();
        Toast.makeText(this, R.string.eh_login_failed, Toast.LENGTH_SHORT).show();
    }

    private void closeSession() {
        if (extension != null) extension.setMessageDelegate(null, NATIVE_APP);
        if (geckoView != null) geckoView.releaseSession();
        if (session != null) {
            session.close();
            session = null;
        }
        geckoView = null;
    }

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        closeSession();
        super.onDestroy();
    }
}
