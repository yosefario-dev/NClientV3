package com.yosefario.nclientv3.api.source.ehentai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class EHentaiSpriteLoader extends EHentaiImageLoader {

    private static final String MARKER = "#ehcrop=";

    private static final LruCache<String, Bitmap> SPRITES = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    @Override
    boolean handles(String url) {
        return url != null && (url.contains(MARKER) || EHentaiPageResolver.isThumbMarker(url));
    }

    @Override
    InputStream open(String url, Fetcher fetcher) throws IOException {
        if (EHentaiPageResolver.isThumbMarker(url)) {
            url = EHentaiPageResolver.resolveThumb(url);
            if (url == null) throw new IOException("Unable to resolve e-hentai thumbnail");
        }
        int marker = url.indexOf(MARKER);
        Bitmap sprite = sprite(marker < 0 ? url : url.substring(0, marker), fetcher);
        if (marker < 0) return png(sprite);
        String[] crop = url.substring(marker + MARKER.length()).split(",");
        int x = Math.max(0, Math.min(Integer.parseInt(crop[0]), sprite.getWidth() - 1));
        int y = Math.max(0, Math.min(Integer.parseInt(crop[1]), sprite.getHeight() - 1));
        int w = Math.min(Integer.parseInt(crop[2]), sprite.getWidth() - x);
        int h = Math.min(Integer.parseInt(crop[3]), sprite.getHeight() - y);
        if (w <= 0 || h <= 0) throw new IOException("Invalid crop rectangle");
        Bitmap cropped = Bitmap.createBitmap(sprite, x, y, w, h);
        InputStream out = png(cropped);
        if (cropped != sprite) cropped.recycle();
        return out;
    }

    private static InputStream png(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static Bitmap sprite(String spriteUrl, Fetcher fetcher) throws IOException {
        Bitmap cached = SPRITES.get(spriteUrl);
        if (cached != null && !cached.isRecycled()) return cached;
        byte[] bytes = EHentaiHttp.bytes(fetcher.track(EHentaiHttp.call(spriteUrl)));
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) throw new IOException("Unable to decode thumbnail");
        SPRITES.put(spriteUrl, bitmap);
        return bitmap;
    }
}
