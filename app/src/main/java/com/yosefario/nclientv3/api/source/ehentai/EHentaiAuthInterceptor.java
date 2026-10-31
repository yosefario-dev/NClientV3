package com.yosefario.nclientv3.api.source.ehentai;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class EHentaiAuthInterceptor implements Interceptor {
    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        String host = request.url().host();
        if (EHentaiUrls.isEHentaiHost(host)) {
            request = request.newBuilder()
                .header("Cookie", EHentaiAuth.cookieHeaderFor(request.url().toString()))
                .build();
        } else if (EHentaiUrls.isImageHost(host)) {
            request = request.newBuilder().removeHeader("Cookie").build();
        }
        return chain.proceed(request);
    }
}
