package com.nabd.news

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE,application=Application::class)
class SharedTextIntentTest {
    @Test fun consumesSharedTextOnceAndPreservesLifecycleIdentity(){
        val url="https://x.com/BBCArabic/status/123456789"
        val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,url)
        share.clipData=ClipData.newPlainText("post",url)
        val identity=Intent(share)
        assertEquals(url,consumeSharedText(share));assertTrue(identity.filterEquals(share))
        assertNull(share.clipData);assertFalse(share.hasExtra(Intent.EXTRA_TEXT));assertNull(consumeSharedText(share))
    }
    @Test fun acceptsExplicitClipTextButNeverDereferencesUriOnlyShares(){
        val share=Intent(Intent.ACTION_SEND).setType("text/plain")
        share.clipData=ClipData.newPlainText("post","https://x.com/test/status/123")
        assertEquals("https://x.com/test/status/123",consumeSharedText(share))
        share.clipData=ClipData("private",arrayOf("text/plain"),ClipData.Item(Uri.parse("content://private/unread")))
        assertNull(consumeSharedText(share));assertNull(share.clipData)
    }
    @Test fun ignoresNonShareIntentsAndBoundsUntrustedText(){
        val regular=Intent(Intent.ACTION_MAIN).putExtra(Intent.EXTRA_TEXT,"ordinary")
        assertNull(consumeSharedText(regular));assertTrue(regular.hasExtra(Intent.EXTRA_TEXT))
        val share=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"a".repeat(30000))
        assertEquals(20000,consumeSharedText(share)!!.length)
    }
}
