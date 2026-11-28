package io.github.alexanderop.eisen.screens

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.rules.ActivityScenarioRule
import io.github.alexanderop.eisen.MainActivity
import io.github.alexanderop.eisen.app.runtime.Eisen
import io.github.alexanderop.eisen.app.state.Loaded
import io.github.alexanderop.eisen.app.state.encodeState
import io.github.alexanderop.eisen.app.state.loadState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowMediaPlayer
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/*
 * The screen tests: the real MainActivity, the real update and the real saved state, on the JVM under Robolectric. No
 * emulator, no adb, no screen dumps: a flow takes a second or two. What only a phone can show (the camera app, the
 * notifications, the lock screen, Gemini Nano, the release build under R8) stays with eisenctl's journeys.
 *
 * The clock is Robolectric's: `robolectric.properties` in this package instruments the app's classes, so the app's
 * `System.currentTimeMillis()` reads the same fake clock as the main looper. Every test starts at [NOW] in Berlin, and
 * [later] moves the clock and everything posted on the main thread with it: a rest or an interval timer runs in no time.
 */

/** The fixtures eisenctl starts the emulator with (`launch --state <name>`); unit tests run in `apps/android/app`. */
private val FIXTURES = File("../../../.claude/skills/verify-eisen/fixtures")

/** The saved answers of Open Food Facts the unit tests read (`off/product/<barcode>.json`, `off/search/<query>.json`). */
private val ANSWERS = File("src/test/resources/off")

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/**
 * When every screen test runs: Wednesday, 25 November 2026, 10:00 in Berlin. Every fixture fits it the way eisenctl moves
 * it to the emulator's today: the `lifter`'s newest workout (Sunday the 22nd) is this week, the `food-history`'s newest
 * day (the 18th) one of the seven before today, the `food-weight`'s last Sunday the one before today, so its check-in is
 * due. Yesterday is in the same week; "usual at this time" is the Skyr of 10:30; a meal made now is a Breakfast.
 */
val NOW: Long = ZonedDateTime.of(2026, 11, 25, 10, 0, 0, 0, BERLIN).toInstant().toEpochMilli()

/** A test that starts on another fixture than its class's [EisenApp]: `@Fixture("food-weight")`. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Fixture(val name: String)

typealias Screen = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/**
 * The app as a new process finds it: the saved state of the fixture [state] (none: a fresh install), read before the
 * activity starts, at [NOW]. Use it as `@get:Rule val app = EisenApp("food")` and the screen as `app.screen`.
 */
class EisenApp(private val state: String? = null) : TestRule {
    val screen: Screen = createAndroidComposeRule<MainActivity>()

    private val context get() = RuntimeEnvironment.getApplication()

    /** The app's `files/`, where it keeps `eisen.json`, `plate.json` and a debug build's `off/` answers. */
    val files: File get() = context.filesDir

    private var zone: TimeZone? = null

    private val install = object : ExternalResource() {
        override fun before() {
            zone = TimeZone.getDefault()
            TimeZone.setDefault(TimeZone.getTimeZone(BERLIN))
            SystemClock.setCurrentTimeMillis(NOW)
            // As `eisenctl launch` grants it: a run and a rest show their notification without asking first.
            org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            // Every recording plays for a second, so the timer's voice runs its course and logs what it said.
            ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(1_000, 0) }
            files.deleteRecursively()
            files.mkdirs()
            val saved = File(files, "eisen.json")
            val state = fixture ?: state
            if (state != null) File(FIXTURES, "$state.json").also { require(it.isFile) { "no fixture $it" } }.copyTo(saved, overwrite = true)
            Eisen.restart(context)
        }

        override fun after() {
            TimeZone.setDefault(zone)
        }
    }

    private val chain = RuleChain.outerRule(install).around(screen)

    /** The fixture a test names with [Fixture], in place of the class's. */
    private var fixture: String? = null

    override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement {
        fixture = description.getAnnotation(Fixture::class.java)?.name
        return chain.apply(base, description)
    }

    /**
     * Open Food Facts answers from the saved [answers] (`product/3017620422003`, `search/skyr`), as a debug build does
     * from its `files/off/`. With the folder there, an answer that is missing is no network.
     */
    fun answers(vararg answers: String) {
        for (kind in listOf("product", "search")) File(files, "off/$kind").mkdirs()
        for (answer in answers) File(ANSWERS, "$answer.json").copyTo(File(files, "off/$answer.json"), overwrite = true)
    }

    /**
     * The process is killed and started again from the launcher: everything the app keeps is read from its files again
     * and the activity is made anew. What lived only in memory (the model, a snackbar) is gone.
     */
    fun restart() {
        val state = File(files, "eisen.json")
        // The app writes its file off the main thread: the new process reads what the old one wrote last.
        screen.waitUntil(5_000) { state.isFile && (loadState(state.readText()) as? Loaded.Ok)?.state == Eisen.model.state }
        screen.runOnUiThread { Eisen.restart(context) }
        screen.activityRule.scenario.recreate()
        screen.waitForIdle()
    }
}

/**
 * What the app would write to `eisen.json` now, at [path] (`active.exercises.0.sets`; empty: all of it): what a recipe
 * reads with `eisenctl state` and jq. A key that is not there is [JsonNull].
 */
fun saved(path: String = ""): JsonElement =
    path.split('.').filter { it.isNotEmpty() }.fold(json(encodeState(Eisen.model.state))) { at, key ->
        when (at) {
            is JsonObject -> at[key] ?: JsonNull
            JsonNull -> JsonNull
            else -> key.toIntOrNull()?.let { at.jsonArray.getOrNull(it) } ?: JsonNull
        }
    }

/** Each element of the array at [path] cut down to [keys], as jq's `map([.name, .amount, .kcal])`. */
fun savedRows(path: String, vararg keys: String): JsonElement =
    JsonArray(saved(path).jsonArray.map { row -> JsonArray(keys.map { key -> row.jsonObject[key] ?: JsonNull }) })

/** JSON written out, to compare with [saved]: `json("[5, null, null, null, null]")`. A number is its value: 118.0 is 118. */
fun json(text: String): JsonElement = numbers(Json.parseToJsonElement(text))

private fun numbers(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.mapValues { numbers(it.value) })
    is JsonArray -> JsonArray(element.map(::numbers))
    is JsonPrimitive -> if (element.isString) element else element.content.toBigDecimalOrNull()?.let { JsonPrimitive(it.stripTrailingZeros().toPlainString().toBigDecimal()) } ?: element
    else -> element
}

/** What the screen shows: every text and description, in order, for a message that says what was there instead. */
fun Screen.texts(): List<String> {
    fun walk(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            listOfNotNull(node.config.getOrNull(SemanticsProperties.EditableText)?.text) +
            node.children.flatMap(::walk)
    return onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap(::walk)
}

private fun Screen.missing(handle: Any) = "'$handle' is not on screen. It shows: ${texts().joinToString(" / ")}"

/** A text or a description on screen, scrolled into view first when it is in a list. */
fun Screen.find(handle: String): SemanticsNodeInteraction = find(matcher(handle), handle)

/** The first node whose whole text or description matches [pattern], scrolled into view first when it is in a list. */
fun Screen.find(pattern: Regex): SemanticsNodeInteraction = find(matcher(pattern), pattern)

private fun Screen.find(matcher: SemanticsMatcher, handle: Any): SemanticsNodeInteraction {
    waitForIdle()
    check(reach(matcher)) { missing(handle) }
    return onAllNodes(matcher).onFirst().also { runCatching { it.performScrollTo() } }
}

fun Screen.tap(handle: String) {
    find(handle).performClick()
    waitForIdle()
}

fun Screen.tap(pattern: Regex) {
    find(pattern).performClick()
    waitForIdle()
}

/** The [index]-th node (from 0) with [handle], as `eisenctl tap "5" 2` taps the second: the set circles all read `5`. */
fun Screen.tap(handle: String, index: Int) {
    waitForIdle()
    onAllNodes(matcher(handle))[index].performClick()
    waitForIdle()
}

/**
 * Two taps on the same spot [gap] seconds apart, from one look at the screen, as `eisenctl doubletap` gives: the second
 * lands on whatever is there by then.
 */
fun Screen.doubleTap(handle: String, index: Int = 0, gap: Double = 0.15) {
    waitForIdle()
    val spot = onAllNodes(matcher(handle))[index].fetchSemanticsNode().boundsInRoot.center
    // The frames stand still between the taps, as a screen that is only starting to move in has not covered the spot yet.
    mainClock.autoAdvance = false
    try {
        onRoot().performTouchInput { click(spot) }
        ShadowLooper.idleMainLooper((gap * 1000).toLong(), TimeUnit.MILLISECONDS)
        onRoot().performTouchInput { click(spot) }
    } finally {
        mainClock.autoAdvance = true
    }
    waitForIdle()
}

/**
 * A press on [from] that stays down for a second, slides over to [to] (which the press brought up) and lifts there: the
 * slide of `features/workout.md`, which eisenctl sends as `input motionevent`.
 */
fun Screen.slide(from: String, fromIndex: Int, to: String) {
    waitForIdle()
    val start = onAllNodes(matcher(from))[fromIndex].fetchSemanticsNode().boundsInRoot.center
    onRoot().performTouchInput { down(start) }
    mainClock.advanceTimeBy(1_000)
    later(1)
    val end = onAllNodes(matcher(to)).onFirst().fetchSemanticsNode().boundsInRoot.center
    onRoot().performTouchInput {
        for (step in 1..4) moveTo(start + (end - start) * (step / 4f))
        up()
    }
    waitForIdle()
}

/** A press of a second, as `eisenctl hold` gives. */
fun Screen.hold(handle: String, index: Int = 0) {
    waitForIdle()
    check(reach(matcher(handle))) { missing(handle) }
    onAllNodes(matcher(handle))[index].performTouchInput { longClick(durationMillis = 1_000) }
    waitForIdle()
}

/** A swipe to the left over the row, the way a finger deletes an entry. */
fun Screen.swipeAway(handle: String) {
    find(handle).performTouchInput { swipeLeft() }
    waitForIdle()
}

/**
 * Text into the field that has the focus, as `eisenctl type` does after a tap on the field. Robolectric shows no
 * keyboard, and a field the app focuses for it may not have the focus here: then the one field on screen.
 */
fun Screen.type(text: String) {
    waitForIdle()
    val focused = isFocused().and(hasSetTextAction())
    val field = if (onAllNodes(focused).fetchSemanticsNodes().isNotEmpty()) onNode(focused) else onNode(hasSetTextAction())
    field.performTextInput(text)
    waitForIdle()
}

/**
 * The keyboard's Done key on the field being typed in: the number typed is taken. A recipe closes the keyboard with
 * `eisenctl back` instead, which Robolectric cannot (it has no keyboard); a number field ends its typing on both.
 */
fun Screen.done() {
    onNode(isFocused().and(hasSetTextAction())).performImeAction()
    waitForIdle()
}

/** The system's back. Robolectric has no keyboard, so a recipe's `back` that only put the keyboard away is left out. */
fun Screen.back() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}

/** The clock moves on by [seconds]: whatever the app posted on the main thread for that time runs, in order. */
fun Screen.later(seconds: Double) {
    waitForIdle()
    var left = (seconds * 1000).toLong()
    // A frame at a time, so the screen draws what each tick changed and a tap guard sees the time pass.
    while (left > 0) {
        val step = minOf(left, 100L)
        ShadowLooper.idleMainLooper(step, TimeUnit.MILLISECONDS)
        left -= step
    }
    waitForIdle()
}

fun Screen.later(seconds: Int) = later(seconds.toDouble())

/** Waits for an answer from another thread (Open Food Facts reads its files off the main thread). */
fun Screen.await(handle: String) {
    waitUntil(5_000) { present(matcher(handle)) }
}

fun Screen.await(pattern: Regex) {
    waitUntil(5_000) { present(matcher(pattern)) }
}

/** The recordings the timer's voice said (`EisenTimer: said <sound>`), in order: what `eisenctl said` reads in logcat. */
fun said(): List<String> = ShadowLog.getLogsForTag("EisenTimer").map { it.msg }.filter { it.startsWith("said ") }.map { it.removePrefix("said ") }

/** The voice said each of [sounds], in this order (other lines between them). */
fun assertSaid(vararg sounds: String) {
    val lines = said()
    var from = 0
    for (sound in sounds) {
        val at = lines.subList(from, lines.size).indexOf(sound)
        check(at >= 0) { "the voice did not say $sound (after ${sounds.takeWhile { it != sound }}). It said: $lines" }
        from += at + 1
    }
}

/** The bar: "Today" and "Progress" are its tabs (the last of the same text on screen), the rest opens from its `+`. */
fun Screen.tab(name: String) {
    when (name) {
        "Today", "Progress" -> onAllNodesWithText(name).onLast().performClick()
        "Food" -> { tap("New"); tap("Food diary") }
        else -> { tap("New"); tap(name) }
    }
    waitForIdle()
}

private fun matcher(handle: String) = hasText(handle).or(hasContentDescription(handle))

private fun matcher(pattern: Regex) = SemanticsMatcher("text or description matches $pattern") { node ->
    (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
        .any(pattern::matches)
}

private fun Screen.present(matcher: SemanticsMatcher): Boolean = onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

/**
 * Brings [matcher] into the composition: a lazy list only composes what is near the screen, so a row further down is
 * scrolled to through every list on screen. False when no list has it.
 */
private fun Screen.reach(matcher: SemanticsMatcher): Boolean {
    if (present(matcher)) return true
    for (index in onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices) {
        if (runCatching { onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(matcher) }.isSuccess) {
            waitForIdle()
            if (present(matcher)) return true
        }
    }
    return false
}

/** Each handle is on the screen (as text or as a description), scrolled to when it is in a list. */
fun Screen.has(vararg handles: String) {
    waitForIdle()
    for (handle in handles) check(reach(matcher(handle))) { missing(handle) }
}

/** Something on screen matches each pattern whole, as eisenctl's handles do. */
fun Screen.has(vararg patterns: Regex) {
    waitForIdle()
    for (pattern in patterns) check(reach(matcher(pattern))) { missing(pattern) }
}

/** None of the handles is on screen. */
fun Screen.gone(vararg handles: String) {
    waitForIdle()
    for (handle in handles) check(!present(matcher(handle))) { "'$handle' is on screen" }
}
