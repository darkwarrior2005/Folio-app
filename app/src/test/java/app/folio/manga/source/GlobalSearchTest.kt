package app.folio.manga.source

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {

    @Test
    fun cloudflareChallengeNeedsWebView() {
        assertTrue(GlobalSearch.needsWebView(CloudflareChallengeException()))
    }

    @Test
    fun http403And503NeedWebView() {
        assertTrue(GlobalSearch.needsWebView(HttpException(403)))
        assertTrue(GlobalSearch.needsWebView(HttpException(503)))
    }

    @Test
    fun otherHttpErrorsDoNotNeedWebView() {
        assertFalse(GlobalSearch.needsWebView(HttpException(404)))
    }

    @Test
    fun messageMentioningCaptchaOrLoginNeedsWebView() {
        assertTrue(GlobalSearch.needsWebView(Exception("Please solve the captcha")))
        assertTrue(GlobalSearch.needsWebView(Exception("Login required")))
    }

    @Test
    fun aPlainParsingFailureDoesNotNeedWebView() {
        assertFalse(GlobalSearch.needsWebView(Exception("Unable to parse response")))
    }
}
