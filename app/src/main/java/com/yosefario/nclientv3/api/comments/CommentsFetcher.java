package com.yosefario.nclientv3.api.comments;

import android.util.JsonReader;
import android.util.JsonToken;

import com.yosefario.nclientv3.CommentActivity;
import com.yosefario.nclientv3.adapters.CommentAdapter;
import com.yosefario.nclientv3.api.source.SourceId;
import com.yosefario.nclientv3.api.source.ehentai.EHentaiComments;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.utility.Utility;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class CommentsFetcher extends Thread {
    private static final String COMMENT_API_URL = Utility.getBaseUrl() + "api/v2/galleries/%d/comments";
    private final int id;
    private final CommentActivity commentActivity;
    private final List<Comment> comments = new ArrayList<>();
    private final SourceId source;
    private final String token;

    public CommentsFetcher(CommentActivity commentActivity, int id) {
        this(commentActivity, id, SourceId.NHENTAI, null);
    }

    public CommentsFetcher(CommentActivity commentActivity, int id, SourceId source, String token) {
        this.id = id;
        this.commentActivity = commentActivity;
        this.source = source == null ? SourceId.NHENTAI : source;
        this.token = token;
    }

    @Override
    public void run() {
        populateComments();
        postResult();
    }

    private void postResult() {
        CommentAdapter commentAdapter = new CommentAdapter(commentActivity, comments);
        commentActivity.setAdapter(commentAdapter);
        commentActivity.runOnUiThread(() -> {
            commentActivity.getRecycler().setAdapter(commentAdapter);
            commentActivity.getRefresher().setRefreshing(false);
        });
    }

    private void populateComments() {
        if (source == SourceId.EHENTAI || source == SourceId.EXHENTAI) {
            try {
                comments.addAll(EHentaiComments.fetch(id, token, source == SourceId.EXHENTAI));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        String url = String.format(Locale.US, COMMENT_API_URL, id);
        try {
            Response response = Global.getClient().newCall(new Request.Builder().url(url).build()).execute();
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                return;
            }
            JsonReader reader = new JsonReader(new InputStreamReader(body.byteStream()));
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    if ("result".equals(reader.nextName()) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                        reader.beginArray();
                        while (reader.hasNext())
                            comments.add(new Comment(reader));
                        reader.endArray();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                reader.beginArray();
                while (reader.hasNext())
                    comments.add(new Comment(reader));
                reader.endArray();
            }
            reader.close();
            response.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
