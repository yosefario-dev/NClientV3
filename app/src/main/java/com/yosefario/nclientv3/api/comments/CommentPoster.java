package com.yosefario.nclientv3.api.comments;

import android.util.JsonReader;

import androidx.annotation.NonNull;

import com.yosefario.nclientv3.loginapi.AuthApi;
import com.yosefario.nclientv3.loginapi.PowSolver;
import com.yosefario.nclientv3.utility.LogUtility;

import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CommentPoster {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public interface Callback {
        void onProgress(@NonNull String message);

        void onSuccess(@NonNull Comment comment);

        void onFailure(@NonNull String message, boolean captchaRejected);
    }

    private CommentPoster() {}

    public static void post(int galleryId, @NonNull String text, @NonNull String captchaToken,
                            @NonNull Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                callback.onProgress("Solving challenge");
                AuthApi.PowChallenge challenge = AuthApi.fetchPowChallenge(AuthApi.ACTION_COMMENT);
                long t0 = System.nanoTime();
                String nonce = PowSolver.solve(challenge.challenge, challenge.difficulty);
                LogUtility.d("CommentPoster: PoW solved difficulty=" + challenge.difficulty
                    + " in " + ((System.nanoTime() - t0) / 1_000_000) + "ms");

                callback.onProgress("Posting comment");
                JSONObject resp;
                try {
                    resp = AuthApi.postComment(galleryId, text, challenge.challenge, nonce, captchaToken);
                } catch (AuthApi.ApiException e) {
                    boolean captcha = e.error != null && e.error.toLowerCase().contains("captcha");
                    callback.onFailure(e.error != null && !e.error.isEmpty()
                        ? e.error : ("HTTP " + e.httpCode), captcha);
                    return;
                }
                Comment comment = new Comment(new JsonReader(new StringReader(resp.toString())));
                callback.onSuccess(comment);
            } catch (IOException e) {
                callback.onFailure(e.getMessage() == null ? "Network error" : e.getMessage(), false);
            } catch (RuntimeException e) {
                callback.onFailure("Unexpected error: " + e.getMessage(), false);
            }
        });
    }
}
