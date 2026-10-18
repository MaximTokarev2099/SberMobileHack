package com.sbermobilehack.magicball

import kotlin.math.abs
import kotlin.math.exp

data class ThrowState(val x: Float, val y: Float, val vx: Float, val vy: Float)
data class ThrowStep(val state: ThrowState, val impact: Float, val stopped: Boolean)

fun advanceThrow(state: ThrowState, dt: Float, width: Float, height: Float, radius: Float, density: Float): ThrowStep {
    val seconds = dt.coerceIn(0f, .032f)
    val right = (width - radius).coerceAtLeast(radius)
    val bottom = (height - radius).coerceAtLeast(radius)
    var vx = state.vx * exp(-.65f * seconds)
    var vy = state.vy + 1600f * density * seconds
    var x = state.x + vx * seconds
    var y = state.y + vy * seconds
    var impact = 0f
    if (x < radius || x > right) {
        impact = abs(vx)
        x = x.coerceIn(radius, right)
        vx = if (x == radius) abs(vx) * .68f else -abs(vx) * .68f
    }
    if (y < radius || y > bottom) {
        impact = maxOf(impact, abs(vy))
        y = y.coerceIn(radius, bottom)
        vy = if (y == radius) abs(vy) * .62f else -abs(vy) * .62f
        if (y == bottom) {
            vx *= .78f
            if (abs(vy) < 100f * density) vy = 0f
        }
    }
    val stopped = y >= bottom - 1f && abs(vx) < 35f * density && vy == 0f
    return ThrowStep(ThrowState(x, y, if (stopped) 0f else vx, vy), impact, stopped)
}
