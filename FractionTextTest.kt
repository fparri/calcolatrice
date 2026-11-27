package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FractionTextTest {

    private fun parts(text: String) = FractionText.find(text).map { Triple(text.substring(it.start, it.end), it.numerator, it.denominator) }

    @Test
    fun simpleFractions() {
        assertEquals(listOf(Triple("3/4", "3", "4")), parts("−3/4"))
        assertEquals(listOf(Triple("1.000.000/3", "1.000.000", "3")), parts("1.000.000/3"))
        assertEquals(listOf(Triple("√3/2", "√3", "2")), parts("√3/2"))
        assertEquals(listOf(Triple("2√6/3", "2√6", "3")), parts("x = 2√6/3"))
        assertEquals(listOf(Triple("3π/4", "3π", "4")), parts("3π/4"))
        assertEquals(listOf(Triple("π/2", "π", "2")), parts("π/2"))
    }

    @Test
    fun complexAndInequalities() {
        assertEquals(
            listOf(Triple("1/2", "1", "2"), Triple("(√3/2)", "√3", "2")),
            parts("−1/2 + (√3/2)i"),
        )
        assertEquals(
            listOf(Triple("1/2", "1", "2"), Triple("2/3", "2", "3")),
            parts("x ≤ −1/2 ∨ x ≥ 2/3"),
        )
        assertEquals(
            listOf(Triple("1/2", "1", "2"), Triple("2/3", "2", "3")),
            parts("]−∞; −1/2] ∪ [2/3; +∞["),
        )
    }

    @Test
    fun textWithoutFractions() {
        assertEquals(emptyList<Triple<String, String, String>>(), parts("0,75"))
        assertEquals(emptyList<Triple<String, String, String>>(), parts("2√2"))
        assertEquals(emptyList<Triple<String, String, String>>(), parts("Nessuna soluzione"))
    }
}
