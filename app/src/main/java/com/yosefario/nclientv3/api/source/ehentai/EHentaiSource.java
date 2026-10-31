package com.yosefario.nclientv3.api.source.ehentai;

import android.content.Context;

import androidx.annotation.Nullable;

import com.yosefario.nclientv3.api.SimpleGallery;
import com.yosefario.nclientv3.api.components.Ranges;
import com.yosefario.nclientv3.api.components.Tag;
import com.yosefario.nclientv3.api.enums.Language;
import com.yosefario.nclientv3.api.enums.TagStatus;
import com.yosefario.nclientv3.api.enums.TagType;
import com.yosefario.nclientv3.api.source.GalleryKey;
import com.yosefario.nclientv3.api.source.GalleryPayload;
import com.yosefario.nclientv3.api.source.ListPayload;
import com.yosefario.nclientv3.api.source.SearchRequest;
import com.yosefario.nclientv3.api.source.Source;
import com.yosefario.nclientv3.api.source.SourceCapabilities;
import com.yosefario.nclientv3.api.source.SourceId;
import com.yosefario.nclientv3.utility.LogUtility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EHentaiSource implements Source {
    private final SourceCapabilities capabilities = SourceCapabilities.EHENTAI;
    private final boolean exhentai;

    public EHentaiSource() {
        this(false);
    }

    public EHentaiSource(boolean exhentai) {
        this.exhentai = exhentai;
    }

    @Override
    public SourceId id() {
        return exhentai ? SourceId.EXHENTAI : SourceId.EHENTAI;
    }

    @Override
    public String displayName() {
        return id().displayName();
    }

    @Override
    public String baseUrl() {
        return EHentaiUrls.host(exhentai);
    }

    @Override
    public SourceCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public ListPayload fetchGalleries(Context context, SearchRequest request) throws IOException {
        int page = Math.max(0, request.page() - 1);
        String query = buildSearchQuery(request);
        int fCats = categoryFilter(request);
        EHentaiUrls.Advanced advanced = pageRangeFilter(request);
        return fetchList(context, EHentaiUrls.searchUrl(exhentai, query, fCats, advanced), request.page(), page);
    }

    private ListPayload fetchList(Context context, String url, int requestPage, int ehPage) throws IOException {
        String html = EHentaiPager.htmlFor(url, ehPage);
        if (html == null) return new ListPayload(new ArrayList<>(), requestPage, 0, -1);
        EHentaiFavorites.remember(html);
        List<EHentaiGallery> parsed = EHentaiSearchParser.parse(html);
        List<SimpleGallery> galleries = new ArrayList<>(parsed.size());
        for (EHentaiGallery gallery : parsed) {
            if (gallery.gid <= 0 || gallery.token == null) continue;
            gallery.thumb = EHentaiUrls.thumbnailNormalize(gallery.thumb);
            SimpleGallery simple = EHentaiGalleryMapper.toSimpleGallery(context, gallery);
            simple.setSource(id());
            galleries.add(simple);
        }
        LogUtility.d("EHentaiSource: " + url + " -> " + galleries.size() + " galleries");
        int count = galleries.size();
        return new ListPayload(galleries, pageCount(url, requestPage, count), count, -1);
    }

    private static int pageCount(String baseUrl, int requestPage, int parsedCount) {
        if (parsedCount == 0) return Math.max(requestPage, 1);
        return EHentaiPager.hasNext(baseUrl, requestPage) ? requestPage + 1 : requestPage;
    }

    @Override
    public GalleryPayload fetchGallery(Context context, GalleryKey key) throws IOException {
        int gid = key.id();
        String token = key.token();
        if (token == null || token.isEmpty())
            throw new IOException("e-hentai gallery requires a token");

        String html = EHentaiHttp.get(EHentaiUrls.galleryPageUrl(exhentai, gid, token, 0));
        EHentaiGalleryPageParser.Result res = EHentaiGalleryPageParser.parse(html);
        EHentaiPageResolver.seed(exhentai, gid, 0, res);

        EHentaiGallery meta = new EHentaiGallery();
        meta.gid = gid;
        meta.token = token;
        meta.title = res.title;
        meta.titleJpn = res.titleJpn;
        meta.category = res.category;
        meta.thumb = res.cover;
        meta.uploader = res.uploader;
        meta.posted = res.posted;
        meta.rating = res.rating;
        meta.filecount = res.pageCount;
        meta.tags.addAll(res.tags);

        int perPage = res.pages.size();
        if (perPage == 0)
            throw new IOException("e-hentai returned no pages for " + gid + " (throttled or unavailable)");
        int total = Math.max(res.pageCount, perPage);
        LogUtility.d("EHentaiSource: gallery " + gid + " has " + total + " pages (" + perPage + " eager)");

        return new GalleryPayload(gid, buildLegacyJson(gid, token, meta, res.pages, perPage, total), new ArrayList<>(), res.favoriteSlot);
    }

    @Override
    public boolean setOnlineFavorite(Context context, GalleryKey key, boolean favorite) throws IOException {
        return setOnlineFavoriteSlot(context, key, favorite ? EHentaiFavorites.addSlot() : EHentaiFavorites.NONE) >= 0;
    }

    public int setOnlineFavoriteSlot(Context context, GalleryKey key, int slot) throws IOException {
        if (!EHentaiAuth.isLoggedIn())
            throw new IOException("e-hentai favorites require login");
        EHentaiFavorites.addOrUpdate(exhentai, key.id(), key.token(), slot, "");
        return slot;
    }

    @Override
    public boolean isLoggedIn() {
        return EHentaiAuth.isLoggedIn();
    }

    private String buildLegacyJson(int gid, String token, EHentaiGallery meta,
                                   List<EHentaiGalleryPageParser.PageRef> eagerPages,
                                   int perPage, int total) throws IOException {
        try {
            JSONObject root = new JSONObject();
            root.put("id", gid);
            root.put("media_id", 0);
            root.put("source", id().name());
            root.put("token", token);
            String english = meta.title != null ? meta.title : "";
            String japanese = meta.titleJpn != null ? meta.titleJpn : "";
            JSONObject title = new JSONObject();
            title.put("english", english);
            title.put("japanese", japanese);
            title.put("pretty", english);
            root.put("title", title);
            root.put("upload_date", meta.posted);
            root.put("num_pages", total);
            root.put("num_favorites", 0);
            root.put("rating", meta.rating);
            if (meta.uploader != null) root.put("uploader", meta.uploader);
            root.put("tags", buildTags(meta));
            JSONObject images = new JSONObject();
            String cover = meta.thumb;
            images.put("cover", imageJson(cover, cover));
            images.put("thumbnail", imageJson(cover, cover));
            Map<Integer, EHentaiGalleryPageParser.PageRef> byPage = new HashMap<>();
            for (EHentaiGalleryPageParser.PageRef ref : eagerPages) byPage.put(ref.page, ref);
            JSONArray pages = new JSONArray();
            for (int page = 1; page <= total; page++) {
                EHentaiGalleryPageParser.PageRef ref = byPage.get(page);
                String viewer, thumb;
                if (ref != null) {
                    viewer = EHentaiUrls.viewerUrl(exhentai, ref.token, ref.gid, ref.page);

                    thumb = ref.thumbUrl != null ? ref.thumbUrl : cover;
                } else {
                    viewer = EHentaiPageResolver.fullMarker(exhentai, gid, token, page, perPage);
                    thumb = EHentaiPageResolver.thumbMarker(exhentai, gid, token, page, perPage);
                }
                pages.put(imageJson(viewer, thumb));
            }
            images.put("pages", pages);
            root.put("images", images);
            return root.toString();
        } catch (JSONException e) {
            throw new IOException("Failed to build e-hentai gallery JSON", e);
        }
    }

    private JSONArray buildTags(@Nullable EHentaiGallery meta) throws JSONException {
        JSONArray array = new JSONArray();
        if (meta == null) return array;
        if (meta.category != null && meta.category != EHentaiCategory.UNKNOWN) {
            String label = meta.category.label().toLowerCase(Locale.US);
            JSONObject cat = new JSONObject();
            cat.put("count", 0);
            cat.put("type", "category");
            cat.put("id", synthTagId("category:" + label));
            cat.put("name", label);
            array.put(cat);
        }
        for (String raw : meta.tags) {
            if (raw == null || raw.isEmpty()) continue;
            int colon = raw.indexOf(':');
            String namespace = colon > 0 ? raw.substring(0, colon) : "";
            String value = colon >= 0 ? raw.substring(colon + 1) : raw;
            if (value.isEmpty()) continue;
            JSONObject tag = new JSONObject();
            tag.put("count", 0);
            tag.put("type", mapNamespace(namespace));
            tag.put("id", synthTagId(raw));
            tag.put("name", value);
            array.put(tag);
        }
        return array;
    }

    private static String mapNamespace(String namespace) {
        switch (namespace) {
            case "artist":
                return "artist";
            case "group":
                return "group";
            case "parody":
                return "parody";
            case "character":
                return "character";
            case "language":
                return "language";
            default:
                return "tag";
        }
    }

    private static int synthTagId(String key) {
        return -((key.hashCode() & 0x3fffffff) + 2);
    }

    private JSONObject imageJson(@Nullable String path, @Nullable String thumbnail) throws JSONException {
        JSONObject image = new JSONObject();
        image.put("t", String.valueOf(extChar(path)));
        image.put("w", 0);
        image.put("h", 0);
        if (path != null && !path.isEmpty()) image.put("path", path);
        if (thumbnail != null && !thumbnail.isEmpty()) image.put("thumbnail", thumbnail);
        return image;
    }

    private char extChar(@Nullable String url) {
        if (url == null) return 'j';
        String lower = url.toLowerCase(Locale.US);
        if (lower.endsWith(".png")) return 'p';
        if (lower.endsWith(".gif")) return 'g';
        if (lower.endsWith(".webp")) return 'w';
        return 'j';
    }

    @Override
    public GalleryPayload fetchRandomGallery(Context context) throws IOException {
        throw new IOException("e-hentai random is not implemented yet");
    }

    @Override
    public GalleryPayload fetchRandomFavorite(Context context) throws IOException {
        throw new IOException("e-hentai random favorite is not implemented yet");
    }

    @Override
    public ListPayload fetchFavorites(Context context, SearchRequest request) throws IOException {
        if (!EHentaiAuth.isLoggedIn())
            throw new IOException("e-hentai favorites require login");
        int page = Math.max(0, request.page() - 1);
        int slot = EHentaiFavorites.viewSlot();
        ListPayload list = fetchList(context, EHentaiUrls.favoritesUrl(exhentai, slot, request.query()), request.page(), page);
        boolean filtered = request.query() != null && !request.query().isEmpty();
        return new ListPayload(list.getGalleries(), list.getPageCount(), list.getPerPage(), filtered ? -1 : EHentaiFavorites.count(slot));
    }

    @Override
    public String buildSearchQuery(SearchRequest request) {
        StringBuilder builder = new StringBuilder();
        appendTerm(builder, request.query());
        for (Tag tag : request.tags()) {
            String term = ehTagTerm(tag);
            if (term != null && builder.indexOf(term) < 0) appendTerm(builder, term);
        }
        return builder.toString().trim();
    }

    private static void appendTerm(StringBuilder builder, String term) {
        if (term == null) return;
        term = term.trim();
        if (term.isEmpty()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(term);
    }

    private static String ehTagTerm(Tag tag) {
        if (tag == null) return null;
        String namespace = ehNamespace(tag.getType());
        if (namespace == null) return null;
        String value = tag.getName();
        if (value == null || value.trim().isEmpty()) return null;
        String core = (namespace.isEmpty() ? "" : namespace + ":") + "\"" + value.trim() + "$\"";
        return tag.getStatus() == TagStatus.AVOIDED ? "-" + core : core;
    }

    private static String ehNamespace(TagType type) {
        if (type == null) return null;
        if (type.equals(TagType.PARODY)) return "parody";
        if (type.equals(TagType.CHARACTER)) return "character";
        if (type.equals(TagType.ARTIST)) return "artist";
        if (type.equals(TagType.GROUP)) return "group";
        if (type.equals(TagType.LANGUAGE)) return "language";
        if (type.equals(TagType.TAG)) return "";
        return null;
    }

    private static int categoryFilter(SearchRequest request) {
        int show = 0;
        for (Tag tag : request.tags()) {
            if (tag == null || !TagType.CATEGORY.equals(tag.getType())) continue;
            EHentaiCategory cat = EHentaiCategory.fromLabel(tag.getName());
            if (cat != EHentaiCategory.UNKNOWN) show |= cat.bit();
        }
        return show == 0 ? 0 : EHentaiCategory.showOnly(show);
    }

    private static EHentaiUrls.Advanced pageRangeFilter(SearchRequest request) {
        Ranges ranges = request.ranges();
        if (ranges == null || ranges.isDefault()) return null;
        EHentaiUrls.Advanced advanced = new EHentaiUrls.Advanced();
        boolean any = false;
        if (ranges.getFromPage() != Ranges.UNDEFINED) {
            advanced.minPages = ranges.getFromPage();
            any = true;
        }
        if (ranges.getToPage() != Ranges.UNDEFINED) {
            advanced.maxPages = ranges.getToPage();
            any = true;
        }
        if (ranges.getMinRating() != Ranges.UNDEFINED) {
            advanced.minRating = ranges.getMinRating();
            any = true;
        }
        return any ? advanced : null;
    }

    @Override
    public String buildGalleryUrl(GalleryKey key) {
        return EHentaiUrls.galleryUrl(exhentai, key.id(), key.token());
    }

    @Override
    public String favoritesUrl() {
        return EHentaiUrls.favoritesUrl(exhentai, EHentaiFavorites.viewSlot(), null);
    }

    @Nullable
    @Override
    public String languageQueryTerm(Language language) {
        if (language == null) return null;
        switch (language) {
            case ENGLISH:
                return "language:english";
            case JAPANESE:
                return "language:japanese";
            case CHINESE:
                return "language:chinese";
            default:
                return null;
        }
    }
}
