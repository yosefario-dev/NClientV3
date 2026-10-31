package com.yosefario.nclientv3.api.source.ehentai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.settings.Global;

import java.io.IOException;

import okhttp3.Cookie;
import okhttp3.HttpUrl;

public final class EHentaiAuth {
    private static final String PREF = "Settings";
    private static final String KEY_MEMBER = "eh_member_id";
    private static final String KEY_HASH = "eh_pass_hash";
    private static final String KEY_IGNEOUS = "eh_igneous";
    private static final String KEY_NAME = "eh_display_name";

    private static volatile String memberId;
    private static volatile String passHash;
    private static volatile String igneous;
    private static volatile String displayName;

    private EHentaiAuth() {
    }

    public static synchronized void init(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        memberId = emptyToNull(p.getString(KEY_MEMBER, null));
        passHash = emptyToNull(p.getString(KEY_HASH, null));
        igneous = emptyToNull(p.getString(KEY_IGNEOUS, null));
        displayName = emptyToNull(p.getString(KEY_NAME, null));
    }

    public static boolean isLoggedIn() {
        return memberId != null && passHash != null;
    }

    @Nullable
    public static String getDisplayName() {
        return displayName;
    }

    public static synchronized void setDisplayName(Context context, @Nullable String name) {
        displayName = emptyToNull(name);
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, displayName).apply();
    }

    public static boolean hasExhentaiAccess() {
        return isLoggedIn() && igneous != null;
    }

    public static String cookieHeader(boolean exhentai) {
        String member = memberId, hash = passHash, ign = igneous;
        StringBuilder sb = new StringBuilder("nw=1");
        if (member != null && hash != null) {
            sb.append("; ipb_member_id=").append(member);
            sb.append("; ipb_pass_hash=").append(hash);
            if (exhentai && ign != null) sb.append("; igneous=").append(ign);
        }
        return sb.toString();
    }

    public static String cookieHeaderFor(@Nullable String url) {
        return cookieHeader(url != null && url.contains("exhentai.org"));
    }

    public static synchronized boolean storeFromCookieString(Context context, @Nullable String cookies) {
        if (cookies == null) return false;
        String member = null;
        String hash = null;
        String ign = null;
        for (String part : cookies.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if ("ipb_member_id".equals(kv[0])) member = kv[1];
            else if ("ipb_pass_hash".equals(kv[0])) hash = kv[1];
            else if ("igneous".equals(kv[0]) && !"mystery".equals(kv[1]) && !kv[1].isEmpty()) ign = kv[1];
        }
        if (member == null || hash == null || member.isEmpty() || "0".equals(member)) return false;
        memberId = member;
        passHash = hash;
        if (ign != null) igneous = ign;
        SharedPreferences.Editor e = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_MEMBER, member).putString(KEY_HASH, hash);
        if (ign != null) e.putString(KEY_IGNEOUS, ign);
        e.apply();
        return true;
    }

    public static synchronized void refreshIgneous(Context context) {
        if (!isLoggedIn()) return;
        try {
            EHentaiHttp.call(EHentaiUrls.host(true)).execute().close();
            for (Cookie c : Global.client.cookieJar().loadForRequest(HttpUrl.get(EHentaiUrls.host(true)))) {
                if ("igneous".equals(c.name()) && !"mystery".equals(c.value()) && !c.value().isEmpty()) {
                    igneous = c.value();
                    context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                        .putString(KEY_IGNEOUS, igneous).apply();
                }
            }
        } catch (IOException ignore) {
        }
    }

    public static synchronized void logout(Context context) {
        memberId = null;
        passHash = null;
        igneous = null;
        displayName = null;
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .remove(KEY_MEMBER).remove(KEY_HASH).remove(KEY_IGNEOUS).remove(KEY_NAME).apply();
    }

    @Nullable
    private static String emptyToNull(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
