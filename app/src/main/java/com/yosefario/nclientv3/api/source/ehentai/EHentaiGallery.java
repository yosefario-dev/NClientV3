package com.yosefario.nclientv3.api.source.ehentai;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EHentaiGallery {
    public int gid;
    @Nullable
    public String token;
    @Nullable
    public String title;
    @Nullable
    public String titleJpn;
    public EHentaiCategory category = EHentaiCategory.UNKNOWN;

    @Nullable
    public String thumb;
    @Nullable
    public String uploader;

    public long posted;
    public int filecount;
    public long filesize;
    public boolean expunged;

    public double rating;
    public int torrentcount;

    public final List<String> tags = new ArrayList<>();

    @Nullable
    public String displayTitle() {
        if (title != null && !title.isEmpty()) return title;
        return titleJpn;
    }
}
