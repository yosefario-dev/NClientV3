package com.yosefario.nclientv3.api.source.ehentai;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.api.source.GalleryKey;
import com.yosefario.nclientv3.api.source.SourceId;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EHentaiUrls {
    public static final String API_URL = "https://api.e-hentai.org/api.php";

    private static final String HOST_EX = "https://exhentai.org/";
    private static final String HOST_EH = "https://e-hentai.org/";

    private EHentaiUrls() {
    }

    static final Pattern GALLERY_PATH = Pattern.compile("/g/(\\d+)/([0-9a-f]{10})");

    @Nullable
    public static GalleryKey parseGalleryLink(@Nullable String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("https?://(?:[\\w-]+\\.)?(e-hentai|exhentai)\\.org" + GALLERY_PATH.pattern(),
            Pattern.CASE_INSENSITIVE).matcher(text);
        if (!m.find()) return null;
        try {
            SourceId source = m.group(1).equalsIgnoreCase("exhentai") ? SourceId.EXHENTAI : SourceId.EHENTAI;
            return GalleryKey.of(source, Integer.parseInt(m.group(2)), m.group(3).toLowerCase(Locale.US));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String galleryLink(GalleryKey key) {
        return galleryUrl(key.source() == SourceId.EXHENTAI, key.id(), key.token());
    }

    static boolean isEHentaiHost(String host) {
        return matches(host, "e-hentai.org") || matches(host, "exhentai.org");
    }

    static boolean isImageHost(String host) {
        return host.endsWith("hath.network") || matches(host, "ehgt.org");
    }

    private static boolean matches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    static String host(boolean exhentai) {
        return exhentai ? HOST_EX : HOST_EH;
    }

    static String galleryUrl(boolean exhentai, int gid, String token) {
        return host(exhentai) + "g/" + gid + "/" + token + "/";
    }

    static String searchUrl(boolean exhentai, String query, int fCats, @Nullable Advanced adv) {
        Uri.Builder b = Uri.parse(host(exhentai)).buildUpon();
        b.appendQueryParameter("f_search", query == null ? "" : query);
        if (fCats != 0) {
            b.appendQueryParameter("f_cats", Integer.toString(fCats));
        }
        if (adv != null) {
            b.appendQueryParameter("advsearch", "1");
            if (adv.minRating > 0) {
                b.appendQueryParameter("f_srdd", Integer.toString(adv.minRating));
            }
            if (adv.minPages > 0) {
                b.appendQueryParameter("f_spf", Integer.toString(adv.minPages));
            }
            if (adv.maxPages > 0) {
                b.appendQueryParameter("f_spt", Integer.toString(adv.maxPages));
            }
            if (adv.showExpunged) {
                b.appendQueryParameter("f_sh", "1");
            }
            if (adv.requireTorrent) {
                b.appendQueryParameter("f_sto", "1");
            }
            if (adv.disableDefaultFilters) {
                b.appendQueryParameter("f_sfl", "1");
                b.appendQueryParameter("f_sfu", "1");
                b.appendQueryParameter("f_sft", "1");
            }
        }
        return b.build().toString();
    }

    static String thumbnailNormalize(String url) {
        if (url != null && url.startsWith("//")) {
            return "https:" + url;
        }
        return url;
    }

    static String galleryPageUrl(boolean exhentai, int gid, String token, int p) {
        String base = galleryUrl(exhentai, gid, token);
        return p <= 0 ? base : base + "?p=" + p;
    }

    static String viewerUrl(boolean exhentai, String pageToken, int gid, int page) {
        return host(exhentai) + "s/" + pageToken + "/" + gid + "-" + page;
    }

    static String favoritesUrl(boolean exhentai, int favcat, @Nullable String query) {
        String base = host(exhentai) + "favorites.php?favcat=" + (favcat == EHentaiFavorites.ALL ? "all" : Integer.toString(favcat));
        if (query != null && !query.isEmpty()) base += "&f_search=" + Uri.encode(query);
        return base;
    }

    static String favoritePopupUrl(boolean exhentai, int gid, String token) {
        return host(exhentai) + "gallerypopups.php?gid=" + gid + "&t=" + token + "&act=addfav";
    }

    public static final class Advanced {
        public int minRating;
        public int minPages;
        public int maxPages;
        public boolean showExpunged;
        public boolean requireTorrent;
        public boolean disableDefaultFilters;
    }
}
