package com.yosefario.nclientv3.api;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;

import androidx.annotation.NonNull;

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
    private final int id, mediaId;
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
    }

    public boolean hasTag(Tag tag) {
        return tags.hasTag(tag);
    }

    public boolean hasTags(Collection<Tag> tags) {
        return this.tags.hasTags(tags);
    }

    public SimpleGallery(Cursor c) {
        title = c.getString(c.getColumnIndex(Queries.HistoryTable.TITLE));
        id = c.getInt(c.getColumnIndex(Queries.HistoryTable.ID));
        mediaId = c.getInt(c.getColumnIndex(Queries.HistoryTable.MEDIAID));
        thumbnail = ImageExt.values()[c.getInt(c.getColumnIndex(Queries.HistoryTable.THUMB))];
    }

    public SimpleGallery(Context context, Element e) {
        String temp;
        String tags = e.attr("data-tags").replace(' ', ',');
        this.tags = Queries.TagTable.getTagsFromListOfInt(tags);
        language = Gallery.loadLanguage(this.tags);
        Element a = e.getElementsByTag("a").first();
        temp = a.attr("href");
        id = Integer.parseInt(temp.substring(3, temp.length() - 1));
        a = e.getElementsByTag("img").first();
        temp = a.hasAttr("data-src") ? a.attr("data-src") : a.attr("src");
        mediaId = Integer.parseInt(temp.substring(temp.indexOf("galleries") + 10, temp.lastIndexOf('/')));
        // Parse extension from the last dot in the URL (handles .webp, .jpg, .jpg.webp etc)
        int lastDot = temp.lastIndexOf('.');
        thumbnail = lastDot >= 0 ? Page.charToExt(temp.charAt(lastDot + 1)) : ImageExt.JPG;
        // Store the raw thumbnail URL for direct use
        if (temp.startsWith("//")) temp = "https:" + temp;
        thumbnailUrl = temp;
        LogUtility.d("SimpleGallery thumb URL: " + thumbnailUrl + " ext=" + thumbnail);
        title = e.getElementsByTag("div").first().text();
        if (context != null && id > Global.getMaxId()) Global.updateMaxId(context, id);
    }

    public SimpleGallery(Gallery gallery) {
        title = gallery.getTitle();
        mediaId = gallery.getMediaId();
        id = gallery.getId();
        thumbnail = gallery.getThumb();
        thumbnailUrl = null; // will be reconstructed from mediaId + extension
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
        return 0;
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
