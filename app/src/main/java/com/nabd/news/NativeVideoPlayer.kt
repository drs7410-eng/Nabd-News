package com.nabd.news

import android.content.Context
import android.view.LayoutInflater
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Internal construction shared by the real viewer and decoder tests, never exposed as an Intent. */
internal fun createNabdNativePlayer(context:Context,url:String,muted:Boolean,autoplay:Boolean,listener:Player.Listener):PlayerView =
    (LayoutInflater.from(context).inflate(R.layout.nabd_video_player,null,false) as PlayerView).apply {
        val engine=ExoPlayer.Builder(context).build();player=engine
        engine.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
        engine.setHandleAudioBecomingNoisy(true);engine.volume=if(muted)0f else 1f
        engine.addListener(listener);engine.setMediaItem(MediaItem.fromUri(url));engine.playWhenReady=autoplay;engine.prepare()
    }
