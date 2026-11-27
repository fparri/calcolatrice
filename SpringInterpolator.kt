package io.github.calcolatrice.ui

import android.animation.TimeInterpolator
import kotlin.math.cos
import kotlin.math.exp

/**
 * Molla smorzata per le animazioni (Material 3 Expressive): arriva in fretta, supera di poco il
 * valore finale e si assesta. Il framework senza AndroidX non ha animazioni a molla; questa è la
 * risposta di un oscillatore sottosmorzato, normalizzata perché valga 0 all'inizio e 1 alla fine.
 */
class SpringInterpolator(
    /** Più alto = si assesta prima. */
    private val damping: Float = 6f,
    /** Più alto = oscilla più volte. */
    private val frequency: Float = 9f,
) : TimeInterpolator {
    private val end = 1f - exp(-damping) * cos(frequency)

    override fun getInterpolation(input: Float): Float =
        (1f - exp(-damping * input) * cos(frequency * input)) / end
}
