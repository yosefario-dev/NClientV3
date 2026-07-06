package com.yosefario.nclientv3.components.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.settings.Global;

import androidx.appcompat.app.AlertDialog;

public final class TurnstileDialog {

    public interface Callback {
        void onToken(@NonNull String token);

        void onCancelled();
    }

    private static final String TURNSTILE_API_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js";

    private final Activity activity;
    private final String siteKey;
    private final Callback callback;

    private AlertDialog dialog;
    private WebView webView;
    private CircularProgressIndicator progress;
    private TextView errorView;
    private boolean delivered;

    public TurnstileDialog(@NonNull Activity activity, @NonNull String siteKey, @NonNull Callback callback) {
        this.activity = activity;
        this.siteKey = siteKey;
        this.callback = callback;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void show(boolean secondCheck) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_turnstile, null);

        TextView title = view.findViewById(R.id.turnstile_dialog_title);
        TextView subtitle = view.findViewById(R.id.turnstile_dialog_subtitle);
        title.setText(secondCheck ? R.string.turnstile_title_second : R.string.turnstile_title);
        subtitle.setText(secondCheck ? R.string.turnstile_subtitle_second : R.string.turnstile_subtitle);
        if (secondCheck)
            title.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorPrimary,
                title.getCurrentTextColor()));

        progress = view.findViewById(R.id.turnstile_progress);
        errorView = view.findViewById(R.id.turnstile_error);
        errorView.setOnClickListener(v -> retry());

        webView = view.findViewById(R.id.turnstile_webview);
        setupWebView();

        dialog = new MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener(d -> onDismiss())
            .create();
        dialog.show();

        loadWidget();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setUserAgentString(Global.getUserAgent());
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "NClientBridge");
    }

    private void loadWidget() {
        int night = activity.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        String cfTheme = night == android.content.res.Configuration.UI_MODE_NIGHT_YES ? "dark" : "light";

        String html = "<!DOCTYPE html><html><head>"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<style>html,body{margin:0;padding:0;background:transparent;"
            + "display:flex;justify-content:center;align-items:center;height:100%}</style>"
            + "<script>"
            + "function onCaptchaSuccess(t){try{NClientBridge.onToken(t);}catch(e){}}"
            + "function onCaptchaError(c){try{NClientBridge.onError(String(c));}catch(e){}}"
            + "function onCaptchaExpired(){try{NClientBridge.onExpired();}catch(e){}}"
            + "function onTurnstileLoad(){try{NClientBridge.onReady();"
            + "window.__w=turnstile.render('#cf',{sitekey:'" + escape(siteKey) + "',"
            + "theme:'" + cfTheme + "',callback:onCaptchaSuccess,'error-callback':onCaptchaError,"
            + "'expired-callback':onCaptchaExpired});}catch(e){}}"
            + "function refreshWidget(){try{if(window.turnstile)turnstile.reset(window.__w);}catch(e){}}"
            + "</script>"
            + "<script src=\"" + TURNSTILE_API_URL + "?onload=onTurnstileLoad&render=explicit\" async defer></script>"
            + "</head><body><div id=\"cf\"></div></body></html>";

        // Base URL must match a domain accepted by the Turnstile site key (nhentai.net).
        webView.loadDataWithBaseURL("https://nhentai.net/", html, "text/html", "utf-8", null);
    }

    private void retry() {
        if (errorView != null) errorView.setVisibility(View.GONE);
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (webView != null) webView.evaluateJavascript("refreshWidget();", null);
    }

    private void onDismiss() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        if (!delivered) callback.onCancelled();
    }

    private final class Bridge {
        @JavascriptInterface
        public void onReady() {
            activity.runOnUiThread(() -> {
                if (progress != null) progress.setVisibility(View.GONE);
            });
        }

        @JavascriptInterface
        public void onToken(@Nullable String token) {
            if (token == null || token.isEmpty()) return;
            activity.runOnUiThread(() -> {
                if (delivered) return;
                delivered = true;
                callback.onToken(token);
                if (dialog != null) dialog.dismiss();
            });
        }

        @JavascriptInterface
        public void onError(@Nullable String code) {
            activity.runOnUiThread(TurnstileDialog.this::showError);
        }

        @JavascriptInterface
        public void onExpired() {
            activity.runOnUiThread(TurnstileDialog.this::retry);
        }
    }

    private void showError() {
        if (progress != null) progress.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
