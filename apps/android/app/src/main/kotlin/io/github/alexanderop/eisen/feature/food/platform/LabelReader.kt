package io.github.alexanderop.eisen.feature.food.platform

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.alexanderop.eisen.feature.food.LabelLine
import io.github.alexanderop.eisen.feature.food.LabelText
import io.github.alexanderop.eisen.feature.food.LabelWord
import io.github.alexanderop.eisen.feature.food.TextBox
import java.io.File
import java.util.concurrent.Executors

/**
 * Reads the text on a photo of a label with ML Kit's text recognition, bundled in the app: on the phone, without a
 * download, an account or a network. Nothing of the photo is kept: the camera's file is deleted once it is read, a picked
 * photo is only opened. What the numbers mean is decided by `readLabel` in the program, not here.
 */
object LabelReader {
    /**
     * The longest side a photo is read at. The labels measured for this (Open Food Facts' photos, up to 4032 px) read as
     * well at 2048 px; the recognizer takes 30 to 120 ms on the emulator then, and a 12-megapixel photo is not held in
     * memory whole.
     */
    private const val MAX_SIDE = 2048

    private val work = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The file the camera app writes the photo to, in the app's cache; one at a time. */
    private fun cameraFile(context: Context): File = File(File(context.cacheDir, "labels").apply { mkdirs() }, "label.jpg")

    /** Where the camera app may write the photo: shared with it by the app's FileProvider, for this one photo. */
    fun cameraUri(context: Context): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", cameraFile(context))

    /** [cameraUri] with no photo of an earlier time in it. */
    fun newCameraUri(context: Context): Uri = cameraFile(context).let { it.delete(); cameraUri(context) }

    /** Reads the photo at [uri]; [done] gets the text on the main thread, or null when the photo could not be read. */
    fun read(context: Context, uri: Uri, done: (LabelText?) -> Unit) {
        val app = context.applicationContext
        work.execute {
            val started = System.currentTimeMillis()
            val bitmap = try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { decoder, info, _ ->
                    // The recognizer reads the pixels: no bitmap in graphics memory.
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val longest = maxOf(width, height)
                    if (longest > MAX_SIDE) decoder.setTargetSize(width * MAX_SIDE / longest, height * MAX_SIDE / longest)
                }
            } catch (error: Exception) {
                Log.w(TAG, "label photo could not be opened", error)
                finish(app) { done(null) }
                return@execute
            }
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener(work) { text ->
                        val label = labelText(text)
                        Log.i(TAG, "label photo read: ${bitmap.width}x${bitmap.height}, ${label.lines.size} lines in ${System.currentTimeMillis() - started} ms")
                        bitmap.recycle()
                        finish(app) { done(label) }
                    }
                    .addOnFailureListener(work) { error ->
                        Log.w(TAG, "label photo could not be read", error)
                        bitmap.recycle()
                        finish(app) { done(null) }
                    }
            } catch (error: RuntimeException) {
                Log.w(TAG, "text recognition could not start", error)
                bitmap.recycle()
                finish(app) { done(null) }
            }
        }
    }

    /** The camera's photo goes, read or not; the answer goes to the main thread. */
    private fun finish(context: Context, answer: () -> Unit) {
        cameraFile(context).delete()
        main.post(answer)
    }

    /** Throws the camera's photo away, when the camera app was left without one or the app was not there to read it. */
    fun discard(context: Context) {
        work.execute { cameraFile(context.applicationContext).delete() }
    }

    private fun labelText(text: Text): LabelText = LabelText(
        text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                LabelLine(
                    line.text,
                    TextBox(box.left, box.top, box.right, box.bottom),
                    line.angle.toDouble(),
                    line.elements.mapNotNull { element -> element.boundingBox?.let { LabelWord(element.text, TextBox(it.left, it.top, it.right, it.bottom)) } },
                )
            }
        },
    )

    private const val TAG = "EisenFood"
}
