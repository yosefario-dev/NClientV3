package com.yosefario.nclientv3.api.source.ehentai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;

abstract class EHentaiImageLoader implements ModelLoader<GlideUrl, InputStream> {

    abstract boolean handles(String url);

    abstract InputStream open(String url, Fetcher fetcher) throws IOException;

    @Nullable
    @Override
    public final LoadData<InputStream> buildLoadData(@NonNull GlideUrl model, int width, int height, @NonNull Options options) {
        return new LoadData<>(model, new Fetcher(this, model.toStringUrl()));
    }

    @Override
    public final boolean handles(@NonNull GlideUrl model) {
        return handles(model.toStringUrl());
    }

    public final ModelLoaderFactory<GlideUrl, InputStream> factory() {
        return new ModelLoaderFactory<GlideUrl, InputStream>() {
            @NonNull
            @Override
            public ModelLoader<GlideUrl, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
                return EHentaiImageLoader.this;
            }

            @Override
            public void teardown() {
            }
        };
    }

    static final class Fetcher implements DataFetcher<InputStream> {
        private final EHentaiImageLoader loader;
        private final String url;
        private volatile Call call;
        private Closeable resource;

        private Fetcher(EHentaiImageLoader loader, String url) {
            this.loader = loader;
            this.url = url;
        }

        Call track(Call call) {
            this.call = call;
            return call;
        }

        void hold(Closeable resource) {
            this.resource = resource;
        }

        @Override
        public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
            try {
                callback.onDataReady(loader.open(url, this));
            } catch (Exception e) {
                callback.onLoadFailed(e);
            }
        }

        @Override
        public void cleanup() {
            Closeable local = resource;
            if (local == null) return;
            try {
                local.close();
            } catch (IOException ignore) {
            }
        }

        @Override
        public void cancel() {
            Call local = call;
            if (local != null) local.cancel();
        }

        @NonNull
        @Override
        public Class<InputStream> getDataClass() {
            return InputStream.class;
        }

        @NonNull
        @Override
        public DataSource getDataSource() {
            return DataSource.REMOTE;
        }
    }
}
