package com.yosefario.nclientv3.settings;

import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yosefario.nclientv3.components.CookieInterceptor;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;

import okhttp3.Cookie;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class CustomInterceptor implements Interceptor {
    private static final String CF_CLEARANCE_COOKIE = "cf_clearance";
    private static final String CSRF_COOKIE = "csrftoken";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final boolean logRequests;
    private static final CloudflareCookieManager MANAGER = new CloudflareCookieManager();

    private static class CloudflareCookieManager implements CookieInterceptor.Manager {
        private boolean cfClearanceFound = false;
        private boolean csrfTokenFound = false;

        void reset() {
            cfClearanceFound = false;
            csrfTokenFound = false;
        }

        @Override
        public void applyCookie(String key, String value) {
            Cookie cookie = Cookie.parse(Login.BASE_HTTP_URL, key + "=" + value + "; Max-Age=31449600; Path=/; SameSite=Lax");
            Global.client.cookieJar().saveFromResponse(Login.BASE_HTTP_URL, Collections.singletonList(cookie));
            cfClearanceFound |= key.equals(CF_CLEARANCE_COOKIE);
            csrfTokenFound |= key.equals(CSRF_COOKIE);
        }

        @Override
        public boolean endInterceptor() {
            if (cfClearanceFound || csrfTokenFound) return true;
            String cookies = CookieManager.getInstance().getCookie(Utility.getBaseUrl());
            if (cookies == null) return false;
            return cookies.contains(CF_CLEARANCE_COOKIE) || cookies.contains(CSRF_COOKIE);
        }

        @Override
        public void onFinish() {

        }
    }
    @Nullable
    private final Context context;

    public CustomInterceptor(@Nullable Context context, boolean logRequests) {
        this.context = context;
        this.logRequests = logRequests;
    }

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        boolean rec = request.header("rec") != null;
        boolean authRefresh = request.header(Login.AUTH_REFRESH_HEADER) != null;
        if (logRequests)
            LogUtility.d("Requested url: " + request.url());
        Request authenticatedRequest = buildRequest(request, authRefresh);
        Response response = chain.proceed(authenticatedRequest);
        if (!authRefresh && isAuthFailure(response)) {
            if (Login.refreshAuthTokens()) {
                response.close();
                return chain.proceed(buildRequest(request, false));
            }
            return response;
        }
        if (
            (response.code() == HttpURLConnection.HTTP_UNAVAILABLE ||
                response.code() == HttpURLConnection.HTTP_FORBIDDEN)
                && (!rec || !MANAGER.endInterceptor())) {

            clearWebViewCookies();
            MANAGER.reset();

            CookieInterceptor interceptor = new CookieInterceptor(MANAGER);
            interceptor.intercept();
            if (context != null) Global.reloadHttpClient(context);
            response.close();
            response = Global.client.newCall(request.newBuilder().addHeader("rec", "1").build()).execute();
        }
        return response;
    }

    private Request buildRequest(Request request, boolean authRefresh) {
        Request.Builder builder = request.newBuilder();
        builder.removeHeader("rec");
        builder.removeHeader(Login.AUTH_REFRESH_HEADER);
        boolean apiV2 = request.url().encodedPath().startsWith("/api/v2/");
        builder.header("User-Agent", apiV2 ? Global.getApiUserAgent() : Global.getUserAgent());
        if (!authRefresh && shouldAddAuthorization(request)) {
            String apiKey = Login.getApiKey();
            if (apiKey != null) builder.header(AUTHORIZATION_HEADER, "Key " + apiKey);
            else addUserToken(builder);
        }
        return builder.build();
    }

    private void addUserToken(Request.Builder builder) {
            String accessToken = Login.getDecodedCookieValue(Login.ACCESS_TOKEN_COOKIE);
            if (accessToken != null) builder.header(AUTHORIZATION_HEADER, "User " + accessToken);
    }

    private boolean shouldAddAuthorization(Request request) {
        if (request.header(AUTHORIZATION_HEADER) != null) return false;
        String path = request.url().encodedPath();
        return path.startsWith("/api/v2/") && !path.startsWith("/api/v2/auth/refresh");
    }

    private boolean isAuthFailure(Response response) {
        if (response.code() == HttpURLConnection.HTTP_UNAUTHORIZED) return true;
        if (response.code() != HttpURLConnection.HTTP_FORBIDDEN) return false;
        String path = response.request().url().encodedPath();
        return path.startsWith("/api/v2/user")
            || path.startsWith("/api/v2/favorites")
            || path.startsWith("/api/v2/blacklist")
            || path.startsWith("/api/v2/auth/");
    }

    private static void clearWebViewCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(null);
            cookieManager.flush();
        } else {
            cookieManager.removeAllCookie();
        }
    }

}
