package com.nabd.news

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Commands go only to the official iframe origin. No Android JavaScript interface or media extraction. */
object MediaEmbedPlayback {
    val controllable=setOf("YouTube","Vimeo","TikTok")
    fun html(target:MediaTarget,base:String,autoplay:Boolean,muted:Boolean):String {
        require(target.provider in controllable && MediaPolicy.validPlayer(target))
        val origin=target.url.toHttpUrlOrNull()!!.let{"https://${it.host}"}
        val url=target.url.toHttpUrlOrNull()!!.newBuilder().apply {
            setQueryParameter("autoplay",if(autoplay)"1"else"0")
            setQueryParameter(if(target.provider=="YouTube")"mute"else"muted",if(muted && target.provider!="TikTok")"1"else"0")
            if(target.provider=="YouTube"){setQueryParameter("enablejsapi","1");setQueryParameter("origin",base.removeSuffix("/"));setQueryParameter("playsinline","1")}
            if(target.provider=="Vimeo")setQueryParameter("playsinline","1")
        }.build()
        val json=Gson()
        return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;background:#080b10;height:100vh;overflow:hidden;display:flex;flex-direction:column">
<iframe id="player" title="مشغل الفيديو الأصلي" src="${org.jsoup.nodes.Entities.escape(url.toString())}" allow="autoplay; fullscreen; encrypted-media; picture-in-picture" allowfullscreen referrerpolicy="strict-origin-when-cross-origin" style="border:0;width:100%;flex:1;min-height:0"></iframe>
<button id="start" style="padding:12px;border-radius:16px;background:#efad56;color:#141414" onclick="nabdSetPlayback(true,false)">تشغيل الفيديو بالصوت</button>
<script>
const frame=document.getElementById('player'), provider=${json.toJson(target.provider)}, origin=${json.toJson(origin)};
let desiredPlay=$autoplay, desiredMute=$muted, youtube=null, vimeo=null;
function started(){document.getElementById('start').style.display='none';}
function blocked(){document.getElementById('start').style.display='block';}
function send(data){frame.contentWindow.postMessage(data,origin);}
function apply(){
 if(provider==='YouTube'&&youtube&&youtube.getPlayerState){if(desiredMute)youtube.mute();else youtube.unMute();if(desiredPlay)youtube.playVideo();else youtube.pauseVideo();}
 if(provider==='Vimeo'&&vimeo){vimeo.setVolume(desiredMute?0:1).catch(blocked);if(desiredPlay)vimeo.play().catch(blocked);else vimeo.pause().catch(()=>{});}
 if(provider==='TikTok'){send({type:desiredMute?'mute':'unMute','x-tiktok-player':true});send({type:desiredPlay?'play':'pause','x-tiktok-player':true});}
}
window.nabdSetPlayback=function(play,mute){desiredPlay=play;desiredMute=mute;apply();};
if(provider==='YouTube'){
 window.onYouTubeIframeAPIReady=()=>{youtube=new YT.Player('player',{events:{onReady:apply,onStateChange:e=>{if(e.data===1)started();},onAutoplayBlocked:blocked,onError:blocked}});};
 const script=document.createElement('script');script.src='https://www.youtube.com/iframe_api';document.head.appendChild(script);
}
if(provider==='Vimeo'){
 const script=document.createElement('script');script.src='https://player.vimeo.com/api/player.js';script.onload=()=>{vimeo=new Vimeo.Player(frame);vimeo.on('play',started);vimeo.on('error',blocked);vimeo.ready().then(apply).catch(blocked);};document.head.appendChild(script);
}
frame.addEventListener('load',()=>{if(provider==='TikTok')apply();});
window.addEventListener('message',e=>{
 if(e.origin!==origin||e.source!==frame.contentWindow)return;
 let d=e.data;try{if(typeof d==='string')d=JSON.parse(d);}catch(_){return;}if(!d)return;
 if(d.event==='onReady'||d.event==='ready'||d.type==='onPlayerReady')apply();
 if((d.event==='onStateChange'&&d.info===1)||(d.type==='onStateChange'&&d.value===1)||d.event==='play')started();
 if(d.event==='onAutoplayBlocked'||d.type==='onPlayerError')blocked();
});
</script></body></html>"""
    }
    fun command(play:Boolean,muted:Boolean)="window.nabdSetPlayback && window.nabdSetPlayback($play,$muted);"
}
