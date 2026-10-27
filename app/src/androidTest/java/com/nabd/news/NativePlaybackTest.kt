package com.nabd.news

import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Synthetic motion/tone asset tests decoding only, never displayed as news or proof of live platform access. */
class NativePlaybackTest {
    @get:Rule val compose=createComposeRule()
    @Test fun rendersFramesDecodesAudioAndChangesMuteWithoutReloadOrSeeking() {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".qa")){"Run with -PqaBuild=true; never target the personal database"}
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=java.io.File(instrumentation.targetContext.cacheDir,"qa-motion-tone.mp4")
        instrumentation.context.assets.open("playback/qa-motion-tone.mp4").use{input->file.outputStream().use{input.copyTo(it)}}
        val error=AtomicReference<PlaybackException?>();var player:ExoPlayer?=null;var creations=0;var surface:SurfaceView?=null
        var muted by mutableStateOf(false)
        compose.setContent {
            var native by remember{mutableStateOf<ExoPlayer?>(null)}
            DisposableEffect(Unit){onDispose{native?.release()}}
            LaunchedEffect(native,muted){native?.volume=if(muted)0f else 1f}
            MediaPlayerFrame(false,{}) {
            AndroidView(modifier=Modifier.fillMaxSize(),factory={ctx->
                createNabdNativePlayer(ctx,file.toURI().toString(),false,true,object:Player.Listener{
                    override fun onPlayerError(e:PlaybackException){error.set(e)}
                }).also{view->
                    assertTrue(view.videoSurfaceView is SurfaceView);surface=view.videoSurfaceView as SurfaceView
                    creations++;player=view.player as ExoPlayer;native=player
                }
            })
            }
        }
        compose.waitUntil(20000){compose.runOnIdle{
            error.get()?.let{throw AssertionError("Decoder failure",it)}
            (player?.videoDecoderCounters?.renderedOutputBufferCount?:0)>=3 && (player?.audioDecoderCounters?.renderedOutputBufferCount?:0)>0
        }}
        val engine=player!!
        // Decoder counters can advance while a Compose/dialog surface remains black or frozen.
        // Capture the actual compositor output, within the video surface bounds (no controls).
        fun displayedPixels():List<Int> {
            val bounds=compose.runOnIdle { val v=surface!!;val xy=IntArray(2);v.getLocationOnScreen(xy);intArrayOf(xy[0],xy[1],v.width,v.height) }
            val shot=instrumentation.uiAutomation.takeScreenshot()?:error("Cannot capture displayed video")
            return try {(1..24).flatMap { x -> (1..12).map { y ->
                shot.getPixel((bounds[0]+bounds[2]*x/25).coerceIn(0,shot.width-1),(bounds[1]+bounds[3]*(y+3)/19).coerceIn(0,shot.height-1))
            } }}finally{shot.recycle()}
        }
        val first=displayedPixels()
        assertTrue("Actual video surface is blank",first.toSet().size>8)
        var changed=false
        repeat(3) {
            android.os.SystemClock.sleep(350)
            val next=displayedPixels()
            if(first.zip(next).count{(a,b)->a!=b}>8)changed=true
        }
        assertTrue("Frames decoded but displayed surface is frozen",changed)
        val initial=compose.runOnIdle{assertEquals(1f,engine.volume,0f);engine.currentPosition}
        compose.runOnIdle{muted=true}
        compose.waitUntil(5000){compose.runOnIdle{engine.volume==0f&&engine.currentPosition>initial+200}}
        val mutedPosition=compose.runOnIdle{engine.currentPosition}
        compose.runOnIdle{muted=false}
        compose.waitUntil(5000){compose.runOnIdle{engine.volume==1f&&engine.currentPosition>mutedPosition+200}}
        compose.runOnIdle{assertSame(engine,player);assertEquals(1,creations);assertTrue(engine.videoSize.width>0);assertNull(error.get())}
    }
}
