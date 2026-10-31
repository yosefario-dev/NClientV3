package com.yosefario.nclientv3.api.source;

import com.yosefario.nclientv3.api.SimpleGallery;

import java.util.List;

public class ListPayload {
    private final List<SimpleGallery> galleries;
    private final int pageCount, perPage, total;

    public ListPayload(List<SimpleGallery> galleries, int pageCount, int perPage, int total) {
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
