package com.yosefario.nclientv3.api.source.ehentai;

import androidx.annotation.NonNull;

import com.yosefario.nclientv3.settings.Global;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class EHentaiHttp {
    private EHentaiHttp() {
    }

    static Call call(String url) {
        return Global.client.newCall(new Request.Builder().url(url).build());
    }

    static Call pacedCall(String url) {
        EHentaiRateLimiter.acquire();
        return call(url);
    }

    static Call pacedPost(String url, RequestBody body) {
        EHentaiRateLimiter.acquire();
        return Global.client.newCall(new Request.Builder().url(url).post(body).build());
    }

    static String get(String url) throws IOException {
        return string(pacedCall(url));
    }

    static String string(Call call) throws IOException {
        try (Response response = call.execute()) {
            return body(response).string();
        }
    }

    static byte[] bytes(Call call) throws IOException {
        try (Response response = call.execute()) {
            return body(response).bytes();
        }
    }

    @NonNull
    static ResponseBody body(Response response) throws IOException {
        ResponseBody body = response.body();
        if (response.isSuccessful() && body != null) return body;
        response.close();
        throw new IOException("HTTP " + response.code() + " for " + response.request().url());
    }
}
