package com.yosefario.nclientv3;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.yosefario.nclientv3.components.activities.GeneralActivity;
import com.yosefario.nclientv3.components.views.TurnstileDialog;
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

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private EditText usernameField;
    private EditText passwordField;
    private EditText apiKeyField;
    private MaterialButton signInButton;
    private MaterialButton saveKeyButton;
    private TextView statusView;
    private MaterialCheckBox identifyCheckbox;

    @Nullable private volatile String captchaSiteKey;

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
        identifyCheckbox = findViewById(R.id.identify_checkbox);
        identifyCheckbox.setChecked(Global.shouldSendAppUserAgent());
        identifyCheckbox.setOnCheckedChangeListener((v, checked) ->
            Global.setSendAppUserAgent(getApplicationContext(), checked));

        signInButton.setOnClickListener(v -> onSignInClicked());
        saveKeyButton.setOnClickListener(v -> onSaveKeyClicked());

        io.execute(this::loadCaptchaInfo);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void loadCaptchaInfo() {
        try {
            AuthApi.CaptchaInfo info = AuthApi.fetchCaptchaInfo();
            captchaSiteKey = info.siteKey;
            LogUtility.d("LoginActivity: captcha provider=" + info.provider + " site_key=" + info.siteKey);
        } catch (IOException e) {
            LogUtility.e("LoginActivity: failed to fetch captcha info", e);
            runOnUiThread(() -> setStatus(getString(R.string.login_status_captcha_error,
                e.getMessage() == null ? "network" : e.getMessage())));
        }
    }

    private void onSignInClicked() {
        String username = textOf(usernameField);
        String password = textOf(passwordField);
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            setStatus(getString(R.string.login_status_missing_fields));
            return;
        }
        requestCaptcha(false, token -> doLogin(username, password, token));
    }

    private void requestCaptcha(boolean secondCheck, @NonNull java.util.function.Consumer<String> onToken) {
        String siteKey = captchaSiteKey;
        if (TextUtils.isEmpty(siteKey)) {
            setStatus(getString(R.string.login_status_captcha_loading));
            io.execute(() -> {
                loadCaptchaInfo();
                runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(captchaSiteKey)) requestCaptcha(secondCheck, onToken);
                });
            });
            return;
        }
        signInButton.setEnabled(false);
        new TurnstileDialog(this, siteKey, new TurnstileDialog.Callback() {
            @Override
            public void onToken(@NonNull String token) {
                onToken.accept(token);
            }

            @Override
            public void onCancelled() {
                signInButton.setEnabled(true);
                setStatus(getString(R.string.login_status_captcha_cancelled));
            }
        }).show(secondCheck);
    }

    private void doLogin(String username, String password, String token) {
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
                    signInButton.setEnabled(true);
                    setStatus(getString(R.string.login_status_login_failed, message));
                });
            }
        });
    }

    private void startKeyCreation() {
        setStatus(getString(R.string.login_status_creating_key));
        requestCaptcha(true, token -> KeyProvisioner.createKey(
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
                        signInButton.setEnabled(true);
                        setStatus(getString(R.string.login_status_login_failed, message));
                    });
                }
            }));
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
}
