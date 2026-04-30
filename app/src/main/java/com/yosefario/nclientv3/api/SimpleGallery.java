package com.yosefario.nclientv3.api;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yosefario.nclientv3.api.components.Gallery;
import com.yosefario.nclientv3.api.components.GalleryData;
import com.yosefario.nclientv3.api.components.GenericGallery;
import com.yosefario.nclientv3.api.components.Page;
import com.yosefario.nclientv3.api.components.Tag;
import com.yosefario.nclientv3.api.components.TagList;
import com.yosefario.nclientv3.api.enums.ImageExt;
import com.yosefario.nclientv3.api.enums.Language;
import com.yosefario.nclientv3.api.enums.TagStatus;
import com.yosefario.nclientv3.async.database.Queries;
import com.yosefario.nclientv3.components.classes.Size;
import com.yosefario.nclientv3.files.GalleryFolder;
import com.yosefario.nclientv3.settings.Global;
import com.yosefario.nclientv3.utility.LogUtility;
import com.yosefario.nclientv3.utility.Utility;

import org.jsoup.nodes.Element;

import java.util.Collection;
import java.util.Locale;

public class SimpleGallery extends GenericGallery {
    public static final Creator<SimpleGallery> CREATOR = new Creator<SimpleGallery>() {
        @Override
        public SimpleGallery createFromParcel(Parcel in) {
            return new SimpleGallery(in);
        }

        @Override
        public SimpleGallery[] newArray(int size) {
            return new SimpleGallery[size];
        }
    };
    private final String title;
    private final ImageExt thumbnail;
    private final int id, mediaId, pageCount;
    private String thumbnailUrl; // raw thumbnail URL from HTML data-src
    private Language language = Language.UNKNOWN;
    private TagList tags;

    public SimpleGallery(Parcel in) {
        title = in.readString();
        id = in.readInt();
        mediaId = in.readInt();
        thumbnail = ImageExt.values()[in.readByte()];
        language = Language.values()[in.readByte()];
        thumbnailUrl = in.readString();
        pageCount = in.readInt();
    }

    public boolean hasTag(Tag tag) {
        return tags != null && tags.hasTag(tag);
    }

    public boolean hasTags(Collection<Tag> tags) {
        return this.tags != null && this.tags.hasTags(tags);
    }

    public SimpleGallery(Cursor c) {
        title = c.getString(c.getColumnIndex(Queries.HistoryTable.TITLE));
        id = c.getInt(c.getColumnIndex(Queries.HistoryTable.ID));
        mediaId = c.getInt(c.getColumnIndex(Queries.HistoryTable.MEDIAID));
        thumbnail = ImageExt.values()[c.getInt(c.getColumnIndex(Queries.HistoryTable.THUMB))];
        pageCount = 0;
    }

    public SimpleGallery(Context context, Element e) {
        String temp;
        String tagIds = e.attr("data-tags").replace(' ', ',');
        tags = tagIds.isEmpty() ? new TagList() : Queries.TagTable.getTagsFromListOfInt(tagIds);
        language = Gallery.loadLanguage(tags);
        Element anchor = e.getElementsByTag("a").first();
        if (anchor == null) throw new IllegalArgumentException("Gallery element has no anchor");
        temp = anchor.attr("href");
        id = Integer.parseInt(temp.substring(3, temp.length() - 1));
        Element image = e.getElementsByTag("img").first();
        if (image == null) throw new IllegalArgumentException("Gallery element has no thumbnail");
        temp = image.hasAttr("data-src") ? image.attr("data-src") : image.attr("src");
        mediaId = parseMediaId(temp);
        int lastDot = temp.lastIndexOf('.');
        thumbnail = lastDot >= 0 ? Page.charToExt(temp.charAt(lastDot + 1)) : ImageExt.JPG;
        if (temp.startsWith("//")) temp = "https:" + temp;
        thumbnailUrl = temp;
        Element titleElement = e.getElementsByTag("div").first();
        title = titleElement == null ? "" : titleElement.text();
        pageCount = 0;
        LogUtility.d("SimpleGallery fallback thumb URL: " + thumbnailUrl + " ext=" + thumbnail);
        if (context != null && id > Global.getMaxId()) Global.updateMaxId(context, id);
    }

    public SimpleGallery(Gallery gallery) {
        title = gallery.getTitle();
        mediaId = gallery.getMediaId();
        id = gallery.getId();
        thumbnail = gallery.getThumb();
        thumbnailUrl = null; // will be reconstructed from mediaId + extension
        pageCount = gallery.getPageCount();
    }

    public SimpleGallery(String title, int id, int mediaId, ImageExt thumbnail, String thumbnailUrl, int pageCount) {
        this(null, title, id, mediaId, thumbnail, thumbnailUrl, pageCount, null);
    }

    public SimpleGallery(@Nullable Context context, String title, int id, int mediaId, ImageExt thumbnail, String thumbnailUrl, int pageCount, @Nullable TagList tags) {
        this.title = title == null ? "" : title;
        this.id = id;
        this.mediaId = mediaId;
        this.thumbnail = thumbnail == null ? ImageExt.JPG : thumbnail;
        this.thumbnailUrl = thumbnailUrl;
        this.pageCount = pageCount;
        this.tags = tags;
        if (this.tags != null) language = Gallery.loadLanguage(this.tags);
        if (context != null && id > Global.getMaxId()) Global.updateMaxId(context, id);
    }

    private static int parseMediaId(String thumbnailUrl) {
        int start = thumbnailUrl.indexOf("galleries/");
        if (start < 0) return 0;
        start += "galleries/".length();
        int end = thumbnailUrl.indexOf('/', start);
        if (end < 0) return 0;
        try {
            return Integer.parseInt(thumbnailUrl.substring(start, end));
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    private static String extToString(ImageExt ext) {
        if (ext == null) return "jpg";
        switch (ext) {
            case GIF:
                return "gif";
            case PNG:
                return "png";
            case JPG:
                return "jpg";
            case WEBP:
                return "webp";
        }
        return "jpg";
    }

    public Language getLanguage() {
        return language;
    }

    public boolean hasIgnoredTags(String s) {
        if (tags == null) return false;
        for (Tag t : tags.getAllTagsList())
            if (s.contains(t.toQueryTag(TagStatus.AVOIDED))) {
                LogUtility.d("Found: " + s + ",," + t.toQueryTag());
                return true;
            }
        return false;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public Type getType() {
        return Type.SIMPLE;
    }

    @Override
    public int getPageCount() {
        return pageCount;
    }

    @Override
    public boolean isValid() {
        return id > 0;
    }

    @Override
    @NonNull
    public String getTitle() {
        return title;
    }

    @Override
    public Size getMaxSize() {
        return null;
    }

    @Override
    public Size getMinSize() {
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeInt(id);
        dest.writeInt(mediaId);
        dest.writeByte((byte) thumbnail.ordinal());
        dest.writeByte((byte) language.ordinal());
        dest.writeString(thumbnailUrl);
        dest.writeInt(pageCount);
        //TAGS AREN'T WRITTEN
    }

    public Uri getThumbnail() {
        // Use stored raw URL from HTML data-src when available (most reliable)
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            return Uri.parse(thumbnailUrl);
        }
        // Fallback: construct URL for database/parcel restored galleries
        if (thumbnail != null && thumbnail == ImageExt.GIF) {
            return Uri.parse(String.format(Locale.US, "https://i." + Utility.getHost() + "/galleries/%d/1.gif", mediaId));
        }
        return Uri.parse(String.format(Locale.US, "https://t." + Utility.getHost() + "/galleries/%d/thumb.%s", mediaId, extToString(thumbnail)));
    }

    public int getMediaId() {
        return mediaId;
    }

    public ImageExt getThumb() {
        return thumbnail;
    }

    @Override
    public GalleryFolder getGalleryFolder() {
        return null;
    }

    @Override
    public String toString() {
        return "SimpleGallery{" +
            "language=" + language +
            ", title='" + title + '\'' +
            ", thumbnail=" + thumbnail +
            ", id=" + id +
            ", mediaId=" + mediaId +
            ", pageCount=" + pageCount +
            '}';
    }

    @Override
    public boolean hasGalleryData() {
        return false;
    }

    @Override
    public GalleryData getGalleryData() {
        return null;
    }
}
