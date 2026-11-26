package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ExactFormsTest {

    private val solutions = SolutionFormatter(DisplayFormatter(',', '.'))

    private fun exact(text: String, unit: AngleUnit = AngleUnit.DEGREES): String? {
        val value = (evaluate(text, unit) as EvalResult.Success).value
        return solutions.exact(value)
    }

    @Test
    fun radicals() {
        assertEquals("2√2", exact("√8"))
        assertEquals("5√3", exact("√12+√27"))
        assertEquals("√3/2", exact("sin(60)"))
        assertEquals("√3/4", exact("cos(30)/2"))
        assertEquals("√6/3", exact("√(2/3)"))
        assertEquals("−√2", exact("-√2"))
        assertEquals("√2/2", exact("1/√2"))
        assertEquals("√3", exact("tan(60)"))
    }

    @Test
    fun multiplesOfPi() {
        assertEquals("π/2", exact("acos(0)", AngleUnit.RADIANS))
        assertEquals("3π/4", exact("atan(-1)+π", AngleUnit.RADIANS))
        assertEquals("−π/6", exact("asin(-0.5)", AngleUnit.RADIANS))
        assertEquals("π", exact("π"))
        assertEquals("2π", exact("2π"))
    }

    @Test
    fun fractions() {
        assertEquals("11/12", exact("2/3+1/4"))
        assertEquals("−3/4", exact("-0.75"))
        assertEquals("1.234/5", exact("1234/5"))
    }

    @Test
    fun valuesWithoutExactForm() {
        assertNull(exact("√2+√3"))
        assertNull(exact("(1+√5)/2"))
        assertNull(exact("ln(2)"))
        assertNull(exact("e"))
        assertNull(exact("1.4142135623"))
        // Gli interi restano nel formato scelto.
        assertNull(exact("6"))
        assertNull(exact("1E30"))
    }

    @Test
    fun complexRootsWithRadicals() {
        // x² + x + 1 = 0 → −1/2 ± (√3/2)i; x² + 3 = 0 → ±i√3.
        val pair = QuadraticEquation.solve(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE) as QuadraticSolution.ComplexPair
        assertEquals("−1/2 + (√3/2)i", solutions.complex(pair.re, pair.im, plus = true, asFraction = true))
        val pure = QuadraticEquation.solve(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal(3)) as QuadraticSolution.ComplexPair
        assertEquals("i√3", solutions.complex(pure.re, pure.im, plus = true, asFraction = true))
        assertEquals("−i√3", solutions.complex(pure.re, pure.im, plus = false, asFraction = true))
    }

    @Test
    fun squareSplitting() {
        assertEquals(2L to 2L, ExactForms.splitSquare(8))
        assertEquals(6L to 1L, ExactForms.splitSquare(36))
        assertEquals(1L to 30L, ExactForms.splitSquare(30))
        assertEquals(5L to 3L, ExactForms.splitSquare(75))
    }
}
