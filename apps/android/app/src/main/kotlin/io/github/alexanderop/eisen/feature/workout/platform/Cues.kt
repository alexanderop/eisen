package io.github.alexanderop.eisen.feature.workout.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.alexanderop.eisen.app.elm.Cue

/**
 * The spoken cues of the rest timer, the same recordings the PWA plays, and the vibration at its end.
 * The hold timer beeps. Whether a cue plays at all (the sound setting) and whether it may beep instead
 * is decided in `update`.
 */
object Cues {
    /** The PWA's beep: short tones 220 ms apart. */
    private const val BEEP_MS = 160
    private const val BEEP_GAP_MS = 220L

    private val main = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        // Spoken guidance: music playing from another app is turned down for it, not stopped.
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Plays [cue]'s recording. Without the file, a cue that asks for it beeps instead. */
    fun play(context: Context, cue: Cue) = say(context, cue.asset, cue.beepWhenSilent)

    fun beep(times: Int) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() ?: return
        repeat(times) { index -> main.postDelayed({ tone.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS) }, index * BEEP_GAP_MS) }
        main.postDelayed({ tone.release() }, times * BEEP_GAP_MS + BEEP_MS)
    }

    fun vibrate(context: Context, pattern: List<Long>) {
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(pattern.toLongArray(), -1))
    }

    private fun say(context: Context, asset: String, beep: Boolean) {
        val audio = context.getSystemService(AudioManager::class.java)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
        audio.requestAudioFocus(focus)
        val player = MediaPlayer()
        try {
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
            // Not recorded, or the player failed before it could start: it is let go here, nobody else will.
            runCatching { player.release() }
            audio.abandonAudioFocusRequest(focus)
            if (beep) runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, FALLBACK_BEEP_MS)
                main.postDelayed({ tone.release() }, FALLBACK_BEEP_MS + 200L)
            }
        }
    }

    /** The beep a cue without its recording plays instead. */
    private const val FALLBACK_BEEP_MS = 600
}
