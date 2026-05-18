package com.stereo.bluemusic.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.view.KeyEvent

class MediaSessionBridgeService : Service() {
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun service(): MediaSessionBridgeService = this@MediaSessionBridgeService
    }

    private fun sendMediaKey(keyCode: Int): Boolean = runCatching {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        true
    }.getOrDefault(false)

    fun playPause(): Boolean = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun next(): Boolean = sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous(): Boolean = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    override fun onBind(intent: Intent?): IBinder = binder
}
