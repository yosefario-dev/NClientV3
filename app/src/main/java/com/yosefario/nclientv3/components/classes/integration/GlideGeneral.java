package com.yosefario.nclientv3.components.classes.integration;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiSpriteLoader;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiViewerLoader;

import java.io.InputStream;

@GlideModule
public class GlideGeneral extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        registry.prepend(GlideUrl.class, InputStream.class, new EHentaiViewerLoader().factory());
        registry.prepend(GlideUrl.class, InputStream.class, new EHentaiSpriteLoader().factory());
    }
}
