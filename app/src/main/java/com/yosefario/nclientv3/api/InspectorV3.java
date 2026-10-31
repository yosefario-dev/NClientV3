package com.yosefario.nclientv3.api;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yosefario.nclientv3.api.components.Gallery;
import com.yosefario.nclientv3.api.components.GenericGallery;
import com.yosefario.nclientv3.api.components.Ranges;
import com.yosefario.nclientv3.api.components.Tag;
import com.yosefario.nclientv3.api.enums.ApiRequestType;
import com.yosefario.nclientv3.api.enums.Language;
import com.yosefario.nclientv3.api.enums.SortType;
import com.yosefario.nclientv3.api.enums.SpecialTagIds;
import com.yosefario.nclientv3.api.enums.TagStatus;
import com.yosefario.nclientv3.api.enums.TagType;
import com.yosefario.nclientv3.api.local.LocalGallery;
import com.yosefario.nclientv3.api.source.GalleryKey;
import com.yosefario.nclientv3.api.source.GalleryPayload;
import com.yosefario.nclientv3.api.source.ListPayload;
import com.yosefario.nclientv3.api.source.SearchRequest;
import com.yosefario.nclientv3.api.source.Source;
import com.yosefario.nclientv3.api.source.SourceId;
import com.yosefario.nclientv3.api.source.SourceManager;
import com.yosefario.nclientv3.async.database.Queries;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import okhttp3.Request;
import okhttp3.Response;

public class InspectorV3 extends Thread implements Parcelable {
    public static final Creator<InspectorV3> CREATOR = new Creator<InspectorV3>() {
        @Override
        public InspectorV3 createFromParcel(Parcel in) {
            return new InspectorV3(in);
        }

        @Override
        public InspectorV3[] newArray(int size) {
            return new InspectorV3[size];
        }
    };
    private SortType sortType;
    private boolean custom, forceStart = false;
    private int page, pageCount = -1, total = -1, id;
    private final Set<String> mergeSeen = new HashSet<>();
    private String query, url;
    private ApiRequestType requestType;
    private Set<Tag> tags;
    private ArrayList<GenericGallery> galleries = null;
    private Ranges ranges = null;
    private InspectorResponse response;
    private WeakReference<Context> context;
    private GalleryPayload apiGalleryPayload;
    private ListPayload apiListPayload;
    private Document htmlDocument;
    private SourceId singleSource;
    private String singleToken;

    protected InspectorV3(Parcel in) {
        sortType = SortType.values()[in.readByte()];
        custom = in.readByte() != 0;
        page = in.readInt();
        pageCount = in.readInt();
        id = in.readInt();
        query = in.readString();
        url = in.readString();
        requestType = ApiRequestType.values[in.readByte()];
        ArrayList x = null;
        switch (GenericGallery.Type.values()[in.readByte()]) {
            case LOCAL:
                x = in.createTypedArrayList(LocalGallery.CREATOR);
                break;
            case SIMPLE:
                x = in.createTypedArrayList(SimpleGallery.CREATOR);
                break;
            case COMPLETE:
                x = in.createTypedArrayList(Gallery.CREATOR);
                break;
        }
        galleries = (ArrayList<GenericGallery>) x;
        tags = new HashSet<>(in.createTypedArrayList(Tag.CREATOR));
        ranges = in.readParcelable(Ranges.class.getClassLoader());
        String source = in.readString();
        singleSource = source == null ? null : SourceId.valueOf(source);
        singleToken = in.readString();
    }

    private InspectorV3(Context context, InspectorResponse response) {
        initialize(context, response);
    }

    /**
     * This method will not run, but a WebView inside MainActivity will do it in its place
     */
    public static InspectorV3 favoriteInspector(Context context, String query, int page, InspectorResponse response) {
        InspectorV3 inspector = new InspectorV3(context, response);
        inspector.page = page;
        inspector.pageCount = 0;
        inspector.query = query == null ? "" : query;
        inspector.requestType = ApiRequestType.FAVORITE;
        inspector.singleSource = SourceManager.favoriteSource().id();
        inspector.tags = new HashSet<>(1);
        inspector.createUrl();
        return inspector;
    }

    /**
     * @param favorite true if random online favorite, false for general random manga
     */
    public static InspectorV3 randomInspector(Context context, InspectorResponse response, boolean favorite) {
        InspectorV3 inspector = new InspectorV3(context, response);
        inspector.requestType = favorite ? ApiRequestType.RANDOM_FAVORITE : ApiRequestType.RANDOM;
        if (favorite) inspector.singleSource = SourceManager.favoriteSource().id();
        inspector.createUrl();
        return inspector;
    }

    public static InspectorV3 galleryInspector(Context context, int id, InspectorResponse response) {
        return galleryInspector(context, id, null, null, response);
    }

    public static InspectorV3 galleryInspector(Context context, int id, SourceId source, String token, InspectorResponse response) {
        InspectorV3 inspector = new InspectorV3(context, response);
        inspector.id = id;
        inspector.singleSource = source;
        inspector.singleToken = token;
        inspector.requestType = ApiRequestType.BYSINGLE;
        inspector.createUrl();
        return inspector;
    }

    public static InspectorV3 basicInspector(Context context, int page, InspectorResponse response) {
        return searchInspector(context, null, null, page, Global.getSortType(), null, response);
    }

    public static InspectorV3 tagInspector(Context context, Tag tag, int page, SortType sortType, InspectorResponse response) {
        Collection<Tag> tags;
        if (!Global.isOnlyTag()) {
            tags = getDefaultTags();
            tags.add(tag);
        } else {
            tags = Collections.singleton(tag);
        }
        return searchInspector(context, null, tags, page, sortType, null, response);
    }

    public static InspectorV3 searchInspector(Context context, String query, Collection<Tag> tags, int page, SortType sortType, @Nullable Ranges ranges, InspectorResponse response) {
        InspectorV3 inspector = new InspectorV3(context, response);
        inspector.custom = tags != null;
        inspector.tags = inspector.custom ? new HashSet<>(tags) : getDefaultTags();
        inspector.tags.addAll(getLanguageTags(Global.getOnlyLanguage()));
        inspector.page = page;
        inspector.pageCount = 0;
        inspector.ranges = ranges;
        inspector.query = query == null ? "" : query;
        inspector.sortType = sortType;
        if (inspector.query.isEmpty() && (ranges == null || ranges.isDefault())) {
            switch (inspector.tags.size()) {
                case 0:
                    inspector.requestType = ApiRequestType.BYALL;
                    inspector.tryByAllPopular();
                    break;
                case 1:
                    inspector.requestType = ApiRequestType.BYTAG;
                    //else by search for the negative tag
                    if (inspector.getTag().getStatus() != TagStatus.AVOIDED)
                        break;
                default:
                    inspector.requestType = ApiRequestType.BYSEARCH;
                    break;
            }
        } else inspector.requestType = ApiRequestType.BYSEARCH;
        inspector.createUrl();
        return inspector;
    }

    @NonNull
    private static HashSet<Tag> getDefaultTags() {
        HashSet<Tag> tags = new HashSet<>(Queries.TagTable.getAllStatus(TagStatus.ACCEPTED));
        tags.addAll(getLanguageTags(Global.getOnlyLanguage()));
        if (Global.removeAvoidedGalleries())
            tags.addAll(Queries.TagTable.getAllStatus(TagStatus.AVOIDED));
        tags.addAll(Queries.TagTable.getAllOnlineBlacklisted());
        return tags;
    }

    private static Set<Tag> getLanguageTags(Language onlyLanguage) {
        Set<Tag> tags = new HashSet<>();
        if (onlyLanguage == null) return tags;
        switch (onlyLanguage) {
            case ENGLISH:
                tags.add(Queries.TagTable.getTagById(SpecialTagIds.LANGUAGE_ENGLISH));
                break;
            case JAPANESE:
                tags.add(Queries.TagTable.getTagById(SpecialTagIds.LANGUAGE_JAPANESE));
                break;
            case CHINESE:
                tags.add(Queries.TagTable.getTagById(SpecialTagIds.LANGUAGE_CHINESE));
                break;
        }
        return tags;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (sortType != null)
            dest.writeByte((byte) (sortType.ordinal()));
        else dest.writeByte((byte) SortType.RECENT_ALL_TIME.ordinal());
        dest.writeByte((byte) (custom ? 1 : 0));
        dest.writeInt(page);
        dest.writeInt(pageCount);
        dest.writeInt(id);
        dest.writeString(query);
        dest.writeString(url);
        dest.writeByte(requestType.ordinal());
        if (galleries == null || galleries.size() == 0)
            dest.writeByte((byte) GenericGallery.Type.SIMPLE.ordinal());
        else dest.writeByte((byte) galleries.get(0).getType().ordinal());
        dest.writeTypedList(galleries);
        dest.writeTypedList(new ArrayList<>(tags));
        dest.writeParcelable(ranges, flags);
        dest.writeString(singleSource == null ? null : singleSource.name());
        dest.writeString(singleToken);
    }

    public String getSearchTitle() {
        //triggered only when in searchMode
        if (query.length() > 0) return query;
        return url.replace(Utility.getBaseUrl() + "search/?q=", "").replace('+', ' ');
    }

    public void initialize(Context context, InspectorResponse response) {
        this.response = response;
        this.context = new WeakReference<>(context);
    }

    public InspectorResponse getResponse() {
        return response;
    }

    public InspectorV3 cloneInspector(Context context, InspectorResponse response) {
        InspectorV3 inspectorV3 = new InspectorV3(context, response);
        inspectorV3.query = query;
        inspectorV3.url = url;
        inspectorV3.tags = tags;
        inspectorV3.requestType = requestType;
        inspectorV3.sortType = sortType;
        inspectorV3.pageCount = pageCount;
        inspectorV3.page = page;
        inspectorV3.id = id;
        inspectorV3.custom = custom;
        inspectorV3.ranges = ranges;
        inspectorV3.singleSource = singleSource;
        inspectorV3.singleToken = singleToken;
        return inspectorV3;
    }

    private void tryByAllPopular() {
        if (sortType != null && sortType != SortType.RECENT_ALL_TIME) {
            requestType = ApiRequestType.BYSEARCH;
            query = "-nclientv3";
        }
    }

    private void createUrl() {
        String query;
        try {
            query = this.query == null ? null : URLEncoder.encode(this.query, "UTF-8");
        } catch (UnsupportedEncodingException ignore) {
            query = this.query;
        }
        StringBuilder builder = new StringBuilder(Utility.getBaseUrl());
        if (requestType == ApiRequestType.BYALL) builder.append("?page=").append(page);
        else if (requestType == ApiRequestType.RANDOM) builder.append("random/");
        else if (requestType == ApiRequestType.RANDOM_FAVORITE) builder.append("favorites/random");
        else if (requestType == ApiRequestType.BYSINGLE) builder.append("g/").append(id);
        else if (requestType == ApiRequestType.FAVORITE) {
            builder.append("favorites/");
            if (query != null && query.length() > 0)
                builder.append("?q=").append(query).append('&');
            else builder.append('?');
            builder.append("page=").append(page);
        } else if (requestType == ApiRequestType.BYSEARCH || requestType == ApiRequestType.BYTAG) {
            builder.append("search/?q=").append(query);
            for (Tag tt : tags) {
                if (tt == null) continue;
                if (builder.toString().contains(tt.toQueryTag(TagStatus.ACCEPTED))) continue;
                builder.append('+').append(URLEncoder.encode(tt.toQueryTag()));
            }
            if (ranges != null)
                builder.append('+').append(ranges.toQuery());
            builder.append("&page=").append(page);
            if (sortType != null && sortType.getUrlAddition() != null) {
                builder.append("&sort=").append(sortType.getUrlAddition());
            }
        }
        url = builder.toString().replace(' ', '+');
        LogUtility.d("WWW: " + getBookmarkURL());
    }

    public void forceStart() {
        forceStart = true;
        start();
    }

    private String getBookmarkURL() {
        if (page < 2) return url;
        else return url.substring(0, url.lastIndexOf('=') + 1);
    }

    public boolean createDocument() throws IOException {
        if (apiGalleryPayload != null) return true;
        if (apiListPayload != null) return true;
        if (htmlDocument != null) return true;
        try {
            createApiDocument();
            return true;
        } catch (IOException | RuntimeException e) {
            boolean nhentaiActive = singleSource != null
                ? singleSource == SourceId.NHENTAI
                : SourceManager.active() != null && SourceManager.active().id() == SourceId.NHENTAI;
            if (requestType == ApiRequestType.BYSINGLE || !nhentaiActive) {
                if (e instanceof IOException) throw (IOException) e;
                throw new IOException(e);
            }
            LogUtility.e("API request failed; falling back to HTML scrape: " + url, e);
            return createHtmlDocument();
        }
    }

    private void createApiDocument() throws IOException {
        Source source = singleSource != null ? SourceManager.get(singleSource) : SourceManager.active();
        Context ctx = context.get();
        if (requestType == ApiRequestType.BYSINGLE) {
            apiGalleryPayload = source.fetchGallery(ctx, GalleryKey.of(source.id(), id, singleToken));
            return;
        }
        if (requestType == ApiRequestType.RANDOM) {
            apiGalleryPayload = source.fetchRandomGallery(ctx);
            id = apiGalleryPayload.getId();
            url = Utility.getBaseUrl() + "g/" + id;
            return;
        }
        if (requestType == ApiRequestType.RANDOM_FAVORITE) {
            apiGalleryPayload = source.fetchRandomFavorite(ctx);
            id = apiGalleryPayload.getId();
            url = Utility.getBaseUrl() + "g/" + id;
            return;
        }
        if (requestType == ApiRequestType.FAVORITE) {
            apiListPayload = source.fetchFavorites(ctx, buildSearchRequest());
            return;
        }
        List<Source> sources = SourceManager.enabled();
        if (sources.size() <= 1) {
            apiListPayload = (sources.isEmpty() ? source : sources.get(0)).fetchGalleries(ctx, buildSearchRequest());
            return;
        }
        SortType mergeSort = sortType;
        for (Source src : sources) {
            if (mergeSort != null && !src.capabilities().sortOptions().contains(mergeSort)) {
                mergeSort = SortType.RECENT_ALL_TIME;
                break;
            }
        }
        SearchRequest mergeRequest = new SearchRequest(requestType, query, tags, ranges, page, mergeSort);
        apiListPayload = mergeAcross(sources, src -> src.fetchGalleries(ctx, mergeRequest));
    }

    private interface Fetcher {
        ListPayload fetch(Source source) throws IOException;
    }

    private ListPayload mergeAcross(List<Source> sources, Fetcher fetcher) throws IOException {
        List<List<SimpleGallery>> perSource = new ArrayList<>();
        int mergedPageCount = 1;
        Exception lastError = null;
        for (Source src : sources) {
            try {
                ListPayload payload = fetcher.fetch(src);
                perSource.add(payload.getGalleries());
                mergedPageCount = Math.max(mergedPageCount, payload.getPageCount());
            } catch (Exception e) {
                lastError = e;
                LogUtility.e("Merge: source " + src.id() + " listing failed: " + e.getMessage(), e);
            }
        }
        if (perSource.isEmpty() && lastError != null) {
            if (lastError instanceof IOException) throw (IOException) lastError;
            throw new IOException(lastError);
        }
        List<SimpleGallery> merged = new ArrayList<>();
        for (int i = 0, added = 1; added > 0; i++) {
            added = 0;
            for (List<SimpleGallery> list : perSource)
                if (i < list.size()) {
                    merged.add(list.get(i));
                    added++;
                }
        }
        if (page <= 1) mergeSeen.clear();
        List<SimpleGallery> mergedUnique = new ArrayList<>(merged.size());
        for (SimpleGallery g : merged)
            if (mergeSeen.add(g.getSource().ordinal() + ":" + g.getId())) mergedUnique.add(g);
        return new ListPayload(mergedUnique, mergedPageCount, mergedUnique.size(), -1);
    }

    private boolean createHtmlDocument() throws IOException {
        LogUtility.d("createDocument fallback: fetching URL: " + url);
        Response response = Global.getClient(context.get()).newCall(new Request.Builder().url(url).build()).execute();
        try {
            int code = response.code();
            LogUtility.d("createDocument fallback: response code=" + code + " for URL: " + url);
            if (response.body() == null) return false;
            setHtmlDocument(Jsoup.parse(response.body().byteStream(), "UTF-8", Utility.getBaseUrl()));
            return code == HttpURLConnection.HTTP_OK;
        } finally {
            response.close();
        }
    }

    public void parseDocument() throws IOException, InvalidResponseException {
        if (apiGalleryPayload != null) {
            doSingle(apiGalleryPayload);
            apiGalleryPayload = null;
        } else if (apiListPayload != null) {
            doSearch(apiListPayload);
            apiListPayload = null;
        } else if (htmlDocument != null) {
            if (requestType.isSingle()) doSingle(htmlDocument.body());
            else doSearch(htmlDocument.body());
            htmlDocument = null;
        } else throw new InvalidResponseException();
    }

    public void setHtmlDocument(Document htmlDocument) {
        this.htmlDocument = htmlDocument;
    }

    public boolean canParseDocument() {
        return this.apiGalleryPayload != null || this.apiListPayload != null || this.htmlDocument != null;
    }

    @Override
    public synchronized void start() {
        if (getState() != State.NEW) return;
        if (forceStart || response.shouldStart(this))
            super.start();
    }

    @Override
    public void run() {
        LogUtility.d("Starting download: " + url);
        if (response != null) response.onStart();
        try {
            createDocument();
            parseDocument();
            if (response != null) {
                response.onSuccess(galleries);
            }
        } catch (Exception e) {
            if (response != null) response.onFailure(e);
        }
        if (response != null) response.onEnd();
        LogUtility.d("Finished download: " + url);
    }

    private void filterDocumentTags() {
        if (galleries == null || tags == null) return;
        ArrayList<SimpleGallery> galleryTag = new ArrayList<>(galleries.size());
        for (GenericGallery gal : galleries) {
            assert gal instanceof SimpleGallery;
            SimpleGallery gallery = (SimpleGallery) gal;
            if (gallery.getSource() != SourceId.NHENTAI || gallery.hasTags(tags)) {
                galleryTag.add(gallery);
            }
        }
        galleries.clear();
        galleries.addAll(galleryTag);
    }

    private void doSingle(GalleryPayload galleryPayload) throws IOException {
        galleries = new ArrayList<>(1);
        galleries.add(new Gallery(context.get(), galleryPayload.getJson(), galleryPayload.getRelated(), galleryPayload.favoriteSlot()));
    }

    private void doSingle(Element document) throws IOException, InvalidResponseException {
        galleries = new ArrayList<>(1);
        Elements scripts = document.getElementsByTag("script");
        LogUtility.d("doSingle fallback: found " + scripts.size() + " script tags");
        String json = null;
        for (Element script : scripts) {
            json = trimScriptTag(script.html());
            if (json != null) break;
        }
        if (json == null) throw new InvalidResponseException();
        Element relContainer = document.getElementById("related-container");
        Elements rel = relContainer == null ? new Elements() : relContainer.getElementsByClass("gallery");
        ArrayList<SimpleGallery> related = new ArrayList<>(rel.size());
        for (Element element : rel) related.add(new SimpleGallery(context.get(), element));
        boolean isFavorite;
        try {
            isFavorite = document.getElementById("favorite").getElementsByTag("span").get(0).text().equals("Unfavorite");
        } catch (Exception e) {
            isFavorite = false;
        }
        galleries.add(new Gallery(context.get(), json, related, isFavorite ? 0 : -1));
    }

    @Nullable
    private String trimScriptTag(String scriptHtml) {
        int parseStart = scriptHtml.indexOf("JSON.parse(");
        if (parseStart < 0) parseStart = scriptHtml.indexOf("parse(");
        if (parseStart < 0) return null;
        int start = scriptHtml.indexOf('"', parseStart);
        int end = scriptHtml.indexOf(");", start);
        if (start < 0 || end <= start) return null;
        if (scriptHtml.charAt(end - 1) == '"') end--;
        String json = Utility.unescapeUnicodeString(scriptHtml.substring(start + 1, end));
        return json.isEmpty() ? null : json;
    }

    private void doSearch(ListPayload payload) {
        galleries = new ArrayList<>(payload.getGalleries().size());
        galleries.addAll(payload.getGalleries());
        pageCount = payload.getPageCount();
        total = payload.getTotal();
        LogUtility.d("doSearch: API pageCount=" + pageCount + ", galleries parsed=" + galleries.size());
        if (Global.isExactTagMatch())
            filterDocumentTags();
    }

    private void doSearch(Element document) throws InvalidResponseException {
        Elements gal = document.getElementsByClass("gallery");
        LogUtility.d("doSearch fallback: found " + gal.size() + " gallery elements");
        galleries = new ArrayList<>(gal.size());
        for (Element e : gal) galleries.add(new SimpleGallery(context.get(), e));
        gal = document.getElementsByClass("last");
        pageCount = gal.size() == 0 ? Math.max(1, page) : findTotal(gal.last());
        total = -1;
        LogUtility.d("doSearch fallback: pageCount=" + pageCount + ", galleries parsed=" + galleries.size());
        if (document.getElementById("content") == null && galleries.isEmpty())
            throw new InvalidResponseException();
        if (Global.isExactTagMatch())
            filterDocumentTags();
    }

    private int findTotal(Element e) {
        String temp = e.attr("href");
        try {
            return Integer.parseInt(Uri.parse(temp).getQueryParameter("page"));
        } catch (Exception ignore) {
            return 1;
        }
    }

    private SearchRequest buildSearchRequest() {
        return new SearchRequest(requestType, query, tags, ranges, page, sortType);
    }

    public void setSortType(SortType sortType) {
        this.sortType = sortType;
        createUrl();
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
        createUrl();
    }

    public List<GenericGallery> getGalleries() {
        return galleries;
    }

    public String getUrl() {
        return url;
    }

    public ApiRequestType getRequestType() {
        return requestType;
    }

    public int getTotal() {
        return total;
    }

    public int getPageCount() {
        return pageCount;
    }

    public boolean isCustom() {
        return custom;
    }

    public String getQuery() {
        return query;
    }

    public Tag getTag() {
        Tag t = null;
        if (tags == null) return null;
        for (Tag tt : tags) {
            if (tt.getType() != TagType.LANGUAGE)
                return tt;
            t = tt;
        }
        return t;
    }

    public static class InvalidResponseException extends Exception {
        public InvalidResponseException() {
            super();
        }
    }

    public interface InspectorResponse {
        boolean shouldStart(InspectorV3 inspector);

        void onSuccess(List<GenericGallery> galleries);

        void onFailure(Exception e);

        void onStart();

        void onEnd();
    }

    public static abstract class DefaultInspectorResponse implements InspectorResponse {
        @Override
        public boolean shouldStart(InspectorV3 inspector) {
            return true;
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onEnd() {
        }

        @Override
        public void onSuccess(List<GenericGallery> galleries) {
        }

        @Override
        public void onFailure(Exception e) {
            LogUtility.e(e.getLocalizedMessage(), e);
        }
    }
}
