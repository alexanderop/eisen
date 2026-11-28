package io.github.alexanderop.eisen.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.rules.ActivityScenarioRule
import io.github.alexanderop.eisen.MainActivity
import io.github.alexanderop.eisen.app.runtime.Eisen
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.robolectric.RuntimeEnvironment
import java.io.File

/*
 * The screen tests: the real MainActivity, the real update and the real saved state, on the JVM under Robolectric. No
 * emulator, no adb, no screen dumps: a flow takes a second or two. What only a phone can show (the camera app, the
 * notifications, the lock screen, Gemini Nano, the release build under R8) stays with eisenctl's journeys.
 */

/** The fixtures eisenctl starts the emulator with (`launch --state <name>`); unit tests run in `apps/android/app`. */
private val FIXTURES = File("../../../.claude/skills/verify-eisen/fixtures")

typealias Screen = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/**
 * The app as a new process finds it: the saved state of the fixture [state] (none: a fresh install), read before the
 * activity starts. Use it as `@get:Rule val app = EisenApp("food")` and the screen as `app.screen`.
 */
class EisenApp(private val state: String? = null) : TestRule {
    val screen: Screen = createAndroidComposeRule<MainActivity>()

    private val files = object : ExternalResource() {
        override fun before() {
            val context = RuntimeEnvironment.getApplication()
            val saved = File(context.filesDir, "eisen.json")
            if (state == null) saved.delete() else File(FIXTURES, "$state.json").also { require(it.isFile) { "no fixture $it" } }.copyTo(saved, overwrite = true)
            Eisen.restart(context)
        }
    }

    private val chain = RuleChain.outerRule(files).around(screen)

    override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description) = chain.apply(base, description)
}

/** A text or a description on screen, scrolled into view first when it is in a list. */
fun Screen.find(handle: String): SemanticsNodeInteraction {
    waitForIdle()
    check(reach(handle)) { "'$handle' is not on screen" }
    return onAllNodes(matcher(handle)).onFirst().also { runCatching { it.performScrollTo() } }
}

fun Screen.tap(handle: String) {
    find(handle).performClick()
    waitForIdle()
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

private fun Screen.present(handle: String): Boolean = onAllNodes(matcher(handle)).fetchSemanticsNodes().isNotEmpty()

/**
 * Brings [handle] into the composition: a lazy list only composes what is near the screen, so a row further down is
 * scrolled to through every list on screen. False when no list has it.
 */
private fun Screen.reach(handle: String): Boolean {
    if (present(handle)) return true
    for (index in onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().indices) {
        if (runCatching { onAllNodes(hasScrollToNodeAction())[index].performScrollToNode(matcher(handle)) }.isSuccess) {
            waitForIdle()
            if (present(handle)) return true
        }
    }
    return false
}

/** Each handle is on the screen (as text or as a description), scrolled to when it is in a list. */
fun Screen.has(vararg handles: String) {
    waitForIdle()
    for (handle in handles) check(reach(handle)) { "'$handle' is not on screen" }
}

/** None of the handles is on screen. */
fun Screen.gone(vararg handles: String) {
    waitForIdle()
    for (handle in handles) check(!present(handle)) { "'$handle' is on screen" }
}
