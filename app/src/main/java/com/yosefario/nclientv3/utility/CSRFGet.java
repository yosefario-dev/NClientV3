package com.yosefario.nclientv3.utility;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.Login;

import java.io.IOException;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.Request;

public class CSRFGet extends Thread {
    @Nullable
    private final Response response;
    private final String url;

    public CSRFGet(@Nullable Response response, String url) {
        this.response = response;
        this.url = url;
    }

    @Override
    public void run() {
        try {
            String token = getCookieToken();
            if (token != null) {
                if (this.response != null) this.response.onResponse(token);
                return;
            }
            if (url.contains("/api/v2/") && (Login.hasCookie(Login.ACCESS_TOKEN_COOKIE) || Login.hasCookie(Login.REFRESH_TOKEN_COOKIE))) {
                if (this.response != null) this.response.onResponse("");
                return;
            }
            if (url.contains("/api/")) throw new IOException("Missing CSRF token cookie");
            assert Global.getClient() != null;
            okhttp3.Response response = Global.getClient().newCall(new Request.Builder().url(url).build()).execute();
            if (response.body() == null) throw new NullPointerException("Error retrieving url");
            token = response.body().string();
            int tokenStart = token.lastIndexOf("csrf_token");
            if (tokenStart < 0) throw new IOException("CSRF token not found in page");
            token = token.substring(tokenStart);
            token = token.substring(token.indexOf('"') + 1);
            token = token.substring(0, token.indexOf('"'));
            if (this.response != null) this.response.onResponse(token);
        } catch (Exception e) {
            if (response != null) response.onError(e);
        }
    }

    @Nullable
    private String getCookieToken() {
        if (Login.BASE_HTTP_URL == null || Global.getClient() == null) return null;
        List<Cookie> cookies = Global.getClient().cookieJar().loadForRequest(Login.BASE_HTTP_URL);
        for (Cookie cookie : cookies) {
            if ("csrftoken".equals(cookie.name()) && !cookie.value().isEmpty()) return cookie.value();
        }
        return null;
    }

    public interface Response {
        void onResponse(String token) throws IOException;

        default void onError(Exception e) {
            e.printStackTrace();
        }
    }
}
