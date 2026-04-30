package com.yosefario.nclientv3.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yosefario.nclientv3.MainActivity;
import com.yosefario.nclientv3.R;
import com.yosefario.nclientv3.api.components.Tag;
import com.yosefario.nclientv3.async.database.Queries;
import com.yosefario.nclientv3.components.CustomCookieJar;
import com.yosefario.nclientv3.loginapi.LoadTags;
import com.yosefario.nclientv3.loginapi.User;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Login {
    public static final String LOGIN_COOKIE = "sessionid";
    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    public static final String API_KEY_PREF = "api_key";
    public static final String AUTH_REFRESH_HEADER = "NClient-Auth-Refresh";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
    private static final long REFRESH_FAILURE_COOLDOWN_MS = 30000;
    public static HttpUrl BASE_HTTP_URL;
    private static User user;
    private static boolean accountTag;
    private static SharedPreferences loginShared;
    private static long lastFailedRefreshTime;

    public static void initLogin(@NonNull Context context) {
        SharedPreferences preferences = context.getSharedPreferences("Settings", 0);
        accountTag = preferences.getBoolean(context.getString(R.string.key_use_account_tag), false);
        if (Global.isSourceConfigured()) {
            BASE_HTTP_URL = HttpUrl.get(Utility.getBaseUrl());
        } else {
            BASE_HTTP_URL = null;
        }
    }

    public static boolean useAccountTag() {
        return accountTag;
    }

    public static void setLoginShared(SharedPreferences loginShared) {
        Login.loginShared = loginShared;
    }

    private static void removeCookie(String cookieName) {
        CustomCookieJar cookieJar = (CustomCookieJar) Global.client.cookieJar();
        cookieJar.removeCookie(cookieName);
    }


    public static void removeCloudflareCookies() {
        if (BASE_HTTP_URL == null) return;
        CustomCookieJar cookieJar = (CustomCookieJar) Global.client.cookieJar();
        List<Cookie> cookies = cookieJar.loadForRequest(BASE_HTTP_URL);
        for (Cookie cookie : cookies) {
            if (cookie.name().equals(LOGIN_COOKIE)
                || cookie.name().equals(ACCESS_TOKEN_COOKIE)
                || cookie.name().equals(REFRESH_TOKEN_COOKIE)) {
                continue;
            }
            cookieJar.removeCookie(cookie.name());
        }
    }

    public static void logout(Context context) {
        CustomCookieJar cookieJar = (CustomCookieJar) Global.client.cookieJar();
        removeCookie(LOGIN_COOKIE);
        removeCookie(ACCESS_TOKEN_COOKIE);
        removeCookie(REFRESH_TOKEN_COOKIE);
        removeCookie("csrftoken");
        cookieJar.clearSession();
        updateUser(null);//remove user
        clearOnlineTags();//remove online tags
        clearApiKey();
        clearWebViewCookies(context);//clear webView cookies
    }

    public static void clearWebViewCookies(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
            } else {
                CookieSyncManager cookieSyncMngr = CookieSyncManager.createInstance(context);
                cookieSyncMngr.startSync();
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeAllCookie();
                cookieManager.removeSessionCookie();
                cookieSyncMngr.stopSync();
                cookieSyncMngr.sync();
            }
        } catch (Throwable ignore) {
        }//catch InvocationTargetException randomly thrown
    }

    public static void clearOnlineTags() {
        Queries.TagTable.removeAllBlacklisted();
    }

    public static void clearCookies(){
        CustomCookieJar cookieJar = (CustomCookieJar) Global.getClient().cookieJar();
        cookieJar.clear();
        cookieJar.clearSession();
    }

    public static void addOnlineTag(Tag tag) {
        Queries.TagTable.insert(tag);
        Queries.TagTable.updateBlacklistedTag(tag, true);
    }

    public static void removeOnlineTag(Tag tag) {
        Queries.TagTable.updateBlacklistedTag(tag, false);
    }

    public static boolean hasCookie(String name) {
        if (BASE_HTTP_URL == null) return false;
        List<Cookie> cookies = Global.client.cookieJar().loadForRequest(BASE_HTTP_URL);
        for (Cookie c : cookies) {
            if (c.name().equals(name) && !c.value().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasApiKey() {
        return getApiKey() != null;
    }

    @Nullable
    public static String getApiKey() {
        if (loginShared == null) return null;
        String key = loginShared.getString(API_KEY_PREF, "");
        if (key == null) return null;
        key = key.trim();
        return key.isEmpty() ? null : key;
    }

    public static void saveApiKey(@NonNull String key) {
        if (loginShared == null) return;
        loginShared.edit().putString(API_KEY_PREF, key.trim()).apply();
    }

    public static void clearApiKey() {
        if (loginShared == null) return;
        loginShared.edit().remove(API_KEY_PREF).apply();
    }

    public static final String PENDING_RELOGIN_PREF = "pending_relogin_prompt";

    /**
     * Detects upgrades from the pre-API-key auth (sessionid / scraped cookies). If the user has
     * any legacy auth state but no api_key, clear the cookies and flag the UI to show a re-login
     * prompt on next MainActivity launch.
     */
    public static void migrateAuthIfNeeded(@NonNull Context context) {
        if (BASE_HTTP_URL == null || loginShared == null) return;
        if (hasApiKey()) return;
        boolean hasLegacy = hasCookie(LOGIN_COOKIE)
            || hasCookie(ACCESS_TOKEN_COOKIE)
            || hasCookie(REFRESH_TOKEN_COOKIE);
        if (!hasLegacy) return;
        LogUtility.d("Login: migrating legacy auth state, clearing cookies");
        removeCookie(LOGIN_COOKIE);
        removeCookie(ACCESS_TOKEN_COOKIE);
        removeCookie(REFRESH_TOKEN_COOKIE);
        removeCookie("csrftoken");
        loginShared.edit().putBoolean(PENDING_RELOGIN_PREF, true).apply();
    }

    public static boolean isReloginPending() {
        return loginShared != null && loginShared.getBoolean(PENDING_RELOGIN_PREF, false);
    }

    public static void clearReloginPending() {
        if (loginShared == null) return;
        loginShared.edit().remove(PENDING_RELOGIN_PREF).apply();
    }

    public static synchronized void authCookiesUpdated() {
        lastFailedRefreshTime = 0;
    }

    @Nullable
    public static String getCookieValue(String name) {
        if (BASE_HTTP_URL == null || Global.client == null) return null;
        List<Cookie> cookies = Global.client.cookieJar().loadForRequest(BASE_HTTP_URL);
        for (Cookie cookie : cookies) {
            if (cookie.name().equals(name) && !cookie.value().isEmpty()) return cookie.value();
        }
        return null;
    }

    @Nullable
    public static String getDecodedCookieValue(String name) {
        String value = getCookieValue(name);
        if (value == null) return null;
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }

    public static synchronized boolean refreshAuthTokens() {
        String refreshToken = getDecodedCookieValue(REFRESH_TOKEN_COOKIE);
        if (refreshToken == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastFailedRefreshTime < REFRESH_FAILURE_COOLDOWN_MS) return false;
        try {
            JSONObject body = new JSONObject();
            body.put(REFRESH_TOKEN_COOKIE, refreshToken);
            body.put(toCamelCase(REFRESH_TOKEN_COOKIE), refreshToken);
            body.put("refresh", refreshToken);
            Request request = new Request.Builder()
                .url(Utility.getBaseUrl() + "api/v2/auth/refresh")
                .header(AUTH_REFRESH_HEADER, "1")
                .post(RequestBody.create(JSON_MEDIA_TYPE, body.toString()))
                .build();
            Response response = Global.client.newCall(request).execute();
            try {
                if (!response.isSuccessful() || response.body() == null) {
                    LogUtility.e("Unable to refresh auth tokens: HTTP " + response.code());
                    lastFailedRefreshTime = now;
                    return false;
                }
                try {
                    saveTokenCookies(response.body().string());
                } catch (JSONException e) {
                    LogUtility.d("Auth refresh response did not contain JSON token fields", e);
                }
                boolean refreshed = hasCookie(ACCESS_TOKEN_COOKIE);
                if (refreshed) lastFailedRefreshTime = 0;
                if (!refreshed) lastFailedRefreshTime = now;
                return refreshed;
            } finally {
                response.close();
            }
        } catch (IOException | JSONException e) {
            LogUtility.e("Unable to refresh auth tokens", e);
            lastFailedRefreshTime = now;
            return false;
        }
    }

    private static void saveTokenCookies(String body) throws JSONException {
        if (body == null || body.trim().isEmpty()) return;
        saveTokensFromJson(new JSONObject(body));
    }

    public static void saveTokensFromJson(@Nullable JSONObject payload) {
        if (payload == null || BASE_HTTP_URL == null) return;
        saveTokenCookie(payload, ACCESS_TOKEN_COOKIE, 1209600, "access", "token");
        saveTokenCookie(payload, REFRESH_TOKEN_COOKIE, 1209600, "refresh");
        JSONObject tokens = payload.optJSONObject("tokens");
        if (tokens != null) {
            saveTokenCookie(tokens, ACCESS_TOKEN_COOKIE, 1209600, "access", "token");
            saveTokenCookie(tokens, REFRESH_TOKEN_COOKIE, 1209600, "refresh");
        }
    }

    private static void saveTokenCookie(JSONObject payload, String name, long maxAge, String... aliases) {
        String value = payload.optString(name, "");
        if (value.isEmpty()) value = payload.optString(toCamelCase(name), "");
        for (String alias : aliases) {
            if (!value.isEmpty()) break;
            value = payload.optString(alias, "");
        }
        if (value.isEmpty()) return;
        Cookie cookie = Cookie.parse(BASE_HTTP_URL, name + "=" + value + "; Max-Age=" + maxAge + "; Path=/; SameSite=Lax");
        if (cookie != null) Global.client.cookieJar().saveFromResponse(BASE_HTTP_URL, Collections.singletonList(cookie));
    }

    private static String toCamelCase(String name) {
        StringBuilder builder = new StringBuilder();
        boolean capitalize = false;
        for (int index = 0; index < name.length(); index++) {
            char actualChar = name.charAt(index);
            if (actualChar == '_') {
                capitalize = true;
            } else if (capitalize) {
                builder.append(Character.toUpperCase(actualChar));
                capitalize = false;
            } else {
                builder.append(actualChar);
            }
        }
        return builder.toString();
    }

    public static boolean isLogged(@Nullable Context context) {
        if (BASE_HTTP_URL == null) return false;
        List<Cookie> cookies = Global.client.cookieJar().loadForRequest(BASE_HTTP_URL);
        LogUtility.d("Cookies: " + cookieNames(cookies));
        if (hasApiKey() || hasCookie(LOGIN_COOKIE) || hasCookie(ACCESS_TOKEN_COOKIE) || refreshAuthTokens()) {
            if (user == null) User.createUser(user -> {
                if (user != null) {
                    new LoadTags(null).start();
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).runOnUiThread(() -> ((MainActivity) context).loginItem.setTitle(context.getString(R.string.login_formatted, user.getUsername())));
                    }
                }
            });
            return true;
        }
        if (context != null) logout(context);
        return false;
        //return sessionId!=null;
    }

    public static boolean isLogged() {
        return isLogged(null);
    }

    private static String cookieNames(List<Cookie> cookies) {
        StringBuilder names = new StringBuilder();
        for (Cookie cookie : cookies) {
            if (names.length() > 0) names.append(',');
            names.append(cookie.name());
        }
        return names.toString();
    }


    public static User getUser() {
        return user;
    }

    public static void updateUser(User user) {
        Login.user = user;
    }


    public static boolean isOnlineTags(Tag tag) {
        return Queries.TagTable.isBlackListed(tag);
    }

    public static void hasLogged(WebView webView) {
    }
}
