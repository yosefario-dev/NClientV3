package com.yosefario.nclientv3.api.source.ehentai;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.HttpUrl;
import okhttp3.ResponseBody;

public final class EHentaiViewerLoader extends EHentaiImageLoader {

    @Override
    boolean handles(String url) {
        if (EHentaiPageResolver.isFullMarker(url)) return true;
        HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
        return parsed != null && EHentaiUrls.isEHentaiHost(parsed.host()) && parsed.encodedPath().startsWith("/s/");
    }

    @Override
    InputStream open(String url, Fetcher fetcher) throws IOException {
        String viewerUrl = EHentaiPageResolver.isFullMarker(url) ? EHentaiPageResolver.resolveViewer(url) : url;
        if (viewerUrl == null) throw new IOException("Unable to resolve e-hentai page");
        String imageUrl = EHentaiGalleryPageParser.parseViewerImageUrl(EHentaiHttp.string(fetcher.track(EHentaiHttp.pacedCall(viewerUrl))));
        if (imageUrl == null) throw new IOException("No image found on viewer page");
        ResponseBody body = EHentaiHttp.body(fetcher.track(EHentaiHttp.call(imageUrl)).execute());
        fetcher.hold(body);
        return body.byteStream();
    }
}
