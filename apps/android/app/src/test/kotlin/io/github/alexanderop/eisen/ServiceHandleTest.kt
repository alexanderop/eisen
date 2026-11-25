package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.feature.workout.platform.ForegroundService
import io.github.alexanderop.eisen.feature.workout.platform.ServiceCall
import io.github.alexanderop.eisen.feature.workout.platform.ServiceEvent
import io.github.alexanderop.eisen.feature.workout.platform.ServiceHandle
import io.github.alexanderop.eisen.feature.workout.platform.transition
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The foreground service of the rest's countdown and of the interval timer (`ServiceHandle.kt`): one test
 * per transition, then the handle with fake Android calls.
 */
class ServiceHandleTest {
    private val idle: ServiceHandle<String> = ServiceHandle.Idle
    private val asked: ServiceHandle<String> = ServiceHandle.Asked("old")
    private val refused: ServiceHandle<String> = ServiceHandle.Refused("old")

    private fun next(handle: ServiceHandle<String>, event: ServiceEvent<String>) = transition(handle, event)

    @Test fun `Show asks for the service unless it was asked for, then it updates the notice in place`() {
        assertEquals(ServiceHandle.Asked("new") to ServiceCall.ASK, next(idle, ServiceEvent.Show("new")))
        assertEquals(ServiceHandle.Asked("new") to ServiceCall.ASK, next(refused, ServiceEvent.Show("new")))
        assertEquals(ServiceHandle.Asked("new") to ServiceCall.NOTIFY, next(asked, ServiceEvent.Show("new")))
    }

    @Test fun `Hide stops a service that was asked for, and forgets the notice in any case`() {
        assertEquals(ServiceHandle.Idle to ServiceCall.STOP, next(asked, ServiceEvent.Hide))
        assertEquals(ServiceHandle.Idle to ServiceCall.NONE, next(refused, ServiceEvent.Hide))
        assertEquals(ServiceHandle.Idle to ServiceCall.NONE, next(idle, ServiceEvent.Hide))
    }

    @Test fun `a refusal or a service that ended leaves the notice waiting for the next ask`() {
        for (event in listOf(ServiceEvent.Refusal, ServiceEvent.Lost)) {
            assertEquals(ServiceHandle.Refused("old") to ServiceCall.NONE, next(asked, event))
            assertEquals(refused to ServiceCall.NONE, next(refused, event))
            assertEquals(idle to ServiceCall.NONE, next(idle, event))
        }
    }

    @Test fun `a service that comes up is asked for again`() {
        assertEquals(ServiceHandle.Asked("old") to ServiceCall.NONE, next(refused, ServiceEvent.Started))
        assertEquals(asked to ServiceCall.NONE, next(asked, ServiceEvent.Started))
        assertEquals(idle to ServiceCall.NONE, next(idle, ServiceEvent.Started))
    }

    /** A handle whose Android side writes down what it was asked to do. [allowed] says whether Android takes a start. */
    private class Fake(var allowed: Boolean = true) {
        val calls = mutableListOf<String>()
        val service = ForegroundService<String>(
            ask = { calls += "ask"; allowed },
            notify = { calls += "notify" },
            stop = { calls += "stop" },
        )
    }

    @Test fun `a service that went away is asked for again by the next notice, not only updated`() {
        // Before, the timer kept a flag that it had asked once: a service that ended meanwhile never came back.
        val fake = Fake()
        fake.service.show("round 1")
        fake.service.lost()
        fake.service.show("round 2")
        assertEquals(listOf("ask", "ask"), fake.calls)
        assertEquals(ServiceHandle.Asked("round 2"), fake.service.handle)
    }

    @Test fun `a refused start is asked again with the next notice, and the notice is there for a late start`() {
        val fake = Fake(allowed = false)
        fake.service.show("rest")
        assertEquals(ServiceHandle.Refused("rest"), fake.service.handle)
        assertEquals("rest", fake.service.notice)
        fake.allowed = true
        fake.service.show("rest +30s")
        fake.service.show("rest +60s")
        fake.service.hide()
        assertEquals(listOf("ask", "ask", "notify", "stop"), fake.calls)
        assertEquals(null, fake.service.notice)
    }
}
