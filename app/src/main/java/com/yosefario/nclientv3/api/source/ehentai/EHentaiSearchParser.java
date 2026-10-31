package com.yosefario.nclientv3.api.source.ehentai;

import com.yosefario.nclientv3.utility.LogUtility;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import androidx.annotation.Nullable;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EHentaiSearchParser {


    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*['\"]?(.*?)['\"]?\\s*\\)");


    private static final Pattern NEXT_PARAM = Pattern.compile("[?&]next=([^&]+)");

    private EHentaiSearchParser() {
    }

    public static List<EHentaiGallery> parse(String html) {
        List<EHentaiGallery> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        if (html == null || html.isEmpty()) return result;
        Document doc = Jsoup.parse(html);

        Elements links = doc.select("a[href*=/g/]");
        for (Element link : links) {
            Matcher m = EHentaiUrls.GALLERY_PATH.matcher(link.attr("href"));
            if (!m.find()) continue;

            int gid;
            try {
                gid = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (!seen.add(gid)) continue;
            String token = m.group(2);

            Element card = cardOf(link);

            EHentaiGallery gallery = new EHentaiGallery();
            gallery.gid = gid;
            gallery.token = token;
            gallery.title = extractTitle(link, card);
            gallery.thumb = extractThumb(card);
            gallery.category = extractCategory(card);
            result.add(gallery);
        }

        LogUtility.d("EHentaiSearchParser: parsed " + result.size() + " galleries");
        return result;
    }

    @Nullable
    public static String nextCursor(String html) {
        if (html == null) return null;
        Document doc = Jsoup.parse(html);
        Element next = doc.getElementById("unext");
        if (next == null) return null;
        String href = next.attr("href");
        Matcher m = NEXT_PARAM.matcher(href);
        return m.find() ? m.group(1) : href;
    }

    private static Element cardOf(Element link) {
        Element row = link.closest("tr");
        if (row != null) return row;
        Element cell = link.closest("div.gl1t, div.id1, td");
        return cell != null ? cell : link;
    }

    private static String extractTitle(Element link, Element card) {
        Element name = card.selectFirst("div.glink, .glname .glink, .glink");
        if (name != null && !name.text().isEmpty()) return name.text();
        String linkText = link.text();
        if (!linkText.isEmpty()) return linkText;
        Element img = card.selectFirst("img[title]");
        if (img != null) return img.attr("title");
        return null;
    }

    private static String extractThumb(Element card) {
        Element img = card.selectFirst("img");
        if (img != null) {

            String dataSrc = img.attr("data-src");
            if (!dataSrc.isEmpty()) return dataSrc;
            String src = img.attr("src");
            if (!src.isEmpty() && !src.startsWith("data:")) return src;
        }

        for (Element el : card.select("[style*=url(]")) {
            Matcher m = CSS_URL.matcher(el.attr("style"));
            if (m.find() && !m.group(1).isEmpty()) return m.group(1);
        }
        return null;
    }

    private static EHentaiCategory extractCategory(Element card) {

        Element chip = card.selectFirst("div.cn, .gl1c div, .cs");
        if (chip != null) {
            EHentaiCategory c = EHentaiCategory.fromLabel(chip.text());
            if (c != EHentaiCategory.UNKNOWN) return c;
        }

        Element gl1c = card.selectFirst(".gl1c, .cn, .cs");
        if (gl1c != null) {
            EHentaiCategory c = categoryFromClass(gl1c.className());
            if (c != EHentaiCategory.UNKNOWN) return c;
        }
        return EHentaiCategory.UNKNOWN;
    }

    private static EHentaiCategory categoryFromClass(String className) {
        if (className == null) return EHentaiCategory.UNKNOWN;
        for (String token : className.split("\\s+")) {
            switch (token) {
                case "ct1": return EHentaiCategory.MISC;
                case "ct2": return EHentaiCategory.DOUJINSHI;
                case "ct3": return EHentaiCategory.MANGA;
                case "ct4": return EHentaiCategory.ARTIST_CG;
                case "ct5": return EHentaiCategory.GAME_CG;
                case "ct6": return EHentaiCategory.IMAGE_SET;
                case "ct7": return EHentaiCategory.COSPLAY;
                case "ct8": return EHentaiCategory.ASIAN_PORN;
                case "ct9": return EHentaiCategory.NON_H;
                case "cta": return EHentaiCategory.WESTERN;
                default: break;
            }
        }
        return EHentaiCategory.UNKNOWN;
    }
}
