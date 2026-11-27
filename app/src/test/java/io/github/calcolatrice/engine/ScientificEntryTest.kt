package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** Tasto ×10ⁿ: notazione scientifica come sulle calcolatrici (6,02×10²³). */
class ScientificEntryTest {

    private fun type(keys: List<CalcKey>, start: CalculatorState = CalculatorState()): CalculatorState =
        keys.fold(start) { state, key -> CalculatorEditor.onKey(state, key, AngleUnit.DEGREES).state }

    private fun keys(text: String): List<CalcKey> = text.map { c ->
        when (c) {
            'E' -> CalcKey.Exponent
            '.' -> CalcKey.Decimal
            '<' -> CalcKey.Backspace
            '(' -> CalcKey.OpenParen
            in "+-*/^" -> CalcKey.Operator(c)
            else -> CalcKey.Digit(c)
        }
    }

    private fun expression(text: String) = type(keys(text)).expression

    @Test
    fun exponentAfterNumber() {
        assertEquals("6.02E23", expression("6.02E23"))
        assertEquals("1.5E-7", expression("1.5E-7"))
        assertEquals("2*1E3", expression("2*E3")) // senza numero davanti vale 1×10ⁿ
        assertEquals("1E5", expression("E5"))
    }

    @Test
    fun onlyDigitsAndMinusInsideTheExponent() {
        assertEquals("2E", expression("2E+")) // + non entra nell'esponente
        assertEquals("2E-", expression("2E--"))
        assertEquals("2E", expression("2E.")) // niente virgola nell'esponente
        assertEquals("2E", expression("2E("))
        assertEquals("2E", expression("2EE"))
        assertEquals("2E3+1", expression("2E3+1"))
        assertEquals("2", expression("2E<")) // cancellando la E si torna al numero
        assertEquals("2", expression("2E-<"))
    }

    @Test
    fun exponentValues() {
        val result = CalculatorEditor.onKey(type(keys("6.02E23*2")), CalcKey.Equals, AngleUnit.DEGREES).state
        assertEquals(BigDecimal("1.204E24").compareTo(result.ans), 0)
        val small = CalculatorEditor.onKey(type(keys("1.5E-7/3")), CalcKey.Equals, AngleUnit.DEGREES).state
        assertEquals(BigDecimal("5E-8").compareTo(small.ans), 0)
    }

    @Test
    fun exponentAfterResult() {
        val after = CalculatorState(expression = "@", ans = BigDecimal("6.02"), justEvaluated = true)
        assertEquals("6.02E", type(listOf(CalcKey.Exponent), after).expression)
        val negative = CalculatorState(expression = "@", ans = BigDecimal("-2"), justEvaluated = true)
        assertEquals("@*1E", type(listOf(CalcKey.Exponent), negative).expression)
    }

    @Test
    fun displayedAsPowerOfTen() {
        val f = DisplayFormatter(',', '.')
        assertEquals("6,02×10²³", f.format("6.02E23"))
        assertEquals("1×10", f.format("1E"))
        assertEquals("1×10⁻", f.format("1E-"))
    }
}
