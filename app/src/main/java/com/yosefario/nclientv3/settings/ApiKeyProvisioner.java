package com.yosefario.nclientv3.settings;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yosefario.nclientv3.utility.Utility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiKeyProvisioner {
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    private ApiKeyProvisioner() {
    }

    @NonNull
    public static CaptchaInfo fetchCaptchaInfo() throws IOException, JSONException {
        JSONObject payload = new JSONObject(fetchText(Utility.getBaseUrl() + "api/v2/captcha"));
        return new CaptchaInfo(payload.optString("provider"), payload.optString("site_key"));
    }

    @NonNull
    public static String createApiKey(@NonNull Context context, @NonNull String captchaResponse) throws IOException, JSONException {
        PoWChallenge challenge = fetchChallenge();
        String nonce = solve(challenge.challenge, challenge.difficulty);
        JSONObject body = new JSONObject();
        body.put("name", "NClientV3 Android");
        body.put("purpose", String.format(Locale.US, "NClientV3 %s", Global.getVersionName(context)));
        body.put("pow_challenge", challenge.challenge);
        body.put("pow_nonce", nonce);
        body.put("captcha_response", captchaResponse);
        Request request = new Request.Builder()
            .url(Utility.getBaseUrl() + "api/v2/user/keys")
            .post(RequestBody.create(JSON_MEDIA_TYPE, body.toString()))
            .build();
        Response response = Global.getClient(context).newCall(request).execute();
        try {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("API key creation failed: HTTP " + response.code() + " " + responseBody);
            String key = new JSONObject(responseBody).optString("key", "").trim();
            if (key.isEmpty()) throw new IOException("API key creation response did not include a key");
            return key;
        } finally {
            response.close();
        }
    }

    private static PoWChallenge fetchChallenge() throws IOException, JSONException {
        JSONObject payload = new JSONObject(fetchText(Utility.getBaseUrl() + "api/v2/pow?action=api_key"));
        return new PoWChallenge(payload.optString("challenge"), payload.optInt("difficulty"));
    }

    private static String fetchText(String url) throws IOException {
        Response response = Global.getClient().newCall(new Request.Builder().url(url).build()).execute();
        try {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " while fetching " + url + " " + body);
            return body;
        } finally {
            response.close();
        }
    }

    private static String solve(String challenge, int difficulty) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long nonce = 0;
            while (true) {
                String value = Long.toString(nonce);
                digest.reset();
                byte[] hash = digest.digest((challenge + value).getBytes(StandardCharsets.UTF_8));
                if (leadingZeroBits(hash) >= difficulty) return value;
                nonce++;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static int leadingZeroBits(byte[] hash) {
        int count = 0;
        for (byte value : hash) {
            int unsigned = value & 0xff;
            if (unsigned == 0) {
                count += 8;
                continue;
            }
            return count + Integer.numberOfLeadingZeros(unsigned) - 24;
        }
        return count;
    }

    public static class CaptchaInfo {
        public final String provider;
        public final String siteKey;

        private CaptchaInfo(String provider, String siteKey) {
            this.provider = provider == null ? "" : provider;
            this.siteKey = siteKey == null ? "" : siteKey;
        }
    }

    private static class PoWChallenge {
        final String challenge;
        final int difficulty;

        PoWChallenge(String challenge, int difficulty) {
            this.challenge = challenge;
            this.difficulty = difficulty;
        }
    }
}
