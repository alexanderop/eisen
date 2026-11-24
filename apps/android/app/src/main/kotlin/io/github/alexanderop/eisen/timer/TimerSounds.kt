package io.github.alexanderop.eisen.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * The sounds of the interval timers: two synthesised beeps and the recordings in `voice/timer`, the
 * PWA's files. Both play on the media stream as spoken guidance. A recording asks for transient audio
 * focus that lets music duck under it, and gives it back when the line ends. Beeps take no focus.
 */
object TimerSounds {
    private const val TAG = "EisenTimer"
    private const val RATE = 44_100

    private val main = Handler(Looper.getMainLooper())

    private val voice = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val tone = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** The short tick before a change of phase and the long one on it. */
    private val beep by lazy { sine(hertz = 880.0, millis = 120) }
    private val beepLong by lazy { sine(hertz = 1320.0, millis = 500) }

    private var app: Context? = null
    private var line: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    /** [fallback] plays instead when [sound] is a line that is not recorded. */
    fun play(context: Context, sound: String, fallback: String? = null) {
        when (sound) {
            "beep" -> tone(sound, beep)
            "beep-long" -> tone(sound, beepLong)
            else -> if (!say(context, sound) && fallback != null) play(context, fallback)
        }
    }

    /** Sounds one after the other, each the given milliseconds from now. A new call drops what an earlier one still had to say. */
    fun playAll(context: Context, sounds: List<Pair<Long, String>>, fallback: String? = null) {
        main.removeCallbacksAndMessages(SEQUENCE)
        for ((after, sound) in sounds) main.postAtTime({ play(context, sound, fallback) }, SEQUENCE, android.os.SystemClock.uptimeMillis() + after)
    }

    private val SEQUENCE = Any()

    /** Silences a line that is being said: a pause or the end of a run. */
    fun stop() {
        line?.let { runCatching { it.stop() } }
        finish()
    }

    /** False when there is no recording of [name]. */
    private fun say(context: Context, name: String): Boolean {
        // One line at a time. The schedule never overlaps them, a sample tapped twice might.
        stop()
        app = context.applicationContext
        val asset = "voice/timer/$name.mp3"
        val player = MediaPlayer()
        try {
            context.assets.openFd(asset).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        } catch (_: Exception) {
            // Not recorded (yet): the timer goes on without it.
            player.release()
            Log.d(TAG, "skipped $name: no recording")
            return false
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(voice).build()
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.d(TAG, "focus for $name: ${if (granted) "granted" else "refused"}")
        focus = request
        line = player
        runCatching {
            player.setAudioAttributes(voice)
            player.setOnCompletionListener { if (line === it) finish() }
            player.setOnErrorListener { failed, _, _ ->
                if (line === failed) finish()
                true
            }
            player.prepare()
            player.start()
            Log.d(TAG, "said $name")
        }.onFailure {
            Log.d(TAG, "could not say $name", it)
            finish()
        }
        return true
    }

    /** Lets the player go and gives the audio focus back, so the music comes back up. */
    private fun finish() {
        line?.let { runCatching { it.release() } }
        line = null
        val request = focus ?: return
        focus = null
        app?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
        Log.d(TAG, "focus released")
    }

    private fun tone(name: String, samples: ShortArray) {
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(tone)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return
        runCatching {
            track.write(samples, 0, samples.size)
            track.play()
            Log.d(TAG, "played $name")
        }
        main.postDelayed({ runCatching { track.release() } }, samples.size * 1000L / RATE + 200)
    }

    /** A sine with a few milliseconds of fade at both ends, so it starts and stops without a click. */
    private fun sine(hertz: Double, millis: Int): ShortArray {
        val count = RATE * millis / 1000
        val fade = RATE * 8 / 1000
        return ShortArray(count) { index ->
            val edge = min(1.0, min(index, count - 1 - index).toDouble() / fade)
            (sin(2 * PI * hertz * index / RATE) * edge * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
