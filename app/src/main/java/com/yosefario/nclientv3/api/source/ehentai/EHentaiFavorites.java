package com.yosefario.nclientv3.api.source.ehentai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import okhttp3.FormBody;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EHentaiFavorites {
    public static final int ALL = -1;
    public static final int NONE = -1;
    public static final int SLOT_COUNT = 10;

    private static final String PREF = "Settings";
    private static final String KEY_NAMES = "eh_favcat_names";
    private static final String KEY_COUNTS = "eh_favcat_counts";
    private static final String KEY_NAMES_AT = "eh_favcat_names_at";
    private static final String KEY_VIEW = "eh_favcat_view";
    private static final String KEY_ADD = "eh_favcat_add";
    private static final long NAMES_TTL_MS = 24L * 3600 * 1000;
    private static final Pattern RGB = Pattern.compile("background-color:rgba\\((\\d+),(\\d+),(\\d+),");
    private static final String[][] SLOT_COLORS = {
        {"0", "0", "0"}, {"240", "0", "0"}, {"240", "160", "0"}, {"208", "208", "0"}, {"0", "128", "0"},
        {"144", "240", "64"}, {"64", "176", "240"}, {"0", "0", "240"}, {"80", "0", "128"}, {"224", "128", "224"},
    };

    private static SharedPreferences prefs;

    private EHentaiFavorites() {
    }

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String[] names() {
        String[] out = new String[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) out[i] = defaultName(i);
        if (prefs == null) return out;
        try {
            JSONArray stored = new JSONArray(prefs.getString(KEY_NAMES, "[]"));
            for (int i = 0; i < stored.length() && i < SLOT_COUNT; i++) {
                String name = stored.optString(i);
                if (!name.isEmpty()) out[i] = name;
            }
        } catch (Exception ignore) {
        }
        return out;
    }

    @Nullable
    public static int[] counts() {
        if (prefs == null) return null;
        try {
            JSONArray stored = new JSONArray(prefs.getString(KEY_COUNTS, "[]"));
            if (stored.length() < SLOT_COUNT) return null;
            int[] out = new int[SLOT_COUNT];
            for (int i = 0; i < SLOT_COUNT; i++) out[i] = stored.optInt(i, 0);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean namesFresh() {
        return prefs != null && System.currentTimeMillis() - prefs.getLong(KEY_NAMES_AT, 0) < NAMES_TTL_MS;
    }

    public static int viewSlot() {
        return prefs == null ? ALL : prefs.getInt(KEY_VIEW, ALL);
    }

    public static void setViewSlot(int slot) {
        if (prefs != null) prefs.edit().putInt(KEY_VIEW, slot).apply();
    }

    public static int addSlot() {
        if (prefs == null) return 0;
        return Math.max(0, Math.min(SLOT_COUNT - 1, prefs.getInt(KEY_ADD, 0)));
    }

    public static void setAddSlot(int slot) {
        if (prefs != null) prefs.edit().putInt(KEY_ADD, slot).apply();
    }

    public static void remember(String html) {
        if (prefs == null || html == null || !html.contains("class=\"ido\"")) return;
        Document doc = Jsoup.parse(html);
        Elements fps = doc.select(".ido .fp");
        if (fps.size() < SLOT_COUNT) return;
        JSONArray names = new JSONArray();
        JSONArray counts = new JSONArray();
        for (int i = 0; i < SLOT_COUNT; i++) {
            Element fp = fps.get(i);
            counts.put(fp.children().size() > 0 ? parseCount(fp.child(0).text()) : 0);
            names.put(fp.children().size() > 2 ? fp.child(2).text().trim() : defaultName(i));
        }
        prefs.edit()
            .putString(KEY_NAMES, names.toString())
            .putString(KEY_COUNTS, counts.toString())
            .putLong(KEY_NAMES_AT, System.currentTimeMillis())
            .apply();
    }

    static String defaultName(int slot) {
        return "Favorites " + slot;
    }

    public static int parseSlot(String html) {
        if (html == null) return NONE;
        Document doc = Jsoup.parse(html);
        Element link = doc.getElementById("favoritelink");
        if (link == null || link.text().trim().equalsIgnoreCase("Add to Favorites")) return NONE;
        Element fav = doc.getElementById("fav");
        if (fav != null) {
            Matcher m = RGB.matcher(fav.attr("style"));
            if (m.find()) {
                for (int i = 0; i < SLOT_COLORS.length; i++) {
                    String[] c = SLOT_COLORS[i];
                    if (c[0].equals(m.group(1)) && c[1].equals(m.group(2)) && c[2].equals(m.group(3))) return i;
                }
            }
        }
        return 0;
    }

    public static void refreshNames(boolean exhentai) {
        try {
            remember(EHentaiHttp.get(EHentaiUrls.favoritesUrl(exhentai, ALL, null)));
        } catch (IOException ignore) {
        }
    }

    static void addOrUpdate(boolean exhentai, int gid, String token, int slot, String note) throws IOException {
        if (token == null || token.isEmpty()) throw new IOException("e-hentai favorite requires a token");
        if (slot != NONE && (slot < 0 || slot >= SLOT_COUNT)) throw new IOException("Invalid favorites category " + slot);
        FormBody body = new FormBody.Builder()
            .add("favcat", slot == NONE ? "favdel" : String.valueOf(slot))
            .add("favnote", note == null ? "" : note)
            .add("apply", "Apply Changes")
            .add("update", "1")
            .build();
        EHentaiHttp.string(EHentaiHttp.pacedPost(EHentaiUrls.favoritePopupUrl(exhentai, gid, token), body));
        setAddSlot(Math.max(0, slot));
    }

    public static int count(int slot) {
        int[] counts = counts();
        if (counts == null) return -1;
        if (slot != ALL) return slot >= 0 && slot < SLOT_COUNT ? counts[slot] : -1;
        int sum = 0;
        for (int c : counts) sum += c;
        return sum;
    }

    public static String label(int slot) {
        String name = slot == ALL ? "All" : names()[slot];
        int count = count(slot);
        return count < 0 ? name : String.format(Locale.US, "%s (%d)", name, count);
    }

    private static int parseCount(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
