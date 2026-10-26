package com.nabd.news

import android.annotation.SuppressLint
import android.view.View
import android.webkit.*
import android.view.LayoutInflater
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Official remote embeds only. No JS interfaces, extraction, TLS bypass or automatic external intents. */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun MediaPlayer(requestedTarget: MediaTarget, title: String, close: () -> Unit, embedded: Boolean = false, autoPlay: Boolean = false, muted: Boolean = true, ended: () -> Unit = {}) {
    if(!MediaPolicy.validPlayer(requestedTarget)) { Text("رابط مشغل غير صالح"); return }
    val normalized = if(requestedTarget.provider == "X") MediaTarget("X",requireNotNull(MediaPolicy.xLink(requestedTarget.url))) else requireNotNull(MediaPolicy.target(requestedTarget.url))
    val target = normalized
    val latestAuto by rememberUpdatedState(autoPlay)
    val latestMuted by rememberUpdatedState(muted)
    val latestEnded by rememberUpdatedState(ended)
    val latestClose by rememberUpdatedState(close)
    var web by remember { mutableStateOf<WebView?>(null) }
    var native by remember { mutableStateOf<ExoPlayer?>(null) }
    var full by remember { mutableStateOf<View?>(null) }
    var fullCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failureReason by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_PAUSE) { web?.evaluateJavascript(MediaEmbedPlayback.command(false,latestMuted),null); web?.onPause(); native?.pause() }
            if(event == Lifecycle.Event.ON_RESUME) { web?.onResume();web?.evaluateJavascript(MediaEmbedPlayback.command(latestAuto,latestMuted),null);native?.playWhenReady=latestAuto }
            if(event == Lifecycle.Event.ON_STOP) latestClose() // No background playback; disposal destroys the remote player.
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); native?.release(); web?.apply { stopLoading(); onPause(); removeAllViews(); destroy() }; fullCallback?.onCustomViewHidden() }
    }
    LaunchedEffect(native,muted){native?.volume=if(muted)0f else 1f}
    LaunchedEffect(native,autoPlay){native?.playWhenReady=autoPlay}
    LaunchedEffect(web,autoPlay,muted){if(target.provider in MediaEmbedPlayback.controllable)web?.evaluateJavascript(MediaEmbedPlayback.command(autoPlay,muted),null)}
    MediaPlayerFrame(embedded, close) {
        BackHandler { if(full != null) { fullCallback?.onCustomViewHidden(); full=null } else close() }
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column {
                if(!embedded) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = close) { Text("العودة إلى نبض") }
                    TextButton(onClick = { failed=false; loading=true; if(native!=null) native?.apply{seekTo(0);prepare();playWhenReady=latestAuto} else web?.reload() }) { Text("إعادة التحميل") }
                }
                if(!embedded) Text(title, Modifier.padding(horizontal=16.dp), maxLines=2)
                if(!embedded) Caption("${target.provider} • مشغل المصدر داخل نبض")
                if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(failed) Text("تعذر التشغيل من المصدر؛ قد يكون المحتوى محذوفًا أو محميًا أو غير متاح للتضمين.", Modifier.padding(16.dp), color=Accent)
                if(failed && failureReason.isNotBlank()) Caption(failureReason)
                if(target.provider == "X") Caption("قد يمنع X العرض أو يطلب تسجيل الدخول. لا يضمن التضمين ترتيب الحساب كاملًا. إذا بقي رابط فقط فلم ينجح تحميل التضمين.")
                Box(Modifier.fillMaxWidth().weight(1f)) {
                if(target.provider == "فيديو مباشر") AndroidView(modifier=Modifier.fillMaxSize(), factory={ ctx ->
                    createNabdNativePlayer(ctx,target.url,latestMuted,latestAuto,object:Player.Listener {
                        override fun onPlaybackStateChanged(state:Int){loading=state==Player.STATE_BUFFERING;if(state==Player.STATE_ENDED)latestEnded()}
                        override fun onPlayerError(error:PlaybackException){loading=false;failed=true;failureReason="تعذر فك الترميز أو جلب الفيديو • ${error.errorCodeName}"}
                    }).also{native=it.player as ExoPlayer}
                },update={view->view.keepScreenOn=autoPlay})
                else AndroidView(modifier=Modifier.fillMaxSize(), factory={ ctx ->
                    WebView(ctx).apply {
                        web=this
                        settings.javaScriptEnabled=true
                        settings.domStorageEnabled=true
                        settings.allowFileAccess=false; settings.allowContentAccess=false
                        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled=true
                        settings.mediaPlaybackRequiresUserGesture=false // Opening this viewer is an explicit playback action. Providers may still require a tap.
                        setLayerType(View.LAYER_TYPE_HARDWARE,null)
                        settings.javaScriptCanOpenWindowsAutomatically=false
                        settings.setSupportMultipleWindows(false)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
                        webChromeClient=object: WebChromeClient() {
                            override fun onShowCustomView(view:View, callback:CustomViewCallback) { fullCallback=callback; full=view }
                            override fun onHideCustomView() { full=null; fullCallback?.onCustomViewHidden(); fullCallback=null }
                        }
                        webViewClient=object: WebViewClient() {
                            override fun shouldOverrideUrlLoading(view:WebView, request:WebResourceRequest):Boolean {
                                if(!request.url.toString().startsWith("https://")) return true
                                if(!request.isForMainFrame) return false
                                if(request.hasGesture()) return false // Explicit HTTPS links stay inside this WebView; preserve provider links.
                                // Block automatic main-frame navigation away from a player. Never launch other apps.
                                val url=request.url.toString().toHttpUrlOrNull() ?: return true
                                return !(url.host == "com.nabd.news" || url.host == "www.youtube.com" && url.encodedPath.startsWith("/embed/") || url.host == "player.vimeo.com" && url.encodedPath.startsWith("/video/") || url.host == "www.instagram.com" && url.encodedPath.endsWith("/embed/") || url.host == "www.tiktok.com" && url.encodedPath.startsWith("/player/v1/"))
                            }
                            override fun onPageFinished(view:WebView, url:String) { loading=false;if(target.provider in MediaEmbedPlayback.controllable)view.evaluateJavascript(MediaEmbedPlayback.command(latestAuto,latestMuted),null) }
                            override fun onReceivedError(view:WebView, request:WebResourceRequest,error:WebResourceError) { if(request.isForMainFrame){failed=true;loading=false} }
                            override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) { if(request.isForMainFrame && response.statusCode>=400){failed=true;loading=false} }
                        }
                        if(target.provider == "X") loadDataWithBaseURL("https://${ctx.packageName}/",MediaPolicy.embedHtml(target),"text/html","UTF-8",null)
                        else if(target.provider in MediaEmbedPlayback.controllable) loadDataWithBaseURL("https://${ctx.packageName}/",MediaEmbedPlayback.html(target,"https://${ctx.packageName}/",latestAuto,latestMuted),"text/html","UTF-8",null)
                        else loadUrl(target.url,mapOf("Referer" to "https://${ctx.packageName}/"))
                    }
                })
                if(full != null) AndroidView(factory = { full!! }, modifier=Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable private fun MediaPlayerFrame(embedded:Boolean, close:()->Unit, content:@Composable ()->Unit) {
    if(embedded) content() else Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=false)) { content() }
}
