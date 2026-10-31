package com.yosefario.nclientv3.api.source.ehentai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yosefario.nclientv3.api.SimpleGallery;
import com.yosefario.nclientv3.api.components.Page;
import com.yosefario.nclientv3.api.enums.ImageExt;

public final class EHentaiGalleryMapper {
    private EHentaiGalleryMapper() {
    }

    public static SimpleGallery toSimpleGallery(@Nullable Context ctx, EHentaiGallery g) {
        String title = g.displayTitle();
        if (title == null) title = "";
        SimpleGallery sg = new SimpleGallery(ctx, title, g.gid, 0, extFromUrl(g.thumb), g.thumb, g.filecount, null);
        sg.setToken(g.token);
        return sg;
    }

    @NonNull
    public static ImageExt extFromUrl(@Nullable String url) {
        if (url == null) return ImageExt.JPG;
        int dot = url.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= url.length()) return ImageExt.JPG;
        return Page.charToExt(url.charAt(dot + 1));
    }
}
