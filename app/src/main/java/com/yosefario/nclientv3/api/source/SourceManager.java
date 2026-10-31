package com.yosefario.nclientv3.api.source;

import com.yosefario.nclientv3.api.source.ehentai.EHentaiSource;
import com.yosefario.nclientv3.api.source.nhentai.NhentaiSource;
import com.yosefario.nclientv3.settings.Global;
import java.util.ArrayList;
import java.util.List;

public final class SourceManager {
    private static final Source NHENTAI = new NhentaiSource();
    private static final Source EHENTAI = new EHentaiSource();
    private static final Source EXHENTAI = new EHentaiSource(true);

    private SourceManager() {
    }

    public static Source active() {
        return get(Global.getActiveSource());
    }

    public static List<Source> enabled() {
        List<Source> list = new ArrayList<>();
        for (SourceId id : Global.getEnabledSources()) list.add(get(id));
        if (list.isEmpty()) list.add(NHENTAI);
        return list;
    }

    public static List<Source> favoriteSources() {
        List<Source> list = new ArrayList<>();
        for (Source source : enabled())
            if (source.capabilities().hasOnlineFavorites()) list.add(source);
        return list;
    }

    public static Source favoriteSource() {
        List<Source> sources = favoriteSources();
        for (Source source : sources)
            if (Global.isFavoriteSource(source.id())) return source;
        for (Source source : sources)
            if (source.isLoggedIn()) return source;
        return sources.isEmpty() ? active() : sources.get(0);
    }

    public static Source get(SourceId id) {
        if (id == SourceId.EXHENTAI) return EXHENTAI;
        if (id == SourceId.EHENTAI) return EHENTAI;
        return NHENTAI;
    }
}
