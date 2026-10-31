package com.yosefario.nclientv3;

import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;

import com.google.android.material.appbar.MaterialToolbar;
import com.yosefario.nclientv3.adapters.CommentAdapter;
import com.yosefario.nclientv3.api.comments.Comment;
import com.yosefario.nclientv3.api.comments.CommentPoster;
import com.yosefario.nclientv3.api.comments.CommentsFetcher;
import com.yosefario.nclientv3.api.source.SourceId;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiAuth;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiComments;
import com.yosefario.nclientv3.components.activities.BaseActivity;
import com.yosefario.nclientv3.components.views.TurnstileDialog;
import com.yosefario.nclientv3.loginapi.AuthApi;
import com.yosefario.nclientv3.settings.Login;
import com.yosefario.nclientv3.utility.LogUtility;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class CommentActivity extends BaseActivity {
    private static final int MINIUM_MESSAGE_LENGHT = 10;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private CommentAdapter adapter;
    private int id;
    private SourceId source = SourceId.NHENTAI;
    private String token;
    private EditText commentText;
    private View sendButton;
    private volatile String captchaSiteKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comment);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setTitle(R.string.comments);
        findViewById(R.id.page_switcher).setVisibility(View.GONE);
        id = getIntent().getIntExtra(getPackageName() + ".GALLERYID", -1);
        source = SourceId.values()[getIntent().getIntExtra(getPackageName() + ".SOURCE", 0)];
        token = getIntent().getStringExtra(getPackageName() + ".TOKEN");
        if (id == -1) {
            finish();
            return;
        }
        recycler = findViewById(R.id.recycler);
        refresher = findViewById(R.id.refresher);
        refresher.setOnRefreshListener(() -> new CommentsFetcher(CommentActivity.this, id, source, token).start());
        commentText = findViewById(R.id.commentText);
        sendButton = findViewById(R.id.sendButton);
        findViewById(R.id.card).setVisibility(canComment() ? View.VISIBLE : View.GONE);
        sendButton.setOnClickListener(v -> onSendClicked());
        if (source == SourceId.NHENTAI) io.execute(this::loadCaptchaInfo);
        changeLayout(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        recycler.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        refresher.setRefreshing(true);
        new CommentsFetcher(CommentActivity.this, id, source, token).start();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void loadCaptchaInfo() {
        try {
            captchaSiteKey = AuthApi.fetchCaptchaInfo().siteKey;
        } catch (IOException e) {
            LogUtility.e("CommentActivity: failed to fetch captcha info", e);
        }
    }

    private boolean isEHentai() {
        return source == SourceId.EHENTAI || source == SourceId.EXHENTAI;
    }

    private boolean canComment() {
        return isEHentai() ? EHentaiAuth.isLoggedIn() : (Login.canComment() && source == SourceId.NHENTAI);
    }

    private void onSendClicked() {
        String text = commentText.getText() == null ? "" : commentText.getText().toString().trim();
        if (text.length() < MINIUM_MESSAGE_LENGHT) {
            Toast.makeText(this, getString(R.string.minimum_comment_length, MINIUM_MESSAGE_LENGHT),
                Toast.LENGTH_SHORT).show();
            return;
        }
        if (isEHentai()) postEHentaiComment(text);
        else requestCaptcha(token -> postComment(text, token));
    }

    private void postEHentaiComment(@NonNull String text) {
        sendButton.setEnabled(false);
        io.execute(() -> {
            try {
                EHentaiComments.post(id, token, source == SourceId.EXHENTAI, text);
                runOnUiThread(() -> {
                    onPosted();
                    refresher.setRefreshing(true);
                    new CommentsFetcher(CommentActivity.this, id, source, token).start();
                });
            } catch (IOException e) {
                String message = e.getMessage() == null ? "Network error" : e.getMessage();
                runOnUiThread(() -> onPostFailed(message));
            }
        });
    }

    private void onPosted() {
        commentText.setText("");
        sendButton.setEnabled(true);
        Toast.makeText(this, R.string.comment_posted, Toast.LENGTH_SHORT).show();
    }

    private void onPostFailed(String message) {
        sendButton.setEnabled(true);
        Toast.makeText(this, getString(R.string.comment_post_failed, message), Toast.LENGTH_LONG).show();
    }

    private void requestCaptcha(@NonNull Consumer<String> onToken) {
        String siteKey = captchaSiteKey;
        if (TextUtils.isEmpty(siteKey)) {
            Toast.makeText(this, R.string.login_status_captcha_loading, Toast.LENGTH_SHORT).show();
            io.execute(() -> {
                loadCaptchaInfo();
                runOnUiThread(() -> {
                    if (!TextUtils.isEmpty(captchaSiteKey)) requestCaptcha(onToken);
                });
            });
            return;
        }
        sendButton.setEnabled(false);
        new TurnstileDialog(this, siteKey, new TurnstileDialog.Callback() {
            @Override
            public void onToken(@NonNull String token) {
                onToken.accept(token);
            }

            @Override
            public void onCancelled() {
                sendButton.setEnabled(true);
            }
        }).show(false);
    }

    private void postComment(@NonNull String text, @NonNull String captchaToken) {
        sendButton.setEnabled(false);
        CommentPoster.post(id, text, captchaToken, new CommentPoster.Callback() {
            @Override
            public void onProgress(@NonNull String message) {}

            @Override
            public void onSuccess(@NonNull Comment comment) {
                runOnUiThread(() -> {
                    if (adapter != null) {
                        adapter.addComment(comment);
                        recycler.smoothScrollToPosition(0);
                    }
                    onPosted();
                });
            }

            @Override
            public void onFailure(@NonNull String message, boolean captchaRejected) {
                runOnUiThread(() -> onPostFailed(message));
            }
        });
    }

    public void setAdapter(CommentAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    protected int getPortraitColumnCount() {
        return 1;
    }

    @Override
    protected int getLandscapeColumnCount() {
        return 2;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
