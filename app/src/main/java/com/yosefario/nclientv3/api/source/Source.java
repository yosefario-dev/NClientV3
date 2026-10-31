package com.yosefario.nclientv3.api.source;

import android.content.Context;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.api.enums.Language;

import java.io.IOException;

public interface Source {
    SourceId id();

    String displayName();

    String baseUrl();

    SourceCapabilities capabilities();

    GalleryPayload fetchGallery(Context context, GalleryKey key) throws IOException;

    GalleryPayload fetchRandomGallery(Context context) throws IOException;

    GalleryPayload fetchRandomFavorite(Context context) throws IOException;

    ListPayload fetchGalleries(Context context, SearchRequest request) throws IOException;

    ListPayload fetchFavorites(Context context, SearchRequest request) throws IOException;

    default boolean setOnlineFavorite(Context context, GalleryKey key, boolean favorite) throws IOException {
        throw new IOException("online favorites not supported by " + displayName());
    }

    default int setOnlineFavoriteSlot(Context context, GalleryKey key, int slot) throws IOException {
        return setOnlineFavorite(context, key, slot >= 0) ? Math.max(0, slot) : -1;
    }

    default boolean isLoggedIn() {
        return false;
    }

    String buildSearchQuery(SearchRequest request);

    String buildGalleryUrl(GalleryKey key);

    String favoritesUrl();

    @Nullable
    String languageQueryTerm(Language language);
}
