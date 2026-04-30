package com.yosefario.nclientv3;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.yosefario.nclientv3.components.activities.GeneralActivity;
import com.yosefario.nclientv3.loginapi.AuthApi;
import com.yosefario.nclientv3.loginapi.KeyProvisioner;
import com.yosefario.nclientv3.loginapi.User;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.LogUtility;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends GeneralActivity {

    private static final String TURNSTILE_API_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js";

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private EditText usernameField;
    private EditText passwordField;
    private EditText apiKeyField;
    private Button signInButton;
    private Button saveKeyButton;
    private TextView statusView;
    private WebView turnstileWebView;
    private MaterialCheckBox identifyCheckbox;

    @Nullable private volatile String currentCaptchaToken;
    @Nullable private volatile String captchaSiteKey;
    @Nullable private volatile java.util.function.Consumer<String> pendingTokenConsumer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.title_activity_login);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        usernameField = findViewById(R.id.username);
        passwordField = findViewById(R.id.password);
        apiKeyField = findViewById(R.id.api_key);
        signInButton = findViewById(R.id.sign_in_button);
        saveKeyButton = findViewById(R.id.save_key_button);
        statusView = findViewById(R.id.status);
        turnstileWebView = findViewById(R.id.turnstile_webview);
        identifyCheckbox = findViewById(R.id.identify_checkbox);
        identifyCheckbox.setChecked(Global.shouldSendAppUserAgent());
        identifyCheckbox.setOnCheckedChangeListener((v, checked) ->
            Global.setSendAppUserAgent(getApplicationContext(), checked));

        signInButton.setOnClickListener(v -> onSignInClicked());
        saveKeyButton.setOnClickListener(v -> onSaveKeyClicked());

        setStatus(getString(R.string.login_status_captcha_loading));
        io.execute(this::loadCaptchaInfo);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (turnstileWebView != null) {
            turnstileWebView.stopLoading();
            turnstileWebView.destroy();
        }
        io.shutdownNow();
        super.onDestroy();
    }

    private void loadCaptchaInfo() {
        try {
            AuthApi.CaptchaInfo info = AuthApi.fetchCaptchaInfo();
            captchaSiteKey = info.siteKey;
            LogUtility.d("LoginActivity: captcha provider=" + info.provider + " site_key=" + info.siteKey);
            runOnUiThread(this::initTurnstileWidget);
        } catch (IOException e) {
            LogUtility.e("LoginActivity: failed to fetch captcha info", e);
            runOnUiThread(() -> setStatus(getString(R.string.login_status_captcha_error,
                e.getMessage() == null ? "network" : e.getMessage())));
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initTurnstileWidget() {
        if (captchaSiteKey == null) return;
        WebSettings ws = turnstileWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString(Global.getUserAgent());
        turnstileWebView.setWebViewClient(new WebViewClient());
        turnstileWebView.addJavascriptInterface(new TurnstileBridge(), "NClientBridge");

        String html = "<!DOCTYPE html><html><head>"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<script src=\"" + TURNSTILE_API_URL + "\" async defer></script>"
            + "<style>body{margin:0;padding:8px;background:transparent}</style>"
            + "</head><body>"
            + "<div class=\"cf-turnstile\""
            + " data-sitekey=\"" + escape(captchaSiteKey) + "\""
            + " data-callback=\"onCaptchaSuccess\""
            + " data-error-callback=\"onCaptchaError\""
            + " data-expired-callback=\"onCaptchaExpired\"></div>"
            + "<script>"
            + "function onCaptchaSuccess(t){try{NClientBridge.onToken(t);}catch(e){}}"
            + "function onCaptchaError(c){try{NClientBridge.onError(String(c));}catch(e){}}"
            + "function onCaptchaExpired(){try{NClientBridge.onExpired();}catch(e){}}"
            + "function refreshWidget(){try{if(window.turnstile)turnstile.reset();}catch(e){}}"
            + "</script></body></html>";

        // Base URL must match a domain accepted by the Turnstile site key (nhentai.net).
        turnstileWebView.loadDataWithBaseURL("https://nhentai.net/",
            html, "text/html", "utf-8", null);
    }

    private void onSignInClicked() {
        String username = textOf(usernameField);
        String password = textOf(passwordField);
        String token = currentCaptchaToken;
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            setStatus(getString(R.string.login_status_missing_fields));
            return;
        }
        if (TextUtils.isEmpty(token)) {
            setStatus(getString(R.string.login_status_missing_captcha));
            return;
        }
        currentCaptchaToken = null;
        signInButton.setEnabled(false);
        setStatus(getString(R.string.login_status_signing_in));
        KeyProvisioner.login(username, password, token, new KeyProvisioner.LoginCallback() {
            @Override
            public void onProgress(@NonNull String message) {
                runOnUiThread(() -> setStatus(message + "…"));
            }

            @Override
            public void onSuccess() {
                runOnUiThread(LoginActivity.this::startKeyCreation);
            }

            @Override
            public void onFailure(@NonNull String message, @Nullable Throwable cause, boolean captchaRejected) {
                LogUtility.e("LoginActivity: login failed: " + message, cause);
                runOnUiThread(() -> {
                    setStatus(getString(R.string.login_status_login_failed, message));
                    refreshCaptchaWidget();
                });
            }
        });
    }

    private void startKeyCreation() {
        setStatus(getString(R.string.login_status_creating_key));
        requestFreshCaptcha(token -> KeyProvisioner.createKey(
            "NClientV3-" + android.os.Build.MODEL, token, new KeyProvisioner.KeyCallback() {
                @Override
                public void onProgress(@NonNull String message) {
                    runOnUiThread(() -> setStatus(message + "…"));
                }

                @Override
                public void onSuccess(@NonNull String apiKey) {
                    runOnUiThread(() -> {
                        setStatus(getString(R.string.login_status_signed_in));
                        finalizeLogin();
                    });
                }

                @Override
                public void onFailure(@NonNull String message, @Nullable Throwable cause, boolean captchaRejected) {
                    LogUtility.e("LoginActivity: key creation failed: " + message, cause);
                    runOnUiThread(() -> {
                        setStatus(getString(R.string.login_status_login_failed, message));
                        refreshCaptchaWidget();
                    });
                }
            }));
    }

    private void requestFreshCaptcha(@NonNull java.util.function.Consumer<String> onToken) {
        pendingTokenConsumer = onToken;
        signInButton.setEnabled(false);
        currentCaptchaToken = null;
        refreshCaptchaWidget();
    }

    private void refreshCaptchaWidget() {
        if (turnstileWebView != null) {
            turnstileWebView.evaluateJavascript("refreshWidget();", null);
        }
    }

    private void onSaveKeyClicked() {
        String key = textOf(apiKeyField);
        if (TextUtils.isEmpty(key) || key.length() < 16) {
            setStatus(getString(R.string.login_status_key_invalid));
            return;
        }
        Login.saveApiKey(key);
        setStatus(getString(R.string.login_status_key_saved));
        finalizeLogin();
    }

    private void finalizeLogin() {
        Login.authCookiesUpdated();
        User.createUser(null);
        finish();
    }

    private void setStatus(@NonNull String message) {
        if (statusView != null) statusView.setText(message);
    }

    private static String textOf(@Nullable EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static String escape(String s) {
        return s.replace("\"", "&quot;");
    }

    private final class TurnstileBridge {
        @JavascriptInterface
        public void onToken(@Nullable String token) {
            if (token == null || token.isEmpty()) return;
            currentCaptchaToken = token;
            runOnUiThread(() -> {
                java.util.function.Consumer<String> pending = pendingTokenConsumer;
                if (pending != null) {
                    pendingTokenConsumer = null;
                    currentCaptchaToken = null;
                    pending.accept(token);
                    return;
                }
                signInButton.setEnabled(true);
                setStatus(getString(R.string.login_status_captcha_ready));
            });
        }

        @JavascriptInterface
        public void onError(@Nullable String code) {
            currentCaptchaToken = null;
            runOnUiThread(() -> {
                signInButton.setEnabled(false);
                setStatus(getString(R.string.login_status_captcha_error,
                    code == null ? "?" : code));
            });
        }

        @JavascriptInterface
        public void onExpired() {
            currentCaptchaToken = null;
            runOnUiThread(() -> {
                signInButton.setEnabled(false);
                setStatus(getString(R.string.login_status_captcha_expired));
            });
        }
    }
}
