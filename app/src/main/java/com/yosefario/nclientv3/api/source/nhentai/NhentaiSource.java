package com.yosefario.nclientv3.api.source.nhentai;

import android.content.Context;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.settings.AuthRequest;
import com.yosefario.nclientv3.api.NhentaiV2Api;
import com.yosefario.nclientv3.api.components.Tag;
import com.yosefario.nclientv3.api.enums.ApiRequestType;
import com.yosefario.nclientv3.api.enums.Language;
import com.yosefario.nclientv3.api.enums.TagStatus;
import com.yosefario.nclientv3.api.source.GalleryKey;
import com.yosefario.nclientv3.api.source.GalleryPayload;
import com.yosefario.nclientv3.api.source.ListPayload;
import com.yosefario.nclientv3.api.source.SearchRequest;
import com.yosefario.nclientv3.api.source.Source;
import com.yosefario.nclientv3.api.source.SourceCapabilities;
import com.yosefario.nclientv3.api.source.SourceId;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Request;

import okhttp3.Response;

public class NhentaiSource implements Source {
    private final SourceCapabilities capabilities = SourceCapabilities.NHENTAI;

    @Override
    public SourceId id() {
        return SourceId.NHENTAI;
    }

    @Override
    public String displayName() {
        return SourceId.NHENTAI.displayName();
    }

    @Override
    public String baseUrl() {
        return Utility.getBaseUrl();
    }

    @Override
    public SourceCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public GalleryPayload fetchGallery(Context context, GalleryKey key) throws IOException {
        return new NhentaiV2Api(context).fetchGalleryPayload(key.id());
    }

    @Override
    public GalleryPayload fetchRandomGallery(Context context) throws IOException {
        return new NhentaiV2Api(context).fetchRandomGalleryPayload();
    }

    @Override
    public GalleryPayload fetchRandomFavorite(Context context) throws IOException {
        return new NhentaiV2Api(context).fetchRandomFavoriteGalleryPayload();
    }

    @Override
    public ListPayload fetchGalleries(Context context, SearchRequest request) throws IOException {
        return new NhentaiV2Api(context).fetchGalleries(buildSearchQuery(request), request.page(), request.sortType());
    }

    @Override
    public ListPayload fetchFavorites(Context context, SearchRequest request) throws IOException {
        return new NhentaiV2Api(context).fetchFavorites(buildSearchQuery(request), request.page());
    }

    @Override
    public boolean isLoggedIn() {
        return com.yosefario.nclientv3.settings.Login.isLogged();
    }

    @Override
    public boolean setOnlineFavorite(Context context, GalleryKey key, boolean favorite) throws IOException {
        String url = String.format(java.util.Locale.US, com.yosefario.nclientv3.utility.Utility.getBaseUrl()
            + "api/v2/galleries/%d/favorite", key.id());
        Request request = new Request.Builder()
            .url(url)
            .method(favorite ? "POST" : "DELETE", favorite ? AuthRequest.EMPTY_BODY : null)
            .build();
        try (okhttp3.Response r = com.yosefario.nclientv3.settings.Global.client.newCall(request).execute()) {
            if (!r.isSuccessful() || r.body() == null)
                throw new IOException("HTTP " + r.code() + " toggling favorite");
            try {
                return new org.json.JSONObject(r.body().string()).optBoolean("favorited", favorite);
            } catch (JSONException e) {
                return favorite;
            }
        }
    }

    @Override
    public String buildSearchQuery(SearchRequest request) {
        ApiRequestType type = request.requestType();
        if (type == ApiRequestType.BYALL || type == ApiRequestType.RANDOM || type == ApiRequestType.RANDOM_FAVORITE)
            return "";
        StringBuilder builder = new StringBuilder();
        append(builder, request.query());
        if (type == ApiRequestType.BYSEARCH || type == ApiRequestType.BYTAG) {
            for (Tag tag : request.tags()) {
                if (tag == null || builder.toString().contains(tag.toQueryTag(TagStatus.ACCEPTED))) continue;
                append(builder, tag.toQueryTag());
            }
            if (request.ranges() != null) append(builder, request.ranges().toQuery());
        }
        return builder.toString().trim();
    }

    private void append(StringBuilder builder, @Nullable String part) {
        if (part == null || part.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(part.trim());
    }

    @Override
    public String buildGalleryUrl(GalleryKey key) {
        return Utility.getBaseUrl() + "g/" + key.id();
    }

    @Override
    public String favoritesUrl() {
        return Utility.getBaseUrl() + "favorites/";
    }

    @Nullable
    @Override
    public String languageQueryTerm(Language language) {

        return null;
    }
}
