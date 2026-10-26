package com.nabd.news

import org.junit.Assert.*
import org.junit.Test
import org.jsoup.Jsoup

/** Protocol/configuration regressions; not evidence of real platform playback or audible device output. */
class PlaybackRegressionTest {
    @Test fun tiktokCanUnmuteWithoutReloadEvenWhenSessionStartsMuted(){
        val target=MediaPolicy.target("https://www.tiktok.com/@scout2015/video/6718335390845095173")!!
        val html=MediaEmbedPlayback.html(target,"https://com.nabd.news/",true,true)
        val frame=Jsoup.parse(html).selectFirst("iframe")!!
        assertTrue(frame.attr("src").contains("muted=0"))
        assertTrue(frame.attr("src").contains("autoplay=1"))
        assertTrue(html.contains("desiredMute=true"));assertTrue(html.contains("'unMute'"))
        assertTrue(html.contains("e.origin!==origin||e.source!==frame.contentWindow"))
        assertFalse(html.contains("addJavascriptInterface"));assertTrue(html.contains("postMessage(data,origin)"))
    }
    @Test fun officialYoutubeApiPreservesInlinePlaybackAndOrigin(){
        val t=MediaPolicy.target("https://www.youtube.com/watch?v=abcdefghijk")!!
        val html=MediaEmbedPlayback.html(t,"https://com.nabd.news/",true,false)
        val frame=Jsoup.parse(html).selectFirst("iframe")!!
        assertTrue(frame.attr("src").contains("enablejsapi=1"));assertTrue(frame.attr("src").contains("playsinline=1"))
        assertTrue(frame.attr("allow").contains("autoplay"));assertTrue(html.contains("https://www.youtube.com/iframe_api"))
        assertTrue(html.contains("onAutoplayBlocked"));assertTrue(html.contains("youtube.unMute()"))
    }
    @Test fun playbackCommandsCannotNavigateOrInterpolateSourceText(){
        assertEquals("window.nabdSetPlayback && window.nabdSetPlayback(true,false);",MediaEmbedPlayback.command(true,false))
        assertFalse(MediaEmbedPlayback.command(false,true).contains("loadUrl"))
        assertTrue(runCatching{MediaEmbedPlayback.html(MediaTarget("YouTube","https://evil.test/watch"),"https://com.nabd.news/",true,false)}.isFailure)
    }
}
