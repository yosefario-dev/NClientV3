package com.yosefario.nclientv3.loginapi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.LogUtility;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Turnstile tokens are single-use, so login() and createKey() each need their own fresh token.
public final class KeyProvisioner {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface LoginCallback {
        void onProgress(@NonNull String message);
        void onSuccess();
        void onFailure(@NonNull String message, @Nullable Throwable cause, boolean captchaRejected);
    }

    public interface KeyCallback {
        void onProgress(@NonNull String message);
        void onSuccess(@NonNull String apiKey);
        void onFailure(@NonNull String message, @Nullable Throwable cause, boolean captchaRejected);
    }

    private KeyProvisioner() {}

    public static void login(@NonNull String username,
                             @NonNull String password,
                             @NonNull String captchaToken,
                             @NonNull LoginCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                callback.onProgress("Solving login challenge");
                AuthApi.PowChallenge challenge = AuthApi.fetchPowChallenge(AuthApi.ACTION_LOGIN);
                long t0 = System.nanoTime();
                String nonce = PowSolver.solve(challenge.challenge, challenge.difficulty);
                LogUtility.d("KeyProvisioner: login PoW solved difficulty=" + challenge.difficulty
                    + " in " + ((System.nanoTime() - t0) / 1_000_000) + "ms");

                callback.onProgress("Signing in");
                JSONObject resp;
                try {
                    resp = AuthApi.login(username, password, challenge.challenge, nonce, captchaToken);
                } catch (AuthApi.ApiException e) {
                    boolean captcha = "Invalid CAPTCHA solution".equalsIgnoreCase(e.error);
                    callback.onFailure(formatError("Login failed", e), e, captcha);
                    return;
                }
                Login.saveTokensFromJson(resp);
                Login.authCookiesUpdated();
                LogUtility.d("KeyProvisioner: login OK, hasAccess="
                    + (Login.getCookieValue(Login.ACCESS_TOKEN_COOKIE) != null));
                callback.onSuccess();
            } catch (IOException e) {
                callback.onFailure(e.getMessage() == null ? "Network error" : e.getMessage(), e, false);
            } catch (RuntimeException e) {
                callback.onFailure("Unexpected error: " + e.getMessage(), e, false);
            }
        });
    }

    public static void createKey(@NonNull String keyName,
                                 @NonNull String captchaToken,
                                 @NonNull KeyCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                callback.onProgress("Solving key challenge");
                AuthApi.PowChallenge challenge = AuthApi.fetchPowChallenge(AuthApi.ACTION_API_KEY);
                long t0 = System.nanoTime();
                String nonce = PowSolver.solve(challenge.challenge, challenge.difficulty);
                LogUtility.d("KeyProvisioner: api_key PoW solved difficulty=" + challenge.difficulty
                    + " in " + ((System.nanoTime() - t0) / 1_000_000) + "ms");

                callback.onProgress("Generating API key");
                JSONObject resp;
                try {
                    resp = AuthApi.createApiKey(keyName, "NClientV3", challenge.challenge, nonce, captchaToken);
                } catch (AuthApi.ApiException e) {
                    boolean captcha = "Invalid CAPTCHA solution".equalsIgnoreCase(e.error);
                    callback.onFailure(formatError("API key creation failed", e), e, captcha);
                    return;
                }
                String key = resp.optString("key", "");
                if (key.isEmpty()) {
                    callback.onFailure("API key creation returned no key", null, false);
                    return;
                }
                Login.saveApiKey(key);
                callback.onSuccess(key);
            } catch (IOException e) {
                callback.onFailure(e.getMessage() == null ? "Network error" : e.getMessage(), e, false);
            } catch (RuntimeException e) {
                callback.onFailure("Unexpected error: " + e.getMessage(), e, false);
            }
        });
    }

    private static String formatError(String prefix, AuthApi.ApiException e) {
        if (e.error != null && !e.error.isEmpty()) return prefix + ": " + e.error;
        return prefix + " (HTTP " + e.httpCode + ")";
    }
}
