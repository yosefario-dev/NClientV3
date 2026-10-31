package com.yosefario.nclientv3.api.source;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.api.components.Ranges;
import com.yosefario.nclientv3.api.components.Tag;
import com.yosefario.nclientv3.api.enums.ApiRequestType;
import com.yosefario.nclientv3.api.enums.SortType;

import java.util.Collections;
import java.util.Set;

public final class SearchRequest {
    private final ApiRequestType requestType;
    private final String query;
    private final Set<Tag> tags;
    @Nullable
    private final Ranges ranges;
    private final int page;
    @Nullable
    private final SortType sortType;

    public SearchRequest(ApiRequestType requestType, String query, Set<Tag> tags,
                         @Nullable Ranges ranges, int page, @Nullable SortType sortType) {
        this.requestType = requestType;
        this.query = query == null ? "" : query;
        this.tags = tags == null ? Collections.emptySet() : tags;
        this.ranges = ranges;
        this.page = page;
        this.sortType = sortType;
    }

    public ApiRequestType requestType() {
        return requestType;
    }

    public String query() {
        return query;
    }

    public Set<Tag> tags() {
        return tags;
    }

    @Nullable
    public Ranges ranges() {
        return ranges;
    }

    public int page() {
        return page;
    }

    @Nullable
    public SortType sortType() {
        return sortType;
    }
}
