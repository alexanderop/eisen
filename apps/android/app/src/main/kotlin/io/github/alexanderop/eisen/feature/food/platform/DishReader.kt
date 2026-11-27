package io.github.alexanderop.eisen.feature.food.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import io.github.alexanderop.eisen.BuildConfig
import io.github.alexanderop.eisen.feature.food.DISH_PROMPT
import io.github.alexanderop.eisen.feature.food.DishAi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shows a photo of a dish to Gemini Nano, the model Android keeps on some phones (AICore), through ML Kit's Prompt API:
 * on the phone, without an account or a network. The model is the system's: the app only asks whether it is there, has
 * Android download it, and sends it a photo with [DISH_PROMPT]. Nothing of the photo is kept. What the answer means is
 * decided by `readDish` in the program, not here.
 *
 * A debug build answers with the text of `files/dish-ai.txt` when that file is there, without asking a model: the emulator
 * has none, and the journeys of the verify-eisen skill put the file there to walk the screens. A release build never
 * reads it.
 */
object DishReader {
    /** The longest side the model is shown: enough to tell foods apart, and a 12-megapixel photo is not held in memory whole. */
    private const val MAX_SIDE = 1024

    /** Enough for [io.github.alexanderop.eisen.feature.food.MAX_DISH_ITEMS] lines of "name; grams". */
    private const val MAX_OUTPUT_TOKENS = 256

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val model by lazy { Generation.getClient() }

    private fun canned(context: Context): String? =
        if (!BuildConfig.DEBUG) null else runCatching { File(context.filesDir, "dish-ai.txt").takeIf { it.isFile }?.readText() }.getOrNull()

    /** Whether the phone has the model; [done] gets it on the main thread. A phone that cannot say has none. */
    fun check(context: Context, done: (DishAi) -> Unit) {
        val app = context.applicationContext
        work.launch {
            val status = if (canned(app) != null) DishAi.READY else try {
                when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> DishAi.READY
                    FeatureStatus.DOWNLOADABLE -> DishAi.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> DishAi.DOWNLOADING
                    else -> DishAi.MISSING
                }
            } catch (error: Exception) {
                Log.i(TAG, "dish model: not on this phone (${error.message})")
                DishAi.MISSING
            }
            Log.i(TAG, "dish model: $status")
            main.post { done(status) }
        }
    }

    /** Has Android download the model; [done] gets whether it is there now, on the main thread. */
    fun download(done: (Boolean) -> Unit) {
        work.launch {
            var answered = false
            fun answer(ready: Boolean) {
                if (answered) return
                answered = true
                main.post { done(ready) }
            }
            try {
                model.download().collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadCompleted -> answer(true)
                        is DownloadStatus.DownloadFailed -> {
                            Log.w(TAG, "dish model: download failed", status.e)
                            answer(false)
                        }
                        else -> Unit
                    }
                }
                // A download that ends without saying how: the phone says whether the model is there.
                answer(runCatching { model.checkStatus() == FeatureStatus.AVAILABLE }.getOrDefault(false))
            } catch (error: Exception) {
                Log.w(TAG, "dish model: download failed", error)
                answer(false)
            }
        }
    }

    /**
     * Shows the photo at [uri] to the model. On the main thread [done] gets what it wrote, [unreadable] is called when the
     * photo could not be opened, and [failed] when the model did not answer.
     */
    fun read(context: Context, uri: Uri, done: (String) -> Unit, unreadable: () -> Unit, failed: () -> Unit) {
        val app = context.applicationContext
        work.launch {
            val started = System.currentTimeMillis()
            val bitmap = try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { decoder, info, _ ->
                    // The model reads the pixels: no bitmap in graphics memory.
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > MAX_SIDE) decoder.setTargetSize(info.size.width * MAX_SIDE / longest, info.size.height * MAX_SIDE / longest)
                }
            } catch (error: Exception) {
                Log.w(TAG, "dish photo could not be opened", error)
                LabelReader.discard(app)
                main.post(unreadable)
                return@launch
            }
            val answer = try {
                canned(app) ?: ask(bitmap)
            } catch (error: Exception) {
                Log.w(TAG, "dish model did not answer", error)
                null
            }
            Log.i(TAG, "dish photo read: ${bitmap.width}x${bitmap.height}, ${answer?.lines()?.size ?: 0} lines in ${System.currentTimeMillis() - started} ms")
            if (BuildConfig.DEBUG && answer != null) Log.d(TAG, "dish model wrote: $answer")
            bitmap.recycle()
            LabelReader.discard(app)
            main.post { if (answer != null) done(answer) else failed() }
        }
    }

    private suspend fun ask(photo: Bitmap): String? {
        val request = generateContentRequest(ImagePart(photo), TextPart(DISH_PROMPT)) {
            // Low: the same photo should give the same list.
            temperature = 0.2f
            maxOutputTokens = MAX_OUTPUT_TOKENS
        }
        return model.generateContent(request).candidates.firstOrNull()?.text
    }

    private const val TAG = "EisenFood"
}
