package com.yosefario.nclientv3.api.source.ehentai;

import com.yosefario.nclientv3.api.comments.Comment;
import com.yosefario.nclientv3.api.comments.User;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;

public final class EHentaiComments {
    private static final Pattern POSTED = Pattern.compile("Posted on (.+?) by", Pattern.CASE_INSENSITIVE);

    private static final String[] POST_ERRORS = {
        "Your comment is too short",
        "Your comment is too long",
        "You have to wait",
        "Please stop spamming",
        "You are not allowed to comment",
    };

    private EHentaiComments() {
    }

    public static void post(int gid, String token, boolean exhentai, String text) throws IOException {
        if (!EHentaiAuth.isLoggedIn()) throw new IOException("e-hentai comments require login");
        if (token == null || token.isEmpty()) throw new IOException("e-hentai comment requires a token");
        String url = EHentaiUrls.galleryUrl(exhentai, gid, token);
        FormBody body = new FormBody.Builder().add("commenttext_new", text).build();
        String error = extractPostError(EHentaiHttp.string(EHentaiHttp.pacedPost(url, body)));
        if (error != null) throw new IOException(error);
    }

    private static String extractPostError(String html) {
        if (html == null) return null;
        Document doc = Jsoup.parse(html);
        Element form = doc.getElementById("formdiv");
        String scope = (form != null ? form.text() : doc.text()).toLowerCase(Locale.US);
        for (String phrase : POST_ERRORS) {
            if (scope.contains(phrase.toLowerCase(Locale.US))) return phrase + ".";
        }
        return null;
    }

    public static List<Comment> fetch(int gid, String token, boolean exhentai) throws IOException {
        if (token == null || token.isEmpty()) return Collections.emptyList();

        String url = EHentaiUrls.galleryUrl(exhentai, gid, token) + "?hc=1";
        return parse(EHentaiHttp.get(url));
    }

    public static List<Comment> parse(String html) {
        List<Comment> comments = new ArrayList<>();
        if (html == null) return comments;
        Document doc = Jsoup.parse(html);
        Element cdiv = doc.getElementById("cdiv");
        if (cdiv == null) return comments;
        for (Element c1 : cdiv.select("div.c1")) {
            Element c6 = c1.selectFirst("div.c6");
            if (c6 == null) continue;
            Element c3 = c1.selectFirst("div.c3");
            String username = "";
            Date date = new Date(0);
            if (c3 != null) {
                Element link = c3.selectFirst("a");
                if (link != null) username = link.text().trim();
                date = parseDate(c3.text());
            }
            for (Element br : c6.select("br")) br.replaceWith(new TextNode("\n"));
            String body = c6.wholeText().trim();
            int score = 0;
            Element scoreEl = c1.selectFirst("span[id^=comment_score_]");
            if (scoreEl != null) score = parseScore(scoreEl.text());
            comments.add(new Comment(parseId(c1.id()), new User(0, username, null), date, body, score));
        }
        return comments;
    }

    private static Date parseDate(String text) {
        Matcher m = POSTED.matcher(text);
        if (m.find()) {
            try {
                return new SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.US).parse(m.group(1).trim());
            } catch (ParseException ignore) {
            }
        }
        return new Date(0);
    }

    private static int parseScore(String text) {
        if (text == null) return 0;
        try {
            return Integer.parseInt(text.replace("+", "").trim());
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    private static int parseId(String elementId) {
        if (elementId == null) return 0;
        int underscore = elementId.lastIndexOf('_');
        if (underscore < 0) return 0;
        try {
            return Integer.parseInt(elementId.substring(underscore + 1));
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }
}
