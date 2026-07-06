package com.yosefario.nclientv3;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShareReceiverActivity extends Activity {

    private static final Pattern GALLERY = Pattern.compile("/g/(\\d+)");
    private static final Pattern NH_URL = Pattern.compile("https?://\\S*nhentai\\.net/\\S*", Pattern.CASE_INSENSITIVE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        route(extractSharedText(getIntent()));
        finish();
    }

    private String extractSharedText(Intent intent) {
        if (intent == null) return null;
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text == null) text = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);
        return text == null ? null : text.toString().trim();
    }

    private void route(String shared) {
        if (TextUtils.isEmpty(shared)) {
            Toast.makeText(this, R.string.nothing_shared, Toast.LENGTH_SHORT).show();
            return;
        }
        Matcher gallery = GALLERY.matcher(shared);
        Matcher url = NH_URL.matcher(shared);
        if (gallery.find()) {
            openGallery(gallery.group(1));
        } else if (url.find()) {
            openUrl(url.group());
        } else if (shared.matches("\\d{1,7}")) {
            openGallery(shared);
        } else {
            openSearch(shared);
        }
    }

    private void openGallery(String id) {
        startForwarded(mainIntent().putExtra(getPackageName() + ".SEARCHMODE", true)
            .putExtra(getPackageName() + ".QUERY", id));
    }

    private void openSearch(String query) {
        startForwarded(mainIntent().putExtra(getPackageName() + ".SEARCHMODE", true)
            .putExtra(getPackageName() + ".QUERY", query));
    }

    private void openUrl(String url) {
        startForwarded(mainIntent().setData(Uri.parse(url)));
    }

    private Intent mainIntent() {
        return new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private void startForwarded(Intent intent) {
        startActivity(intent);
    }
}
