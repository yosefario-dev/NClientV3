package com.yosefario.nclientv3.api.source.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EHentaiPagerTest {
    private static final String BASE = "https://e-hentai.org/?f_search=x";
    private final List<String> requested = new ArrayList<>();
    private int pages = 3;

    private String pageHtml(int index) {
        String next = index + 1 < pages
            ? "<a id=\"unext\" href=\"https://e-hentai.org/?f_search=x&amp;next=" + (1000 + index) + "\">&gt;</a>"
            : "";
        return "<html><body>" + next + "<p>page " + index + "</p></body></html>";
    }

    @Before
    public void setUp() {
        EHentaiPager.setSource(url -> {
            requested.add(url);
            int index = 0;
            int at = url.indexOf("next=");
            if (at >= 0) index = Integer.parseInt(url.substring(at + 5)) - 999;
            if (index > pages) throw new IOException("past the end");
            return pageHtml(index);
        });
    }

    @After
    public void tearDown() {
        EHentaiPager.setSource(null);
    }

    @Test
    public void firstPageHasNoQuery() throws IOException {
        String html = EHentaiPager.htmlFor(BASE, 0);
        assertTrue(html.contains("page 0"));
        assertEquals(1, requested.size());
        assertTrue(EHentaiPager.hasNext(BASE, 1));
    }

    @Test
    public void walksToTheRequestedPage() throws IOException {
        assertEquals("page 0", marker(EHentaiPager.htmlFor(BASE, 0)));
        assertEquals("page 1", marker(EHentaiPager.htmlFor(BASE, 1)));
        assertEquals("page 2", marker(EHentaiPager.htmlFor(BASE, 2)));
        assertEquals(3, requested.size());
        assertFalse(EHentaiPager.hasNext(BASE, 3));
    }

    @Test
    public void cachesWalkedPagesAndDoesNotRefetch() throws IOException {
        EHentaiPager.htmlFor(BASE, 0);
        EHentaiPager.htmlFor(BASE, 1);
        EHentaiPager.htmlFor(BASE, 2);
        EHentaiPager.htmlFor(BASE, 1);
        assertEquals(3, requested.size());
    }

    @Test
    public void jumpAheadWalksForward() throws IOException {
        pages = 6;
        String html = EHentaiPager.htmlFor(BASE, 4);
        assertEquals("page 4", marker(html));
        assertEquals(5, requested.size());
    }

    @Test
    public void tooFarAheadReturnsNull() throws IOException {
        pages = 40;
        assertNull(EHentaiPager.htmlFor(BASE, 30));
        assertTrue(requested.size() <= 9);
    }

    @Test
    public void pastTheEndStopsAndReportsNoNext() throws IOException {
        pages = 2;
        EHentaiPager.htmlFor(BASE, 0);
        assertFalse(EHentaiPager.hasNext(BASE, 2));
        assertNull(EHentaiPager.htmlFor(BASE, 2));
        assertTrue(EHentaiPager.hasNext(BASE, 1));
    }

    @Test
    public void refreshResetsTheChain() throws IOException {
        pages = 4;
        EHentaiPager.htmlFor(BASE, 0);
        EHentaiPager.htmlFor(BASE, 1);
        int before = requested.size();
        EHentaiPager.htmlFor(BASE, 0);
        assertTrue(EHentaiPager.hasNext(BASE, 1));
        assertEquals(before + 1, requested.size());
    }

    @Test
    public void nextUrlKeepsExistingQuery() {
        assertEquals("https://e-hentai.org/?next=5", EHentaiPager.nextUrl("https://e-hentai.org/", "5"));
        assertEquals("https://e-hentai.org/?favcat=1&next=5", EHentaiPager.nextUrl("https://e-hentai.org/?favcat=1", "5"));
    }

    private static String marker(String html) {
        int at = html.indexOf("page ");
        return html.substring(at, at + 6);
    }
}
