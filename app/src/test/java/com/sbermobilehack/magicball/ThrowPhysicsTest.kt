package com.sbermobilehack.magicball

import org.junit.Assert.*
import org.junit.Test

class ThrowPhysicsTest {
    @Test fun wallReversesVelocityAndKeepsBallInside() {
        val step = advanceThrow(ThrowState(349f, 200f, 2000f, 0f), .016f, 400f, 800f, 50f, 1f)
        assertTrue(step.state.vx < 0)
        assertTrue(step.state.x <= 350f)
        assertTrue(step.impact > 0)
    }
    @Test fun gravityAndFrictionEventuallyStopThrow() {
        var state = ThrowState(200f, 200f, 1500f, -900f)
        var stopped = false
        repeat(1200) {
            val step = advanceThrow(state, .016f, 400f, 800f, 50f, 1f)
            state = step.state
            stopped = stopped || step.stopped
            assertTrue(state.x in 50f..350f)
            assertTrue(state.y in 50f..750f)
        }
        assertTrue(stopped)
    }
}
