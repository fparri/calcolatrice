package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenMathTest {

    private fun it(text: String) = SpokenMath.describe(text, "it")
    private fun en(text: String) = SpokenMath.describe(text, "en")

    @Test
    fun fractions() {
        assertEquals("meno 3 quarti", it("−3/4"))
        assertEquals("un mezzo", it("1/2"))
        assertEquals("radice di 3 fratto 2", it("√3/2"))
        assertEquals("3 pi greco fratto 4", it("3π/4"))
        assertEquals("11 fratto 12", it("11/12"))
        assertEquals("minus 3 quarters", en("−3/4"))
        assertEquals("one half", en("1/2"))
    }

    @Test
    fun powersAndScientificNotation() {
        assertEquals("x al quadrato più 1", it("x²+1"))
        assertEquals("6,02 per 10 alla 23", it("6,02×10²³"))
        assertEquals("1,5 per 10 alla meno 12", it("1,5×10⁻¹²"))
        assertEquals("2 cubed", en("2³"))
        assertEquals("2 to the power of 10", en("2¹⁰"))
    }

    @Test
    fun equationsAndInequalities() {
        assertEquals("x 1 uguale meno un mezzo", it("x₁ = −1/2"))
        assertEquals("x minore o uguale a meno un mezzo oppure x maggiore o uguale a 2 terzi", it("x ≤ −1/2 ∨ x ≥ 2/3"))
        assertEquals("arcoseno(0,5)", it("sin⁻¹(0,5)"))
        assertEquals("meno un mezzo più radice di 3 fratto 2 i", it("−1/2 + (√3/2)i"))
    }

    @Test
    fun limits() {
        assertEquals("lim x tende a 2 da sinistra f(x) uguale meno infinito", it("lim x → 2⁻ f(x) = −∞"))
        assertEquals("lim x tends to 0 from the right f(x) equals plus infinity", en("lim x → 0⁺ f(x) = +∞"))
    }
}
