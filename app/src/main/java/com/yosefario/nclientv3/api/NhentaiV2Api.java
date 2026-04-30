package com.yosefario.nclientv3.api;

import android.content.Context;
import android.net.Uri;
import android.util.JsonReader;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.api.components.Page;
import com.yosefario.nclientv3.api.components.Tag;
import com.yosefario.nclientv3.api.components.TagList;
import com.yosefario.nclientv3.api.enums.ImageExt;
import com.yosefario.nclientv3.api.enums.SortType;
import com.yosefario.nclientv3.async.database.Queries;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import okhttp3.Request;
import okhttp3.Response;

public class NhentaiV2Api {
    private static final String API_GALLERIES = "api/v2/galleries";
    private static final String API_SEARCH = "api/v2/search";
    private static final String API_FAVORITES = "api/v2/favorites";
    private final Context context;
    private final Random random = new Random();

    public NhentaiV2Api(Context context) {
        this.context = context;
    }

    public GalleryPayload fetchGalleryPayload(int galleryId) throws IOException {
        return parseGalleryPayload(fetchText(getSingleGalleryApiUrl(galleryId)));
    }

    public GalleryPayload fetchRandomGalleryPayload() throws IOException {
        int galleryId = fetchRandomGalleryId();
        return fetchGalleryPayload(galleryId);
    }

    public GalleryPayload fetchRandomFavoriteGalleryPayload() throws IOException {
        ListPayload firstPage = fetchFavorites(null, 1);
        int pageCount = Math.max(1, firstPage.getPageCount());
        int selectedPage = pageCount == 1 ? 1 : random.nextInt(pageCount) + 1;
        ListPayload selectedPayload = selectedPage == 1 ? firstPage : fetchFavorites(null, selectedPage);
        List<SimpleGallery> favorites = selectedPayload.getGalleries();
        if (favorites.isEmpty()) throw new IOException("No online favorites available");
        SimpleGallery selected = favorites.get(random.nextInt(favorites.size()));
        return fetchGalleryPayload(selected.getId());
    }

    public ListPayload fetchGalleries(@Nullable String query, int page, @Nullable SortType sortType) throws IOException {
        return parseListPayload(fetchText(buildGalleryListApiUrl(query, page, sortType)));
    }

    public ListPayload fetchFavorites(@Nullable String query, int page) throws IOException {
        return parseListPayload(fetchText(buildFavoritesApiUrl(query, page)));
    }

    public GalleryPayload parseGalleryPayload(String body) throws IOException {
        try {
            JSONObject gallery = new JSONObject(body);
            String legacyJson = convertV2GalleryJson(gallery);
            List<SimpleGallery> related = parseSimpleGalleryList(gallery.optJSONArray("related"));
            LogUtility.d("NhentaiV2Api: parsed gallery id=" + parseJsonInt(gallery, "id"));
            return new GalleryPayload(parseJsonInt(gallery, "id"), legacyJson, related, isFavorite(gallery));
        } catch (JSONException e) {
            throw new IOException("Unable to parse gallery API response", e);
        }
    }

    private int fetchRandomGalleryId() throws IOException {
        try {
            JSONObject randomGallery = new JSONObject(fetchText(Utility.getBaseUrl() + API_GALLERIES + "/random"));
            int galleryId = parseJsonInt(randomGallery, "id");
            if (galleryId <= 0) throw new IOException("Invalid random gallery id");
            LogUtility.d("NhentaiV2Api: random API selected id=" + galleryId);
            return galleryId;
        } catch (JSONException e) {
            throw new IOException("Unable to parse random gallery API response", e);
        }
    }

    private String getSingleGalleryApiUrl(int galleryId) {
        return Utility.getBaseUrl() + API_GALLERIES + "/" + galleryId + "?include=comments%2Crelated";
    }

    private String buildGalleryListApiUrl(@Nullable String query, int page, @Nullable SortType sortType) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (!normalizedQuery.isEmpty()) return buildSearchApiUrl(normalizedQuery, page, sortType);
        Uri.Builder builder = Uri.parse(Utility.getBaseUrl() + API_GALLERIES).buildUpon()
            .appendQueryParameter("page", String.valueOf(Math.max(1, page)));
        return builder.build().toString();
    }

    private String buildSearchApiUrl(String query, int page, @Nullable SortType sortType) {
        Uri.Builder builder = Uri.parse(Utility.getBaseUrl() + API_SEARCH).buildUpon()
            .appendQueryParameter("query", query)
            .appendQueryParameter("page", String.valueOf(Math.max(1, page)));
        if (sortType != null && sortType.getUrlAddition() != null)
            builder.appendQueryParameter("sort", sortType.getUrlAddition());
        return builder.build().toString();
    }

    private String buildFavoritesApiUrl(@Nullable String query, int page) {
        Uri.Builder builder = Uri.parse(Utility.getBaseUrl() + API_FAVORITES).buildUpon()
            .appendQueryParameter("page", String.valueOf(Math.max(1, page)));
        if (query != null && !query.trim().isEmpty()) builder.appendQueryParameter("q", query.trim());
        return builder.build().toString();
    }

    private String fetchText(String fetchUrl) throws IOException {
        LogUtility.d("NhentaiV2Api: fetching URL: " + fetchUrl);
        Response response = Global.getClient(context).newCall(new Request.Builder().url(fetchUrl).build()).execute();
        try {
            int code = response.code();
            LogUtility.d("NhentaiV2Api: response code=" + code + " for URL: " + fetchUrl);
            if (code != HttpURLConnection.HTTP_OK) {
                String preview = response.peekBody(1024).string().replace('\n', ' ').replace('\r', ' ');
                LogUtility.e("NhentaiV2Api: non-OK response code=" + code + " for URL: " + fetchUrl + "; preview=" + preview);
                throw new IOException("HTTP " + code + " while fetching " + fetchUrl);
            }
            if (response.body() == null) throw new IOException("Empty response body while fetching " + fetchUrl);
            return response.body().string();
        } finally {
            response.close();
        }
    }

    private ListPayload parseListPayload(String body) throws IOException {
        try {
            Object payload = new JSONTokener(body).nextValue();
            JSONObject envelope = payload instanceof JSONObject ? (JSONObject) payload : null;
            JSONArray result = payload instanceof JSONArray ? (JSONArray) payload : findListArray(envelope);
            if (result == null) throw new IOException("Gallery list API response has no gallery array");
            List<SimpleGallery> galleries = parseSimpleGalleryList(result);
            int pageCount = Math.max(1, envelope == null ? 1 : envelope.optInt("num_pages", 1));
            int perPage = envelope == null ? result.length() : envelope.optInt("per_page", result.length());
            int total = envelope == null ? result.length() : envelope.optInt("total", Math.max(galleries.size(), pageCount * Math.max(1, perPage)));
            LogUtility.d(String.format(Locale.US, "NhentaiV2Api: parsed list galleries=%d pageCount=%d total=%d", galleries.size(), pageCount, total));
            return new ListPayload(galleries, pageCount, perPage, total);
        } catch (JSONException e) {
            throw new IOException("Unable to parse gallery list API response", e);
        }
    }

    @Nullable
    private JSONArray findListArray(@Nullable JSONObject envelope) {
        if (envelope == null) return null;
        String[] keys = {"result", "galleries", "favorites", "items", "data"};
        for (String key : keys) {
            JSONArray result = envelope.optJSONArray(key);
            if (result != null) return result;
        }
        return null;
    }

    private List<SimpleGallery> parseSimpleGalleryList(@Nullable JSONArray result) {
        List<SimpleGallery> galleries = new ArrayList<>();
        if (result == null) return galleries;
        for (int index = 0; index < result.length(); index++) {
            JSONObject gallery = result.optJSONObject(index);
            if (gallery == null || gallery.optBoolean("blacklisted")) continue;
            SimpleGallery simpleGallery = parseSimpleGallery(gallery);
            if (simpleGallery.isValid()) galleries.add(simpleGallery);
        }
        return galleries;
    }

    private SimpleGallery parseSimpleGallery(JSONObject gallery) {
        JSONObject nestedGallery = gallery.optJSONObject("gallery");
        if (nestedGallery != null) gallery = nestedGallery;
        String thumbnailPath = readThumbnailPath(gallery);
        int mediaId = parseJsonInt(gallery, "media_id");
        if (mediaId <= 0) mediaId = parseMediaId(thumbnailPath);
        return new SimpleGallery(
            context,
            readTitle(gallery),
            parseJsonInt(gallery, "id"),
            mediaId,
            Page.charToExt(extensionToType(thumbnailPath)),
            buildThumbnailUrl(thumbnailPath),
            gallery.optInt("num_pages"),
            readTagList(gallery)
        );
    }

    private String readTitle(JSONObject gallery) {
        String title = gallery.optString("english_title", "");
        if (!title.isEmpty()) return title;
        title = gallery.optString("japanese_title", "");
        if (!title.isEmpty()) return title;
        JSONObject titleObject = gallery.optJSONObject("title");
        if (titleObject == null) return "";
        title = titleObject.optString("english", "");
        if (!title.isEmpty()) return title;
        title = titleObject.optString("pretty", "");
        return title.isEmpty() ? titleObject.optString("japanese", "") : title;
    }

    private String readThumbnailPath(JSONObject gallery) {
        Object thumbnail = gallery.opt("thumbnail");
        if (thumbnail instanceof String) return (String) thumbnail;
        if (thumbnail instanceof JSONObject) return readImagePath((JSONObject) thumbnail);
        return "";
    }

    private TagList readTagList(JSONObject gallery) {
        JSONArray tagIds = gallery.optJSONArray("tag_ids");
        if (tagIds != null && tagIds.length() > 0) return parseTagIds(tagIds);
        JSONArray tags = gallery.optJSONArray("tags");
        if (tags != null && tags.length() > 0) return parseTags(tags);
        return new TagList();
    }

    private TagList parseTagIds(@Nullable JSONArray tagIds) {
        if (tagIds == null || tagIds.length() == 0) return new TagList();
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < tagIds.length(); index++) {
            int tagId = tagIds.optInt(index);
            if (tagId <= 0) continue;
            if (builder.length() > 0) builder.append(',');
            builder.append(tagId);
        }
        return builder.length() == 0 ? new TagList() : Queries.TagTable.getTagsFromListOfInt(builder.toString());
    }

    private TagList parseTags(JSONArray tags) {
        TagList tagList = new TagList();
        JsonReader reader = new JsonReader(new StringReader(tags.toString()));
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                Tag tag = new Tag(reader);
                Queries.TagTable.insert(tag);
                tagList.addTag(tag);
            }
            reader.endArray();
        } catch (IOException e) {
            LogUtility.e("Unable to parse API tag array", e);
        } finally {
            try {
                reader.close();
            } catch (IOException ignore) {
            }
        }
        return tagList;
    }

    private String convertV2GalleryJson(JSONObject gallery) throws JSONException {
        JSONObject legacy = new JSONObject();
        legacy.put("id", parseJsonInt(gallery, "id"));
        legacy.put("media_id", parseJsonInt(gallery, "media_id"));
        legacy.put("title", gallery.optJSONObject("title") == null ? new JSONObject() : gallery.optJSONObject("title"));
        legacy.put("upload_date", gallery.optLong("upload_date"));
        legacy.put("num_pages", gallery.optInt("num_pages", gallery.optJSONArray("pages") == null ? 0 : gallery.optJSONArray("pages").length()));
        legacy.put("num_favorites", gallery.optInt("num_favorites"));
        legacy.put("tags", gallery.optJSONArray("tags") == null ? new JSONArray() : gallery.optJSONArray("tags"));

        JSONObject images = new JSONObject();
        images.put("cover", convertV2Image(readImageObject(gallery, "cover")));
        images.put("thumbnail", convertV2Image(readImageObject(gallery, "thumbnail")));
        JSONArray pages = new JSONArray();
        JSONArray v2Pages = gallery.optJSONArray("pages");
        if (v2Pages != null) {
            for (int index = 0; index < v2Pages.length(); index++) {
                pages.put(convertV2Image(v2Pages.optJSONObject(index)));
            }
        }
        images.put("pages", pages);
        legacy.put("images", images);
        return legacy.toString();
    }

    private JSONObject convertV2Image(@Nullable JSONObject image) throws JSONException {
        JSONObject converted = new JSONObject();
        String path = readImagePath(image);
        converted.put("t", String.valueOf(extensionToType(path)));
        converted.put("w", image == null ? 0 : image.optInt("width"));
        converted.put("h", image == null ? 0 : image.optInt("height"));
        converted.put("path", path);
        if (image != null && image.has("thumbnail")) converted.put("thumbnail", image.optString("thumbnail"));
        return converted;
    }

    @Nullable
    private JSONObject readImageObject(JSONObject object, String key) throws JSONException {
        Object value = object.opt(key);
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) {
            JSONObject image = new JSONObject();
            image.put("path", value);
            return image;
        }
        return null;
    }

    private String readImagePath(@Nullable JSONObject image) {
        if (image == null) return "";
        String path = image.optString("path", "");
        if (!path.isEmpty()) return path;
        return image.optString("url", "");
    }

    private String buildThumbnailUrl(String path) {
        if (path == null || path.isEmpty()) return "";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return "https://t." + Utility.getHost() + "/" + path;
    }

    private int parseJsonInt(JSONObject object, String key) {
        Object value = object.opt(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignore) {
                return 0;
            }
        }
        return 0;
    }

    private int parseMediaId(String path) {
        if (path == null) return 0;
        int start = path.indexOf("galleries/");
        if (start < 0) return 0;
        start += "galleries/".length();
        int end = path.indexOf('/', start);
        if (end < 0) return 0;
        try {
            return Integer.parseInt(path.substring(start, end));
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    private char extensionToType(String path) {
        if (path == null) return 'j';
        String lowerPath = path.toLowerCase(Locale.US);
        if (lowerPath.endsWith(".gif")) return 'g';
        if (lowerPath.endsWith(".png")) return 'p';
        if (lowerPath.endsWith(".webp") || lowerPath.endsWith(".avif")) return 'w';
        return 'j';
    }

    private boolean isFavorite(JSONObject gallery) {
        return gallery.optBoolean("favorite") || gallery.optBoolean("is_favorite") || gallery.optBoolean("is_favorited");
    }

    public static class GalleryPayload {
        private final int id;
        private final String json;
        private final List<SimpleGallery> related;
        private final boolean favorite;

        GalleryPayload(int id, String json, List<SimpleGallery> related, boolean favorite) {
            this.id = id;
            this.json = json;
            this.related = related;
            this.favorite = favorite;
        }

        public int getId() {
            return id;
        }

        public String getJson() {
            return json;
        }

        public List<SimpleGallery> getRelated() {
            return related;
        }

        public boolean isFavorite() {
            return favorite;
        }
    }

    public static class ListPayload {
        private final List<SimpleGallery> galleries;
        private final int pageCount, perPage, total;

        private ListPayload(List<SimpleGallery> galleries, int pageCount, int perPage, int total) {
            this.galleries = galleries;
            this.pageCount = pageCount;
            this.perPage = perPage;
            this.total = total;
        }

        public List<SimpleGallery> getGalleries() {
            return galleries;
        }

        public int getPageCount() {
            return pageCount;
        }

        public int getPerPage() {
            return perPage;
        }

        public int getTotal() {
            return total;
        }
    }
}