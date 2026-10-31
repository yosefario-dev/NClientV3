package com.yosefario.nclientv3.api.source.ehentai;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EHentaiPager {
    private static final int MAX_QUERIES = 24;
    private static final int MAX_WALK = 8;
    private static final int CACHED_PAGES = 4;

    private static final Map<String, Chain> CHAINS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Chain> eldest) {
            return size() > MAX_QUERIES;
        }
    };

    interface Fetcher {
        String get(String url) throws IOException;
    }

    private static volatile Fetcher fetcher = EHentaiHttp::get;

    private EHentaiPager() {
    }

    static void setSource(@Nullable Fetcher source) {
        fetcher = source == null ? EHentaiHttp::get : source;
        reset();
    }

    @Nullable
    static String htmlFor(String baseUrl, int page) throws IOException {
        Chain chain = chain(baseUrl);
        synchronized (chain) {
            if (page > 0 && chain.size() == 0) {
                String first = fetcher.get(baseUrl);
                chain.reset(cursorOf(first), first);
                if (page == 0) return first;
            }
            if (page <= 0) {
                String html = fetcher.get(baseUrl);
                chain.reset(cursorOf(html), html);
                return html;
            }
            if (page - chain.size() >= MAX_WALK) return null;
            while (chain.size() <= page) {
                int next = chain.size();
                String cursor = chain.cursorAt(next - 1);
                if (cursor.isEmpty()) return null;
                String html = fetcher.get(nextUrl(baseUrl, cursor));
                chain.put(next, cursorOf(html), html);
                if (next == page) return html;
            }
            String cached = chain.htmlAt(page);
            if (cached != null) return cached;
            String cursor = chain.cursorAt(page - 1);
            return cursor.isEmpty() ? null : fetcher.get(nextUrl(baseUrl, cursor));
        }
    }

    static boolean hasNext(String baseUrl, int requestPage) {
        Chain chain = chain(baseUrl);
        synchronized (chain) {
            int index = requestPage - 1;
            if (index < 0 || chain.size() <= index) return false;
            return !chain.cursorAt(index).isEmpty();
        }
    }

    static String nextUrl(String baseUrl, String cursor) {
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + "next=" + cursor;
    }

    private static String cursorOf(String html) {
        String next = EHentaiSearchParser.nextCursor(html);
        return next == null ? "" : next;
    }

    private static Chain chain(String baseUrl) {
        synchronized (CHAINS) {
            return CHAINS.computeIfAbsent(baseUrl, k -> new Chain());
        }
    }

    static void reset() {
        synchronized (CHAINS) {
            CHAINS.clear();
        }
    }

    private static final class Chain {
        private final List<String> cursors = new ArrayList<>();
        private final Map<Integer, String> pages = new LinkedHashMap<Integer, String>(8, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                return size() > CACHED_PAGES;
            }
        };

        int size() {
            return cursors.size();
        }

        String cursorAt(int index) {
            return cursors.get(index);
        }

        String htmlAt(int page) {
            return pages.get(page);
        }

        void put(int page, String cursor, String html) {
            cursors.add(cursor);
            pages.put(page, html);
        }

        void reset(String cursor, String html) {
            cursors.clear();
            pages.clear();
            cursors.add(cursor);
            pages.put(0, html);
        }
    }
}
