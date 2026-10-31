package com.yosefario.nclientv3.api.source.ehentai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EHentaiGalleryPageParser {

    public static final class PageRef {
        public final String token;
        public final int gid;
        public final int page;

        @Nullable
        public final String thumbUrl;

        public PageRef(String token, int gid, int page, @Nullable String thumbUrl) {
            this.token = token;
            this.gid = gid;
            this.page = page;
            this.thumbUrl = thumbUrl;
        }
    }

    public static final class Result {
        public final List<PageRef> pages;
        public final int pageCount;
        public final String title;
        public final String titleJpn;
        public final EHentaiCategory category;
        public final List<String> tags;
        public final double rating;
        public final String cover;
        public final String uploader;
        public final long posted;
        public final int favoriteSlot;

        public Result(List<PageRef> pages, int pageCount, String title, String titleJpn,
                      EHentaiCategory category, List<String> tags, double rating,
                      String cover, String uploader, long posted, int favoriteSlot) {
            this.pages = pages;
            this.pageCount = pageCount;
            this.title = title;
            this.titleJpn = titleJpn;
            this.category = category;
            this.tags = tags;
            this.rating = rating;
            this.cover = cover;
            this.uploader = uploader;
            this.posted = posted;
            this.favoriteSlot = favoriteSlot;
        }
    }

    private static final Pattern PAGE_LINK = Pattern.compile("/s/([0-9a-f]+)/(\\d+)-(\\d+)");

    private static final Pattern SHOWING_OF = Pattern.compile("of\\s+([\\d,]+)");
    private static final Pattern RATING = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern STYLE_URL = Pattern.compile("url\\(([^)]+)\\)");
    private static final Pattern STYLE_POS = Pattern.compile("url\\([^)]+\\)\\s*(-?\\d+)(?:px)?\\s+(-?\\d+)(?:px)?");
    private static final Pattern STYLE_W = Pattern.compile("width\\s*:\\s*(\\d+)px");
    private static final Pattern STYLE_H = Pattern.compile("height\\s*:\\s*(\\d+)px");

    private EHentaiGalleryPageParser() {
    }

    @NonNull
    public static Result parse(String html) {
        if (html == null) html = "";
        Document doc = Jsoup.parse(html);
        List<PageRef> pages = extractPages(doc, html);
        String title = extractTitle(doc);
        String titleJpn = text(doc, "gj");
        EHentaiCategory category = extractCategory(doc);
        List<String> tags = extractTags(doc);
        double rating = extractRating(doc);
        int pageCount = extractPageCount(doc, pages.size());
        String cover = extractCover(doc);
        String uploader = extractUploader(doc);
        long posted = extractPosted(doc);
        int favoriteSlot = EHentaiFavorites.parseSlot(html);
        return new Result(pages, pageCount, title, titleJpn, category, tags, rating, cover, uploader, posted, favoriteSlot);
    }

    private static String text(Document doc, String id) {
        Element e = doc.getElementById(id);
        return e == null ? "" : e.text().trim();
    }

    private static String extractCover(Document doc) {
        Element gd1 = doc.getElementById("gd1");
        if (gd1 == null) return null;
        Element div = gd1.selectFirst("div[style]");
        if (div != null) {
            Matcher m = STYLE_URL.matcher(div.attr("style"));
            if (m.find()) return m.group(1).replaceAll("['\"]", "").trim();
        }
        Element img = gd1.selectFirst("img[src]");
        return img == null ? null : img.attr("src");
    }

    private static String extractUploader(Document doc) {
        Element gdn = doc.getElementById("gdn");
        if (gdn == null) return null;
        Element link = gdn.selectFirst("a");
        return link != null ? link.text().trim() : gdn.text().trim();
    }

    private static long extractPosted(Document doc) {
        Element gdd = doc.getElementById("gdd");
        if (gdd == null) return 0L;
        for (Element row : gdd.select("tr")) {
            Element key = row.selectFirst("td.gdt1");
            Element value = row.selectFirst("td.gdt2");
            if (key == null || value == null) continue;
            if (key.text().trim().startsWith("Posted")) {
                try {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .parse(value.text().trim()).getTime() / 1000L;
                } catch (ParseException ignore) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    public static String parseViewerImageUrl(String html) {
        if (html == null) return null;
        Element img = Jsoup.parse(html).getElementById("img");
        if (img != null) {
            String src = img.attr("src");
            if (!src.isEmpty()) return src;
        }
        return null;
    }

    private static List<PageRef> extractPages(Document doc, String html) {
        Map<Integer, PageRef> byPage = new LinkedHashMap<>();

        for (Element anchor : doc.select("#gdt a[href]")) {
            Matcher m = PAGE_LINK.matcher(anchor.attr("href"));
            if (!m.find()) continue;
            int page = Integer.parseInt(m.group(3));
            if (byPage.containsKey(page)) continue;
            byPage.put(page, new PageRef(m.group(1), Integer.parseInt(m.group(2)), page, parseThumb(anchor)));
        }

        if (byPage.isEmpty()) {
            Matcher m = PAGE_LINK.matcher(html);
            while (m.find()) {
                int page = Integer.parseInt(m.group(3));
                if (byPage.containsKey(page)) continue;
                byPage.put(page, new PageRef(m.group(1), Integer.parseInt(m.group(2)), page, null));
            }
        }
        List<PageRef> pages = new ArrayList<>(byPage.values());
        pages.sort(Comparator.comparingInt(p -> p.page));
        return pages;
    }

    @Nullable
    private static String parseThumb(Element anchor) {
        Element div = anchor.selectFirst("div[style]");
        if (div == null) return null;
        String style = div.attr("style");
        Matcher url = STYLE_URL.matcher(style);
        if (!url.find()) return null;
        String sprite = url.group(1).replaceAll("['\"]", "").trim();
        if (!sprite.startsWith("http")) return null;
        int x = 0, y = 0, w = 0, h = 0;
        Matcher pos = STYLE_POS.matcher(style);
        if (pos.find()) {
            x = -Integer.parseInt(pos.group(1));
            y = -Integer.parseInt(pos.group(2));
        }
        Matcher wm = STYLE_W.matcher(style);
        if (wm.find()) w = Integer.parseInt(wm.group(1));
        Matcher hm = STYLE_H.matcher(style);
        if (hm.find()) h = Integer.parseInt(hm.group(1));
        if (w <= 0 || h <= 0) return sprite;
        return sprite + "#ehcrop=" + x + "," + y + "," + w + "," + h;
    }

    private static String extractTitle(Document doc) {
        Element gn = doc.getElementById("gn");
        if (gn != null && !gn.text().trim().isEmpty()) return gn.text().trim();
        Element gj = doc.getElementById("gj");
        if (gj != null) return gj.text().trim();
        return "";
    }

    private static EHentaiCategory extractCategory(Document doc) {
        Element cat = doc.selectFirst("#gdc .cs");
        if (cat == null) cat = doc.selectFirst("div.cs");
        if (cat == null) return EHentaiCategory.UNKNOWN;
        return EHentaiCategory.fromLabel(cat.text().trim());
    }

    private static List<String> extractTags(Document doc) {
        List<String> tags = new ArrayList<>();
        Element taglist = doc.getElementById("taglist");
        if (taglist == null) return tags;
        for (Element row : taglist.select("tr")) {
            Element ns = row.selectFirst("td.tc");
            if (ns == null) continue;
            String namespace = ns.text().trim();
            if (namespace.endsWith(":")) namespace = namespace.substring(0, namespace.length() - 1);
            for (Element a : row.select("div.gt a, div.gtl a, div.gtw a")) {
                String value = a.text().trim();
                if (value.isEmpty()) continue;
                tags.add(namespace + ":" + value);
            }
        }
        return tags;
    }

    private static double extractRating(Document doc) {
        Element label = doc.getElementById("rating_label");
        if (label != null) {
            Matcher m = RATING.matcher(label.text());
            if (m.find()) {
                try {
                    return Double.parseDouble(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0.0;
    }

    private static int extractPageCount(Document doc, int fallback) {
        Element gpc = doc.selectFirst("p.gpc");
        if (gpc != null) {
            Matcher m = SHOWING_OF.matcher(gpc.text());
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1).replace(",", ""));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return fallback;
    }
}
