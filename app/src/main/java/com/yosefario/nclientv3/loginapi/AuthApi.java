package com.yosefario.nclientv3.loginapi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yosefario.nclientv3.settings.CustomInterceptor;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Blocking calls; invoke from a background thread. */
public final class AuthApi {

    public static final String ACTION_LOGIN = "login";
    public static final String ACTION_API_KEY = "api_key";
    public static final String ACTION_REGISTER = "register";
    public static final String ACTION_RESET = "reset";
    public static final String ACTION_COMMENT = "comment";

    private static final MediaType JSON = MediaType.get("application/json");

    private AuthApi() {}

    public static class PowChallenge {
        public final String challenge;
        public final int difficulty;

        PowChallenge(String challenge, int difficulty) {
            this.challenge = challenge;
            this.difficulty = difficulty;
        }
    }

    public static class CaptchaInfo {
        public final String provider;
        public final String siteKey;

        CaptchaInfo(String provider, String siteKey) {
            this.provider = provider;
            this.siteKey = siteKey;
        }
    }

    public static class ApiException extends IOException {
        public final int httpCode;
        public final String error;

        public ApiException(int httpCode, String error) {
            super("HTTP " + httpCode + (error == null ? "" : ": " + error));
            this.httpCode = httpCode;
            this.error = error;
        }
    }

    @NonNull
    public static PowChallenge fetchPowChallenge(@NonNull String action) throws IOException {
        String url = Utility.getBaseUrl() + "api/v2/pow?action=" + action;
        JSONObject json = getJson(url);
        try {
            return new PowChallenge(json.getString("challenge"), json.getInt("difficulty"));
        } catch (JSONException e) {
            throw new IOException("Malformed PoW response", e);
        }
    }

    @NonNull
    public static CaptchaInfo fetchCaptchaInfo() throws IOException {
        JSONObject json = getJson(Utility.getBaseUrl() + "api/v2/captcha");
        try {
            return new CaptchaInfo(json.getString("provider"), json.getString("site_key"));
        } catch (JSONException e) {
            throw new IOException("Malformed CAPTCHA response", e);
        }
    }

    @NonNull
    public static JSONObject login(@NonNull String username, @NonNull String password,
                                   @NonNull String powChallenge, @NonNull String powNonce,
                                   @NonNull String captchaResponse) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            body.put("pow_challenge", powChallenge);
            body.put("pow_nonce", powNonce);
            body.put("captcha_response", captchaResponse);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        return postJson(Utility.getBaseUrl() + "api/v2/auth/login", body);
    }

    // The OkHttp interceptor attaches Authorization based on the cookies/access_token saved
    // during login, so callers don't need to pass it explicitly here.
    @NonNull
    public static JSONObject createApiKey(@NonNull String name, @NonNull String purpose,
                                          @NonNull String powChallenge, @NonNull String powNonce,
                                          @NonNull String captchaResponse) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("name", name);
            body.put("purpose", purpose);
            body.put("pow_challenge", powChallenge);
            body.put("pow_nonce", powNonce);
            body.put("captcha_response", captchaResponse);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        return postJson(Utility.getBaseUrl() + "api/v2/user/keys", body);
    }

    @NonNull
    public static JSONObject postComment(int galleryId, @NonNull String text,
                                         @NonNull String powChallenge, @NonNull String powNonce,
                                         @NonNull String captchaResponse) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("body", text);
            body.put("pow_challenge", powChallenge);
            body.put("pow_nonce", powNonce);
            body.put("captcha_response", captchaResponse);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        Request request = new Request.Builder()
            .url(Utility.getBaseUrl() + "api/v2/galleries/" + galleryId + "/comments")
            .tag(CustomInterceptor.Auth.class, CustomInterceptor.Auth.PREFER_USER)
            .post(RequestBody.create(JSON, body.toString()))
            .build();
        try (Response response = Global.getClient().newCall(request).execute()) {
            return parseJsonOrThrow(response);
        }
    }

    public static void deleteComment(int commentId) throws IOException {
        Request request = new Request.Builder()
            .url(Utility.getBaseUrl() + "api/v2/comments/" + commentId)
            .tag(CustomInterceptor.Auth.class, CustomInterceptor.Auth.PREFER_USER)
            .delete()
            .build();
        try (Response response = Global.getClient().newCall(request).execute()) {
            if (response.code() == HttpURLConnection.HTTP_OK) return;
            ResponseBody rb = response.body();
            String text = rb == null ? "" : rb.string();
            LogUtility.e("AuthApi: delete comment HTTP " + response.code() + " body=" + text);
            throw new ApiException(response.code(), extractError(text));
        }
    }

    @NonNull
    private static JSONObject getJson(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = Global.getClient().newCall(request).execute()) {
            return parseJsonOrThrow(response);
        }
    }

    @NonNull
    private static JSONObject postJson(String url, JSONObject body) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(JSON, body.toString()))
            .build();
        try (Response response = Global.getClient().newCall(request).execute()) {
            return parseJsonOrThrow(response);
        }
    }

    @NonNull
    private static JSONObject parseJsonOrThrow(Response response) throws IOException {
        ResponseBody rb = response.body();
        String text = rb == null ? "" : rb.string();
        if (response.code() != HttpURLConnection.HTTP_OK) {
            String error = extractError(text);
            LogUtility.e("AuthApi: HTTP " + response.code() + " for " + response.request().url() + " body=" + text);
            throw new ApiException(response.code(), error);
        }
        if (text.isEmpty()) return new JSONObject();
        try {
            return new JSONObject(text);
        } catch (JSONException e) {
            throw new IOException("Malformed JSON response", e);
        }
    }

    @Nullable
    private static String extractError(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            return new JSONObject(body).optString("error", null);
        } catch (JSONException ignored) {
            return null;
        }
    }
}
