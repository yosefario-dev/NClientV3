package com.yosefario.nclientv3.api.source.ehentai;

import java.util.Locale;

public enum EHentaiCategory {
    MISC(0x1, "Misc"),
    DOUJINSHI(0x2, "Doujinshi"),
    MANGA(0x4, "Manga"),
    ARTIST_CG(0x8, "Artist CG"),
    GAME_CG(0x10, "Game CG"),
    IMAGE_SET(0x20, "Image Set"),
    COSPLAY(0x40, "Cosplay"),
    ASIAN_PORN(0x80, "Asian Porn"),
    NON_H(0x100, "Non-H"),
    WESTERN(0x200, "Western"),
    PRIVATE(0x400, "Private"),
    UNKNOWN(0, "Unknown");

    public static final int ALL = 0x3ff;

    private final int bit;
    private final String label;

    EHentaiCategory(int bit, String label) {
        this.bit = bit;
        this.label = label;
    }

    public int bit() {
        return bit;
    }

    public String label() {
        return label;
    }

    private static String normalize(String s) {
        return s.replace(" ", "").replace("-", "").toLowerCase(Locale.US);
    }

    public static EHentaiCategory fromLabel(String name) {
        if (name == null) return UNKNOWN;
        String compact = normalize(name);
        for (EHentaiCategory c : values()) {
            if (normalize(c.label).equals(compact)) return c;
        }
        return UNKNOWN;
    }

    public static int showOnly(int categoryBits) {
        return ALL & ~(categoryBits & ALL);
    }
}
