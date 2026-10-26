package com.nabd.news

import android.graphics.Bitmap
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ReaderNavigation {
    fun allowed(url: String): Boolean = url.toHttpUrlOrNull()?.let { it.isHttps && it.username.isBlank() && it.password.isBlank() } == true
}

/** Normal publisher page, never a DOM scraping bridge. No injected JS, credentials, or SSL override. */
@Composable fun OriginalArticleReader(url: String, source: String, close: () -> Unit) {
    var browser by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(url) { mutableStateOf(url) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var canBack by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) { onDispose { browser?.apply { stopLoading(); webChromeClient = null; destroy() }; browser = null } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { if (browser?.canGoBack() == true) browser?.goBack() else close() }
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = close) { Text("العودة إلى نبض") }
                    if (canBack) TextButton(onClick = { browser?.goBack() }) { Text("الصفحة السابقة") }
                    TextButton(onClick = { openOriginalArticle(context, currentUrl) }) { Text("فتح بالمتصفح") }
                }
                Text("$source • صفحة المصدر الأصلية", Modifier.padding(horizontal = 20.dp), color = Mint)
                Caption(currentUrl.toHttpUrlOrNull()?.host.orEmpty())
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) {
                    Text("تعذر تحميل الصفحة. قد تتطلب اتصالًا أو اشتراكًا لدى المصدر.", Modifier.padding(20.dp))
                    TextButton(onClick = { failed = false; browser?.reload() }) { Text("إعادة المحاولة") }
                }
                AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { ctx ->
                    WebView(ctx).apply {
                        browser = this
                        settings.apply {
                            javaScriptEnabled = true // Publisher's normal interactive page only; no evaluateJavascript or interface.
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            safeBrowsingEnabled = true
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !ReaderNavigation.allowed(request.url.toString())
                            override fun onPageStarted(view: WebView, pageUrl: String, favicon: Bitmap?) {
                                currentUrl = pageUrl; loading = true; failed = false
                            }
                            override fun onPageFinished(view: WebView, pageUrl: String) { loading = false; canBack = view.canGoBack() }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) { failed = true; loading = false }
                            }
                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                if (request.isForMainFrame && errorResponse.statusCode >= 400) loading = false
                            }
                        }
                        if (ReaderNavigation.allowed(url)) loadUrl(url) else { failed = true; loading = false }
                    }
                })
            }
        }
    }
}
