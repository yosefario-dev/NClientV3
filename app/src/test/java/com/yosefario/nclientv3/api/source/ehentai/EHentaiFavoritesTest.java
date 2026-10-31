package com.yosefario.nclientv3.api.source.ehentai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class EHentaiFavoritesTest {
    private static final String NOT_FAVORITED =
        "<div id=\"gdf\"><a id=\"favoritelink\" href=\"#\">Add to Favorites</a></div>";
    private static final String SLOT_0 =
        "<div id=\"gdf\"><div id=\"fav\" style=\"background-position:0px -1px;background-color:rgba(0,0,0,"
            + "0.5);background-image:url(//ehgt.org/f.png)\"></div>"
            + "<a id=\"favoritelink\" href=\"#\">Favorites 0</a></div>";
    private static final String SLOT_3 =
        "<div id=\"gdf\"><div id=\"fav\" style=\"background-position:0px -21px;background-color:rgba(208,208,0,"
            + "0.5);background-image:url(//ehgt.org/f.png)\"></div>"
            + "<a id=\"favoritelink\" href=\"#\">Reading</a></div>";

    @Test
    public void notFavoritedIsNone() {
        assertEquals(EHentaiFavorites.NONE, EHentaiFavorites.parseSlot(NOT_FAVORITED));
    }

    @Test
    public void blackDotIsSlotZero() {
        assertEquals(0, EHentaiFavorites.parseSlot(SLOT_0));
    }

    @Test
    public void coloredDotMapsToItsSlot() {
        assertEquals(3, EHentaiFavorites.parseSlot(SLOT_3));
    }

    @Test
    public void missingHtmlIsNone() {
        assertEquals(EHentaiFavorites.NONE, EHentaiFavorites.parseSlot(null));
    }

    @Test
    public void nextCursorReadsTheUnextLink() {
        String html = "<a id=\"unext\" href=\"https://e-hentai.org/?f_search=x&amp;next=1234567\">&gt;</a>";
        assertEquals("1234567", EHentaiSearchParser.nextCursor(html));
    }

    @Test
    public void nextCursorKeepsTheFavoritesTimestamp() {
        String html = "<a id=\"unext\" href=\"https://e-hentai.org/favorites.php?favcat=all&amp;next=2961893-1718000000\">&gt;</a>";
        assertEquals("2961893-1718000000", EHentaiSearchParser.nextCursor(html));
    }

    @Test
    public void nextCursorIsNullOnTheLastPage() {
        assertNull(EHentaiSearchParser.nextCursor("<table class=\"ptt\"></table>"));
    }
}
