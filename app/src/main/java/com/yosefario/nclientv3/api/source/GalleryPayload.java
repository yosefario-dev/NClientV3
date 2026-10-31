package com.yosefario.nclientv3.api.source;

import com.yosefario.nclientv3.api.SimpleGallery;

import java.util.List;

public class GalleryPayload {
    private final int id;
    private final String json;
    private final List<SimpleGallery> related;
    private final int favoriteSlot;

    public GalleryPayload(int id, String json, List<SimpleGallery> related, boolean favorite) {
        this(id, json, related, favorite ? 0 : -1);
    }

    public GalleryPayload(int id, String json, List<SimpleGallery> related, int favoriteSlot) {
        this.id = id;
        this.json = json;
        this.related = related;
        this.favoriteSlot = favoriteSlot;
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
        return favoriteSlot >= 0;
    }

    public int favoriteSlot() {
        return favoriteSlot;
    }
}
