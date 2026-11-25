package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.workout.ui.RestPull
import io.github.alexanderop.eisen.feature.workout.ui.RestPullEvent
import io.github.alexanderop.eisen.feature.workout.ui.RestPullStep
import io.github.alexanderop.eisen.feature.workout.ui.restPullAfter
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pulling the rest screen down (`RestPull.kt`): one test per transition, then the bugs of the loose offset. */
class RestPullTest {
    private val line = 300f
    private val flick = 1500f
    private fun letGo(velocity: Float = 0f) = RestPullEvent.LetGo(velocity, line, flick)

    @Test fun `a drag pulls the screen down, never above its place`() {
        assertEquals(RestPullStep(RestPull.Pulled(40f)), restPullAfter(RestPull.Still, RestPullEvent.Drag(40f)))
        assertEquals(RestPullStep(RestPull.Pulled(60f)), restPullAfter(RestPull.Pulled(40f), RestPullEvent.Drag(20f)))
        assertEquals(RestPullStep(RestPull.Pulled(0f)), restPullAfter(RestPull.Pulled(40f), RestPullEvent.Drag(-90f)))
    }

    @Test fun `a drag catches a screen that springs back`() {
        assertEquals(RestPullStep(RestPull.Pulled(90f)), restPullAfter(RestPull.Returning(80f), RestPullEvent.Drag(10f)))
    }

    @Test fun `let go past the line or flicked, the screen is put away from where it is`() {
        assertEquals(RestPullStep(RestPull.Away(301f), minimise = true), restPullAfter(RestPull.Pulled(301f), letGo()))
        assertEquals(RestPullStep(RestPull.Away(20f), minimise = true), restPullAfter(RestPull.Pulled(20f), letGo(velocity = 1600f)))
    }

    @Test fun `let go short of the line, it springs back, frame by frame, and settles`() {
        assertEquals(RestPullStep(RestPull.Returning(120f)), restPullAfter(RestPull.Pulled(120f), letGo()))
        assertEquals(RestPullStep(RestPull.Still), restPullAfter(RestPull.Pulled(0f), letGo()))
        assertEquals(RestPullStep(RestPull.Returning(50f)), restPullAfter(RestPull.Returning(120f), RestPullEvent.Frame(50f)))
        assertEquals(RestPullStep(RestPull.Still), restPullAfter(RestPull.Returning(1f), RestPullEvent.Settled))
    }

    @Test fun `a screen on its way out takes no drag and no let go`() {
        val away = RestPull.Away(320f)
        assertEquals(RestPullStep(away), restPullAfter(away, RestPullEvent.Drag(-10f)))
        assertEquals(RestPullStep(away), restPullAfter(away, letGo(velocity = 2000f)))
    }

    @Test fun `shown again over the workout, a screen on its way out is back in place`() {
        assertEquals(RestPullStep(RestPull.Still), restPullAfter(RestPull.Away(320f), RestPullEvent.Shown))
        for (pull in listOf(RestPull.Still, RestPull.Pulled(10f), RestPull.Returning(10f))) assertEquals(RestPullStep(pull), restPullAfter(pull, RestPullEvent.Shown))
    }

    @Test fun `the spring changes nothing it does not move`() {
        for (pull in listOf(RestPull.Still, RestPull.Pulled(70f), RestPull.Away(320f))) {
            assertEquals(RestPullStep(pull), restPullAfter(pull, RestPullEvent.Frame(5f)))
            assertEquals(RestPullStep(pull), restPullAfter(pull, RestPullEvent.Settled))
        }
        assertEquals(RestPullStep(RestPull.Still), restPullAfter(RestPull.Still, letGo()))
    }

    // The bugs of the loose `pulled`.

    @Test fun `bug - reopened while it leaves, the screen is in place and a small drag does not put it away`() {
        // Old: the offset stayed at 320 after Minimise; reopened, the screen hung 320 px low and any drag minimised it.
        var pull: RestPull = RestPull.Pulled(320f)
        pull = restPullAfter(pull, letGo()).pull
        pull = restPullAfter(pull, RestPullEvent.Shown).pull
        assertEquals(0f, pull.offset, 0f)
        pull = restPullAfter(pull, RestPullEvent.Drag(5f)).pull
        assertEquals(RestPullStep(RestPull.Returning(5f)), restPullAfter(pull, letGo()))
    }

    @Test fun `bug - a drag during the spring back is not overwritten by the spring`() {
        // Old: the drag and the animation both wrote the offset, so the next frame of the spring undid the drag.
        var pull = restPullAfter(RestPull.Pulled(120f), letGo()).pull
        pull = restPullAfter(pull, RestPullEvent.Drag(30f)).pull
        pull = restPullAfter(pull, RestPullEvent.Frame(40f)).pull
        pull = restPullAfter(pull, RestPullEvent.Settled).pull
        assertEquals(RestPull.Pulled(150f), pull)
    }
}
