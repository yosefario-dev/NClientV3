package com.yosefario.nclientv3.loginapi;

import android.util.JsonReader;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.adapters.TagsAdapter;
import com.yosefario.nclientv3.api.components.Tag;
import com.yosefario.nclientv3.api.enums.TagType;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;

public class LoadTags extends Thread {
    @Nullable
    private final TagsAdapter adapter;

    public LoadTags(@Nullable TagsAdapter adapter) {
        this.adapter = adapter;
    }

    private void readTags(JsonReader reader) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            Tag tt = new Tag(reader);
            if (tt.getType() != TagType.LANGUAGE && tt.getType() != TagType.CATEGORY) {
                Login.addOnlineTag(tt);
                if (adapter != null) adapter.addItem();
            }
        }
    }

    private void readTags(JSONArray tags) throws IOException {
        Login.clearOnlineTags();
        JsonReader reader = new JsonReader(new StringReader(tags.toString()));
        readTags(reader);
        reader.close();
    }

    private JSONArray fetchApiTags() throws IOException, JSONException {
        String url = Utility.getBaseUrl() + "api/v2/blacklist";
        LogUtility.d(url);
        Response response = Global.getClient().newCall(new Request.Builder().url(url).build()).execute();
        try {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " while fetching " + url);
            if (response.body() == null) return new JSONArray();
            Object payload = new JSONTokener(response.body().string()).nextValue();
            if (payload instanceof JSONArray) return (JSONArray) payload;
            if (!(payload instanceof JSONObject)) return new JSONArray();
            JSONObject object = (JSONObject) payload;
            String[] keys = {"result", "tags", "blacklist", "blacklisted_tags", "items"};
            for (String key : keys) {
                JSONArray array = object.optJSONArray(key);
                if (array != null) return array;
            }
            return new JSONArray();
        } finally {
            response.close();
        }
    }

    private Elements getScripts(String url) throws IOException {
        Response response = Global.getClient().newCall(new Request.Builder().url(url).build()).execute();
        try {
            if (response.body() == null) return new Elements();
            return Jsoup.parse(response.body().byteStream(), null, Utility.getBaseUrl()).getElementsByTag("script");
        } finally {
            response.close();
        }
    }

    private String extractArray(Element e) throws StringIndexOutOfBoundsException {
        String text = e.toString();
        int start = text.indexOf('[');
        int end = text.indexOf(';', start);
        return text.substring(start, end);
    }

    private void fetchScrapedTags() throws IOException {
        String url = String.format(Locale.US, Utility.getBaseUrl() + "users/%s/%s/blacklist",
            Login.getUser().getId(), Login.getUser().getCodename()
        );
        LogUtility.d(url);
        analyzeScripts(getScripts(url));
    }

    @Override
    public void run() {
        super.run();
        if (Login.getUser() == null) return;
        try {
            readTags(fetchApiTags());
        } catch (IOException | JSONException e) {
            LogUtility.e("Unable to load API blacklist; falling back to HTML", e);
            try {
                fetchScrapedTags();
            } catch (IOException | StringIndexOutOfBoundsException fallbackError) {
                LogUtility.e("Unable to load HTML blacklist fallback", fallbackError);
            }
        }

    }

    private void analyzeScripts(Elements scripts) throws IOException, StringIndexOutOfBoundsException {
        if (scripts.size() > 0) {
            Login.clearOnlineTags();
            String array = Utility.unescapeUnicodeString(extractArray(scripts.last()));
            JsonReader reader = new JsonReader(new StringReader(array));
            readTags(reader);
            reader.close();
        }
    }
}
