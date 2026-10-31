package com.yosefario.nclientv3.api.source.ehentai;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class EHentaiPageResolver {
    private static final String HOST = "ehlazy.invalid";
    private static final int MAX_CACHED = 12;

    private static final LinkedHashMap<String, EHentaiGalleryPageParser.Result> CACHE =
        new LinkedHashMap<String, EHentaiGalleryPageParser.Result>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, EHentaiGalleryPageParser.Result> eldest) {
                return size() > MAX_CACHED;
            }
        };

    private static final Map<String, Object> LOCKS = new HashMap<>();

    private EHentaiPageResolver() {
    }

    static String fullMarker(boolean exhentai, int gid, String token, int page, int perPage) {
        return marker("p", exhentai, gid, token, page, perPage);
    }

    static String thumbMarker(boolean exhentai, int gid, String token, int page, int perPage) {
        return marker("t", exhentai, gid, token, page, perPage);
    }

    private static String marker(String kind, boolean exhentai, int gid, String token, int page, int perPage) {
        return "https://" + HOST + "/" + kind + "/" + gid + "/" + token + "/" + page + "/" + perPage
            + (exhentai ? "?ex=1" : "");
    }

    static boolean isFullMarker(@Nullable String url) {
        return url != null && url.startsWith("https://" + HOST + "/p/");
    }

    static boolean isThumbMarker(@Nullable String url) {
        return url != null && url.startsWith("https://" + HOST + "/t/");
    }

    static void seed(boolean exhentai, int gid, int p, EHentaiGalleryPageParser.Result res) {
        synchronized (CACHE) {
            CACHE.put(cacheKey(exhentai, gid, p), res);
        }
    }

    @Nullable
    static String resolveViewer(String marker) throws IOException {
        EHentaiGalleryPageParser.PageRef ref = resolve(marker);
        if (ref == null) return null;
        return EHentaiUrls.viewerUrl(isEx(marker), ref.token, ref.gid, ref.page);
    }

    @Nullable
    static String resolveThumb(String marker) throws IOException {
        EHentaiGalleryPageParser.PageRef ref = resolve(marker);
        return ref == null ? null : ref.thumbUrl;
    }

    @Nullable
    public static String resolveImageUrl(String pageUrl) throws IOException {
        String viewerUrl = isFullMarker(pageUrl) ? resolveViewer(pageUrl) : pageUrl;
        if (viewerUrl == null) return null;
        return EHentaiGalleryPageParser.parseViewerImageUrl(EHentaiHttp.get(viewerUrl));
    }

    @Nullable
    private static EHentaiGalleryPageParser.PageRef resolve(String marker) throws IOException {
        Uri uri = Uri.parse(marker);
        List<String> seg = uri.getPathSegments();
        if (seg.size() < 5) return null;
        boolean exhentai = "1".equals(uri.getQueryParameter("ex"));
        int gid, page, perPage;
        try {
            gid = Integer.parseInt(seg.get(1));
            page = Integer.parseInt(seg.get(3));
            perPage = Integer.parseInt(seg.get(4));
        } catch (NumberFormatException e) {
            return null;
        }
        if (perPage <= 0) return null;
        String token = seg.get(2);
        int p = (page - 1) / perPage;
        EHentaiGalleryPageParser.Result res = galleryPage(exhentai, gid, token, p);
        for (EHentaiGalleryPageParser.PageRef ref : res.pages)
            if (ref.page == page) return ref;
        return null;
    }

    private static boolean isEx(String marker) {
        return "1".equals(Uri.parse(marker).getQueryParameter("ex"));
    }

    private static String cacheKey(boolean exhentai, int gid, int p) {
        return (exhentai ? "ex" : "eh") + ":" + gid + ":" + p;
    }

    private static EHentaiGalleryPageParser.Result galleryPage(boolean exhentai, int gid, String token, int p) throws IOException {
        String key = cacheKey(exhentai, gid, p);
        EHentaiGalleryPageParser.Result cached = cacheGet(key);
        if (cached != null) return cached;
        Object lock;
        synchronized (LOCKS) {
            lock = LOCKS.get(key);
            if (lock == null) {
                lock = new Object();
                LOCKS.put(key, lock);
            }
        }
        synchronized (lock) {
            try {
                cached = cacheGet(key);
                if (cached != null) return cached;
                String html = EHentaiHttp.get(EHentaiUrls.galleryPageUrl(exhentai, gid, token, p));
                EHentaiGalleryPageParser.Result res = EHentaiGalleryPageParser.parse(html);

                if (!res.pages.isEmpty()) {
                    synchronized (CACHE) {
                        CACHE.put(key, res);
                    }
                }
                return res;
            } finally {

                synchronized (LOCKS) {
                    LOCKS.remove(key);
                }
            }
        }
    }

    @Nullable
    private static EHentaiGalleryPageParser.Result cacheGet(String key) {
        synchronized (CACHE) {
            return CACHE.get(key);
        }
    }
}
