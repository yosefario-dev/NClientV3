package com.yosefario.nclientv3.api.source;

public enum SourceId {
    NHENTAI("nh", "nhentai"),
    EHENTAI("eh", "e-hentai"),
    EXHENTAI("ex", "ExHentai");

    private final String prefix, displayName;

    SourceId(String prefix, String displayName) {
        this.prefix = prefix;
        this.displayName = displayName;
    }

    public String prefix() {
        return prefix;
    }

    public String displayName() {
        return displayName;
    }
}
