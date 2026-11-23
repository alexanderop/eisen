package io.github.alexanderop.eisen.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import io.github.alexanderop.eisen.domain.SharedExercise
import io.github.alexanderop.eisen.domain.setText
import io.github.alexanderop.eisen.domain.SharedWorkout
import io.github.alexanderop.eisen.domain.loadText
import io.github.alexanderop.eisen.domain.sharedStats
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The share card of the PWA (`apps/web/src/shareImage.ts`), drawn the same way: same size,
 * same layout, same colours. Numbers are in pixels of the picture, not dp.
 */

private object Palette {
    const val BACKGROUND = 0xFF0B0C0E.toInt()
    const val CARD = 0xFF15171B.toInt()
    const val BORDER = 0xFF2B2F37.toInt()
    const val FOREGROUND = 0xFFF4F5F7.toInt()
    const val MUTED = 0xFF8D939F.toInt()
    const val PRIMARY = 0xFFC8F031.toInt()
    const val ON_PRIMARY = 0xFF11140A.toInt()
    const val WARN = 0xFFFFB020.toInt()
}

private fun rgba(red: Int, green: Int, blue: Int, alpha: Double): Int = android.graphics.Color.argb(Math.round(alpha * 255).toInt(), red, green, blue)

private fun lime(alpha: Double): Int = rgba(200, 240, 49, alpha)

private const val WIDTH = 1080
/** 4:5, the tallest picture a feed shows uncropped. Longer workouts make it taller. */
private const val MIN_HEIGHT = 1350
private const val PAD = 72f
private const val INNER = WIDTH - PAD * 2

private const val HEADER = 452f
private const val STATS = 176f
private const val ROW = 204f
private const val ROW_GAP = 20f
private const val FOOTER = 132f
private const val PILL = 76f
private const val PILL_GAP = 12f

/** Roboto in the weights the card uses. Before Android 9 a typeface has only regular and bold. */
private fun typeface(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, weight, false)
    else if (weight >= 600) Typeface.DEFAULT_BOLD
    else Typeface.DEFAULT

/** A canvas with the drawing helpers of the web version: one paint, set up per call. */
private class Card(val canvas: Canvas) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /** [spacing] is in pixels, like the canvas `letterSpacing` of the web version. */
    fun font(weight: Int, size: Float, spacing: Float = 0f) {
        paint.typeface = typeface(weight)
        paint.textSize = size
        paint.letterSpacing = spacing / size
    }

    fun measure(text: String): Float = paint.measureText(text)

    /** Cuts a text that is too wide and ends it with an ellipsis. */
    fun fit(text: String, width: Float): String {
        if (measure(text) <= width) return text
        var cut = text
        while (cut.length > 1 && measure("$cut…") > width) cut = cut.dropLast(1)
        return "${cut.trimEnd()}…"
    }

    fun text(text: String, x: Float, y: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        paint.textAlign = align
        canvas.drawText(text, x, y, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /** The canvas `textBaseline = 'middle'`: [y] is the middle of the text, not its baseline. */
    fun middleText(text: String, x: Float, y: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        val metrics = paint.fontMetrics
        text(text, x, y - (metrics.ascent + metrics.descent) / 2, color, align)
    }

    fun fill(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        rect.set(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    fun stroke(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int, line: Float, dashed: Boolean = false) {
        paint.style = Paint.Style.STROKE
        paint.shader = null
        paint.color = color
        paint.strokeWidth = line
        paint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(9f, 8f), 0f) else null
        rect.set(x, y, x + width, y + height)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
    }
}

/** The barbell from the app icon. */
private fun Card.barbell(x: Float, y: Float, size: Float, color: Int) {
    val unit = size / 264
    fun part(left: Float, top: Float, width: Float, height: Float, radius: Float) =
        fill(x + left * unit, y + top * unit, width * unit, height * unit, radius * unit, color)
    part(22f, 68f, 220f, 24f, 6f)
    part(42f, 0f, 34f, 160f, 10f)
    part(188f, 0f, 34f, 160f, 10f)
    part(0f, 28f, 30f, 104f, 10f)
    part(234f, 28f, 30f, 104f, 10f)
}

private fun Card.background(height: Int) {
    canvas.drawColor(Palette.BACKGROUND)

    paint.style = Paint.Style.FILL
    paint.shader = RadialGradient(
        WIDTH - 60f, -40f, 900f,
        intArrayOf(lime(0.30), lime(0.07), lime(0.0)),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, WIDTH.toFloat(), height.toFloat(), paint)
    paint.shader = null

    // A barbell far too big for the picture, barely there, behind the headline.
    canvas.save()
    canvas.translate(WIDTH - 250f, 210f)
    canvas.rotate(Math.toDegrees(-0.21).toFloat())
    barbell(-390f, -236f, 780f, lime(0.05))
    canvas.restore()
}

private fun Card.header(shared: SharedWorkout, date: String) {
    barbell(PAD, 80f, 66f, Palette.PRIMARY)
    font(800, 30f, spacing = 5f)
    middleText("EISEN", PAD + 88, 102f, Palette.PRIMARY)

    font(700, 30f, spacing = 4f)
    text("WORKOUT DONE", PAD, 250f, Palette.MUTED)

    var size = 116f
    font(800, size)
    while (size > 68 && measure(shared.name) > INNER) {
        size -= 4
        font(800, size)
    }
    text(fit(shared.name, INNER), PAD - 4, 364f, Palette.FOREGROUND)

    font(500, 38f)
    text(if (shared.minutes == null) date else "$date  ·  ${shared.minutes} min", PAD, 426f, Palette.MUTED)
}

private class Tile(val value: String, val unit: String, val label: String)

private fun Card.stats(shared: SharedWorkout, top: Float) {
    val (volume, sets, reps) = sharedStats(shared)
    val tiles = buildList {
        // JavaScript's Math.round and toLocaleString('en-US'): 3,120.
        if (volume > 0) add(Tile(String.format(Locale.US, "%,d", floor(volume + 0.5).toLong()), shared.unit.label, "VOLUME"))
        add(Tile(sets.toString(), "", "SETS"))
        // Holds are no reps: a workout of only holds shows its time instead.
        val showReps = reps > 0 || shared.exercises.none { it.timed }
        if (showReps) add(Tile(reps.toString(), "", "REPS"))
        if ((volume <= 0 || !showReps) && shared.minutes != null) add(Tile(shared.minutes.toString(), "min", "TIME"))
    }
    val gap = 16f
    // The volume is the longest number, so its tile is the widest.
    val weights = tiles.map { if (it.label == "VOLUME") 1.5f else 1f }
    val total = weights.sum()
    var x = PAD
    tiles.forEachIndexed { index, tile ->
        val width = (INNER - gap * (tiles.size - 1)) * weights[index] / total
        fill(x, top, width, 148f, 28f, Palette.CARD)
        stroke(x, top, width, 148f, 28f, Palette.BORDER, 2f)

        font(800, 58f)
        text(tile.value, x + 28, top + 78, Palette.FOREGROUND)
        if (tile.unit.isNotEmpty()) {
            val offset = measure(tile.value)
            font(700, 30f)
            text(tile.unit, x + 28 + offset + 8, top + 78, Palette.MUTED)
        }
        font(700, 22f, spacing = 3f)
        text(tile.label, x + 28, top + 120, Palette.MUTED)
        x += width + gap
    }
}

private fun Card.badgeWidth(): Float {
    font(800, 22f, spacing = 2f)
    return measure("RECORD") + 34
}

private fun Card.badge(x: Float, y: Float) {
    val width = badgeWidth()
    fill(x, y - 31, width, 42f, 21f, Palette.PRIMARY)
    text("RECORD", x + 18, y - 2, Palette.ON_PRIMARY)
}

private fun Card.pill(exercise: SharedExercise, reps: Int?, x: Float, y: Float) {
    val target = exercise.targetReps
    val missed = reps != null && target != null && reps < target
    when {
        reps == null -> stroke(x, y, PILL, PILL, 22f, Palette.BORDER, 3f, dashed = true)
        missed -> {
            fill(x, y, PILL, PILL, 22f, rgba(255, 176, 32, 0.14))
            stroke(x, y, PILL, PILL, 22f, Palette.WARN, 3f)
        }
        else -> fill(x, y, PILL, PILL, 22f, Palette.PRIMARY)
    }
    val color = if (reps == null) Palette.MUTED else if (missed) Palette.WARN else Palette.ON_PRIMARY
    val label = reps?.let { setText(it, exercise.timed) } ?: "–"
    // A hold such as "1:30" is wider than a count of reps: it gets a smaller font, not a wider pill.
    var size = 36f
    font(800, size)
    while (size > 20 && measure(label) > PILL - 14) {
        size -= 2
        font(800, size)
    }
    middleText(label, x + PILL / 2, y + PILL / 2 + 2, color, Paint.Align.CENTER)
}

private fun Card.exerciseRow(shared: SharedWorkout, exercise: SharedExercise, top: Float) {
    fill(PAD, top, INNER, ROW, 32f, Palette.CARD)
    stroke(PAD, top, INNER, ROW, 32f, Palette.BORDER, 2f)

    val left = PAD + 32
    val right = PAD + INNER - 32
    val baseline = top + 76

    val load = loadText(exercise.bodyweight, exercise.weight, shared.unit)
    // Plain bodyweight carries no number worth the accent colour; an added weight does.
    val plain = exercise.bodyweight && exercise.weight == 0.0
    font(800, 48f)
    text(load, right, baseline, if (plain) Palette.MUTED else Palette.PRIMARY, Paint.Align.RIGHT)
    val loadWidth = measure(load)

    val badgeSpace = if (exercise.record) badgeWidth() + 18 else 0f

    font(700, 42f)
    val name = fit(exercise.name, right - left - loadWidth - 28 - badgeSpace)
    text(name, left, baseline, Palette.FOREGROUND)
    if (exercise.record) badge(left + measure(name) + 18, baseline)

    exercise.sets.forEachIndexed { index, reps ->
        pill(exercise, reps, left + index * (PILL + PILL_GAP), top + 100)
    }
}

private fun Card.footer(height: Int, site: String) {
    val y = height - 64f
    font(600, 28f)
    text("Tracked with Eisen 5×5", PAD, y, Palette.MUTED)
    text(fit(site, 480f), WIDTH - PAD, y, Palette.MUTED, Paint.Align.RIGHT)
}

/**
 * Draws a finished workout as a picture to post or send.
 * [date] is already written out, [site] is where the app lives, shown in the corner.
 */
fun drawWorkoutImage(shared: SharedWorkout, date: String, site: String): Bitmap {
    val rows = shared.exercises.size
    val content = HEADER + 40 + STATS + rows * (ROW + ROW_GAP) + FOOTER
    val height = max(MIN_HEIGHT, content.toInt())
    val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
    val card = Card(Canvas(bitmap))

    card.background(height)
    card.header(shared, date)
    // A short workout is spread over the picture instead of leaving the bottom empty.
    val spare = min(height - content, 120f)
    val statsTop = HEADER + 40 + spare / 2
    card.stats(shared, statsTop)
    val listTop = statsTop + STATS + spare / 2
    shared.exercises.forEachIndexed { index, exercise ->
        card.exerciseRow(shared, exercise, listTop + index * (ROW + ROW_GAP))
    }
    card.footer(height, site)
    return bitmap
}
