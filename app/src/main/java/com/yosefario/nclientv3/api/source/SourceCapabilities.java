package com.yosefario.nclientv3.api.source;

import com.yosefario.nclientv3.api.enums.SortType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SourceCapabilities {
    public static final SourceCapabilities NHENTAI =
        new SourceCapabilities(true, true, Collections.unmodifiableList(Arrays.asList(SortType.values())), 0);
    public static final SourceCapabilities EHENTAI =
        new SourceCapabilities(true, false, Collections.singletonList(SortType.RECENT_ALL_TIME), 10);

    private final boolean onlineFavorites;
    private final boolean random;
    private final List<SortType> sortOptions;
    private final int favoriteCategories;

    private SourceCapabilities(boolean onlineFavorites, boolean random, List<SortType> sortOptions, int favoriteCategories) {
        this.onlineFavorites = onlineFavorites;
        this.random = random;
        this.sortOptions = sortOptions;
        this.favoriteCategories = favoriteCategories;
    }

    public boolean hasOnlineFavorites() {
        return onlineFavorites;
    }

    public boolean hasRandom() {
        return random;
    }

    public List<SortType> sortOptions() {
        return sortOptions;
    }

    public boolean hasFavoriteCategories() {
        return favoriteCategories > 0;
    }

    public int favoriteCategories() {
        return favoriteCategories;
    }
}
