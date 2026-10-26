package com.nabd.news

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Session order is frozen. Only the settled page owns a player; off-screen pages show posters. */
@Composable fun ReelsScreen(vm:NabdViewModel, session:List<MediaClip>, start:Int, close:()->Unit) {
    val live by vm.clips.collectAsStateWithLifecycle()
    val pager=rememberPagerState(initialPage=start.coerceIn(0,(session.size-1).coerceAtLeast(0)),pageCount={session.size})
    val scope=rememberCoroutineScope()
    var muted by remember { mutableStateOf(vm.settings.get("reelsMuted","false")=="true") }
    var autoplay by remember { mutableStateOf(vm.settings.get("reelsAutoplay","true")=="true") }
    fun move(delta:Int) { val next=(pager.settledPage+delta).coerceIn(0,session.lastIndex);scope.launch{pager.animateScrollToPage(next)} }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler(onBack=close)
        Surface(Modifier.fillMaxSize(),color=Ink) {
            VerticalPager(state=pager,modifier=Modifier.fillMaxSize().safeDrawingPadding(),key={session[it].id},beyondViewportPageCount=0) { index ->
                val snapshot=session[index]
                val clip=live.firstOrNull{it.id==snapshot.id} ?: snapshot
                val focused=pager.settledPage==index
                val active=focused && !pager.isScrollInProgress
                DisposableEffect(clip.id,focused) {
                    val started=SystemClock.elapsedRealtime()
                    onDispose {
                        if(focused && vm.settings.get("mediaLearning","true")=="true") {
                            val elapsed=(SystemClock.elapsedRealtime()-started).coerceIn(0,120000)
                            if(elapsed>=500) vm.task { vm.dao.recordClipExposure(clip.id,elapsed,if(pager.settledPage!=index && elapsed<3000)1 else 0) }
                        }
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick=close){Icon(Icons.Outlined.Close,"إغلاق المشاهدة")}
                        Caption("${index+1} / ${session.size}",Mint)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick={autoplay=!autoplay;vm.settings.set("reelsAutoplay",autoplay.toString())}) { Icon(if(autoplay)Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,if(autoplay)"إيقاف التشغيل التلقائي" else "تفعيل التشغيل التلقائي") }
                        IconButton(enabled=clip.provider in setOf("YouTube","Vimeo","TikTok","فيديو مباشر"),onClick={muted=!muted;vm.settings.set("reelsMuted",muted.toString())}) { Icon(if(muted)Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,if(muted)"تشغيل الصوت" else "كتم الصوت") }
                    }
                    Text(clip.title,Modifier.padding(horizontal=20.dp,vertical=6.dp),style=MaterialTheme.typography.titleLarge,maxLines=3)
                    Text("${clip.source} • ${clip.topic} • ${clip.provider}",Modifier.padding(horizontal=20.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium,color=Mint)
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.weight(1f).fillMaxHeight(),contentAlignment=Alignment.Center) {
                            if(active) key(clip.id) {
                                MediaPlayer(MediaTarget(clip.provider,clip.playUrl),clip.title,close,embedded=true,autoPlay=autoplay,muted=muted,ended={if(active && pager.settledPage==index && !pager.isScrollInProgress && index<session.lastIndex && autoplay)move(1)})
                            } else if(clip.thumbnail.isNotBlank()) NewsImage(clip.thumbnail,clip.title)
                            else Icon(Icons.Outlined.PlayCircle,null,tint=Muted,modifier=Modifier.size(64.dp))
                        }
                        // A separate swipe rail preserves gestures even when a third-party iframe consumes touch events.
                        Box(Modifier.width(30.dp).fillMaxHeight().background(Panel).pointerInput(index) {
                            var drag=0f
                            detectVerticalDragGestures(onDragStart={drag=0f},onDragEnd={if(kotlin.math.abs(drag)>50.dp.toPx())move(if(drag<0)1 else -1)},onVerticalDrag={change,amount->change.consume();drag+=amount})
                        },contentAlignment=Alignment.Center){Icon(Icons.Outlined.UnfoldMore,"اسحب من الحافة للانتقال",tint=Muted)}
                    }
                    Caption(if(clip.provider in setOf("X","Instagram"))"اسحب من الحافة للتالي • التشغيل والصوت من أدوات المصدر" else "اسحب للأعلى للتالي • قد يطلب المصدر نقرة تشغيل")
                    Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                        IconButton(onClick={move(-1)},enabled=index>0){Icon(Icons.Outlined.KeyboardArrowUp,"المقطع السابق")}
                        IconButton(onClick={vm.task{vm.dao.likeClip(clip.id,!clip.liked)}}){Icon(if(clip.liked)Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"إعجاب",tint=if(clip.liked)Accent else Muted)}
                        IconButton(onClick={vm.task{vm.dao.favoriteClip(clip.id,!clip.favorite)}}){Icon(if(clip.favorite)Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,if(clip.favorite)"إزالة من مفضلة المقاطع" else "حفظ في مفضلة المقاطع",tint=Mint)}
                        IconButton(onClick={move(1)},enabled=index<session.lastIndex){Icon(Icons.Outlined.KeyboardArrowDown,"المقطع التالي")}
                    }
                }
            }
        }
    }
}
