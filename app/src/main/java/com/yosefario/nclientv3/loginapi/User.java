package com.yosefario.nclientv3.loginapi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class User {
    private static final List<CreateUser> PENDING_CALLBACKS = new ArrayList<>();
    private static boolean loading;
    private final String username, codename;
    private final int id;

    private User(String username, String id, String codename) {
        this(username, parseJsonInt(id), codename);
    }

    private User(String username, int id, String codename) {
        this.username = username;
        this.id = id;
        this.codename = codename == null ? "" : codename;
    }

    public static void createUser(final CreateUser createUser) {
        synchronized (User.class) {
            if (createUser != null) PENDING_CALLBACKS.add(createUser);
            if (loading) return;
            loading = true;
        }
        fetchApiUser();
    }

    private static void fetchApiUser() {
        Global.getClient().newCall(new Request.Builder().url(Utility.getBaseUrl() + "api/v2/user").build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                LogUtility.e("Unable to fetch API user; falling back to HTML", e);
                fetchHtmlUser();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                User user = null;
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        user = parseApiUser(response.body().string());
                    } else {
                        LogUtility.e("Unable to fetch API user: HTTP " + response.code());
                    }
                } catch (JSONException e) {
                    LogUtility.e("Unable to parse API user", e);
                } finally {
                    response.close();
                }
                if (user != null) finishCreateUser(user);
                else fetchHtmlUser();
            }
        });
    }

    private static void fetchHtmlUser() {
        String url = Login.BASE_HTTP_URL == null ? Utility.getBaseUrl() : Login.BASE_HTTP_URL.toString();
        Global.getClient().newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                LogUtility.e("Unable to fetch HTML user fallback", e);
                finishCreateUser(Login.getUser());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                User user = null;
                try {
                    if (response.isSuccessful() && response.body() != null) user = parseHtmlUser(response);
                    else LogUtility.e("Unable to fetch HTML user fallback: HTTP " + response.code());
                } finally {
                    response.close();
                }
                finishCreateUser(user);
            }
        });
    }

    private static void finishCreateUser(@Nullable User user) {
        Login.updateUser(user);
        notifyCallbacks(Login.getUser());
    }

    private static void notifyCallbacks(User user) {
        List<CreateUser> callbacks;
        synchronized (User.class) {
            loading = false;
            callbacks = new ArrayList<>(PENDING_CALLBACKS);
            PENDING_CALLBACKS.clear();
        }
        for (CreateUser callback : callbacks) callback.onCreateUser(user);
    }

    private static User parseApiUser(String body) throws JSONException {
        JSONObject payload = new JSONObject(body);
        JSONObject user = payload.optJSONObject("user");
        if (user == null) user = payload;
        int id = parseJsonInt(user.opt("id"));
        String username = firstNonEmpty(
            user.optString("username", ""),
            user.optString("name", ""),
            user.optString("display_name", "")
        );
        String codename = firstNonEmpty(
            user.optString("slug", ""),
            user.optString("codename", ""),
            extractCodename(user.optString("url", ""))
        );
        if (username.isEmpty()) username = codename;
        if (codename.isEmpty()) codename = username;
        return id > 0 && !username.isEmpty() ? new User(username, id, codename) : null;
    }

    @Nullable
    private static User parseHtmlUser(Response response) throws IOException {
        if (response.body() == null) return null;
        Document doc = Jsoup.parse(response.body().byteStream(), null, Utility.getBaseUrl());
        Elements elements = doc.getElementsByClass("fa-tachometer-alt");
        if (elements.isEmpty()) return null;
        Element anchor = elements.first() == null ? null : elements.first().parent();
        if (anchor == null) return null;
        String username = anchor.text().trim();
        String[] parts = anchor.attr("href").split("/");
        if (parts.length < 4) return null;
        return new User(username, parts[2], parts[3]);
    }

    private static String extractCodename(String url) {
        if (url == null || url.isEmpty()) return "";
        String[] parts = url.split("/");
        for (int index = parts.length - 1; index >= 0; index--) {
            if (!parts[index].isEmpty()) return parts[index];
        }
        return "";
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static int parseJsonInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) return parseJsonInt((String) value);
        return 0;
    }

    private static int parseJsonInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return username + '(' + id + '/' + codename + ')';
    }

    public String getUsername() {
        return username;
    }

    public int getId() {
        return id;
    }

    public String getCodename() {
        return codename;
    }

    public interface CreateUser {
        void onCreateUser(User user);
    }


}
