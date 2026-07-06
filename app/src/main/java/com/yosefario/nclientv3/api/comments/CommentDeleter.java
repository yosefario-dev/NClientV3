package com.yosefario.nclientv3.api.comments;

import androidx.annotation.NonNull;

import com.yosefario.nclientv3.loginapi.AuthApi;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CommentDeleter {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onSuccess();

        void onFailure(@NonNull String message);
    }

    private CommentDeleter() {}

    public static void delete(int commentId, @NonNull Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                AuthApi.deleteComment(commentId);
                callback.onSuccess();
            } catch (AuthApi.ApiException e) {
                callback.onFailure(e.error != null && !e.error.isEmpty()
                    ? e.error : ("HTTP " + e.httpCode));
            } catch (IOException e) {
                callback.onFailure(e.getMessage() == null ? "Network error" : e.getMessage());
            }
        });
    }
}
