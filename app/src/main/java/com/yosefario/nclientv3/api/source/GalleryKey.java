package com.yosefario.nclientv3.api.source;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

public final class GalleryKey {
    private final SourceId source;
    private final int id;
    @Nullable
    private final String token;

    public GalleryKey(SourceId source, int id, @Nullable String token) {
        this.source = source;
        this.id = id;
        this.token = token;
    }

    public static GalleryKey of(SourceId source, int id, @Nullable String token) {
        return new GalleryKey(source, id, token);
    }

    public static GalleryKey nhentai(int id) {
        return new GalleryKey(SourceId.NHENTAI, id, null);
    }

    public SourceId source() {
        return source;
    }

    public int id() {
        return id;
    }

    @Nullable
    public String token() {
        return token;
    }

    @NonNull
    public String storageKey() {
        return String.format(Locale.US, "%s:%d", source.prefix(), id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GalleryKey)) return false;
        GalleryKey that = (GalleryKey) o;
        return id == that.id && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, id);
    }

    @NonNull
    @Override
    public String toString() {
        return storageKey() + (token == null ? "" : "/" + token);
    }
}
