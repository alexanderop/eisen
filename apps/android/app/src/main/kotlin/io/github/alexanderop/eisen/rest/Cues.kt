package io.github.alexanderop.eisen.rest

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.alexanderop.eisen.data.Store

/** The spoken cues of the rest timer. The same recordings the PWA plays. */
object Cues {
    private val attributes = AudioAttributes.Builder()
        // Spoken guidance: music playing from another app is turned down for it, not stopped.
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun ten(context: Context) {
        if (Store.state.settings.sound) say(context, "voice/ten-seconds.mp3", fallback = false)
    }

    /** The end of a rest: the cue when sound is on, and the phone always shakes. */
    fun over(context: Context) {
        if (Store.state.settings.sound) say(context, "voice/rest-over.mp3", fallback = true)
        context.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 120, 300, 120, 300), -1))
    }

    /** Plays the end-of-rest cue once, so the lifter hears what switching it on does. */
    fun preview(context: Context) = say(context.applicationContext, "voice/rest-over.mp3", fallback = true)

    private fun say(context: Context, asset: String, fallback: Boolean) {
        val audio = context.getSystemService(AudioManager::class.java)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
        audio.requestAudioFocus(focus)
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(attributes)
            context.assets.openFd(asset).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            player.setOnCompletionListener {
                it.release()
                audio.abandonAudioFocusRequest(focus)
            }
            player.setOnErrorListener { failed, _, _ ->
                failed.release()
                audio.abandonAudioFocusRequest(focus)
                true
            }
            player.prepare()
            player.start()
        } catch (_: Exception) {
            // No voice file: the end of the rest falls back to a beep.
            audio.abandonAudioFocusRequest(focus)
            if (fallback) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP2, 600) }
        }
    }
}
