package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.*
import org.junit.Test

class LetterboxdBrowserTest {
    @Test fun browserChallengeIsRejectedEvenWhenItsHtmlIsLarge() {
        assertTrue(LetterboxdWeb.isChallenge("<html><title>Just a moment...</title>" + " ".repeat(30_000)))
        assertTrue(LetterboxdWeb.isChallenge("<form id=\"challenge-form\">Wait</form>"))
        assertFalse(LetterboxdWeb.isChallenge("<html><title>Member profile</title><body>Films and lists</body></html>"))
    }

    @Test fun aClosedFetcherReturnsWithoutStartingTheBrowser() {
        val web = LetterboxdWeb()
        web.close()
        assertNull(web.page("/someone/"))
        assertNull(web.json("/film/example/poster/std/230/"))
    }
}
