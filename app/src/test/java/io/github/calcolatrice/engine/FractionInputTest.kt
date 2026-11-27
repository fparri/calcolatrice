package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/** Frazioni su due livelli mentre si scrive: `{numeratore~denominatore}` nel formato interno. */
class FractionInputTest {

    private fun keys(text: String): List<CalcKey> = text.map { c ->
        when (c) {
            'F' -> CalcKey.Fraction
            '<' -> CalcKey.Backspace
            '>' -> CalcKey.CloseParen // solo per i test: vedi move()
            '(' -> CalcKey.OpenParen
            'P' -> CalcKey.Parentheses
            '.' -> CalcKey.Decimal
            in "+-*/^" -> CalcKey.Operator(c)
            in "XA" -> CalcKey.Variable(c)
            else -> CalcKey.Digit(c)
        }
    }

    private fun type(text: String, casio: Boolean = false, start: CalculatorState = CalculatorState()): CalculatorState =
        keys(text).fold(start) { state, key -> CalculatorEditor.onKey(state, key, AngleUnit.DEGREES, emptyMap(), casio).state }

    private fun right(state: CalculatorState, steps: Int = 1): CalculatorState =
        (1..steps).fold(state) { s, _ -> CalculatorEditor.moveCursor(s, 1) }

    private fun value(state: CalculatorState): String =
        when (val r = evaluate(state.expression, AngleUnit.DEGREES)) {
            is EvalResult.Success -> ResultFormatter.toInternal(r.value)
            is EvalResult.Failure -> r.error.name
        }

    @Test
    fun dedicatedKeyTemplate() {
        val empty = type("F")
        assertEquals("{~}", empty.expression)
        assertEquals(1, empty.cursor) // nel numeratore

        val numerator = type("F3")
        assertEquals("{3~}", numerator.expression)
        // ▶ passa al denominatore, poi esce dalla frazione.
        val denominator = type("4", start = right(numerator))
        assertEquals("{3~4}", denominator.expression)
        val after = type("+1", start = right(denominator))
        assertEquals("{3~4}+1", after.expression)
        assertNull(after.cursor)
        assertEquals("1.75", value(after))
    }

    @Test
    fun casioDivisionTakesTheOperandBefore() {
        assertEquals("{3~}", type("3/", casio = true).expression)
        assertEquals("2+{3~4}", type("2+3/4", casio = true).expression)
        assertEquals("{X^2~}", type("X^2/", casio = true).expression)
        assertEquals("{(1+2)~}", type("(1+2P/", casio = true).expression)
        // Senza operando prima: frazione vuota con il cursore nel numeratore.
        val empty = type("/", casio = true)
        assertEquals("{~}", empty.expression)
        assertEquals(1, empty.cursor)
        // Nella modalità normale ÷ resta la divisione.
        assertEquals("3/4", type("3/4").expression)
    }

    @Test
    fun casioDivisionAfterResult() {
        val result = CalculatorState(expression = "@", ans = BigDecimal("6"), justEvaluated = true)
        val state = type("/4", casio = true, start = result)
        assertEquals("{@~4}", state.expression)
        assertEquals("1.5", ResultFormatter.toInternal((evaluate(state.expression, AngleUnit.DEGREES, BigDecimal("6")) as EvalResult.Success).value))
    }

    @Test
    fun nestedFractionsAndImplicitProduct() {
        // 1/(1/2) = 2
        val nested = type("F1", casio = false).let { right(it) }.let { type("F1", start = it) }.let { right(it) }.let { type("2", start = it) }
        assertEquals("{1~{1~2}}", nested.expression)
        assertEquals("2", value(nested))
        // Un numero dopo una frazione è un prodotto esplicito.
        val product = type("5", start = right(type("3/4", casio = true)))
        assertEquals("{3~4}*5", product.expression)
    }

    @Test
    fun backspaceKeepsFractionsComplete() {
        val full = right(type("3/4", casio = true)) // cursore dopo la frazione
        val inside = type("<", start = full)
        assertEquals("{3~4}", inside.expression) // ⌫ dopo la frazione entra nel denominatore
        assertEquals(4, inside.cursor)
        val emptied = type("<<<<", start = inside) // 4, poi al numeratore, poi 3, poi frazione vuota
        assertEquals("", emptied.expression)
    }

    @Test
    fun parenthesesStayInsideTheirSlot() {
        // Una parentesi aperta nel numeratore si chiude da sola al passaggio al denominatore.
        val state = type("2", start = right(type("F(1+1")))
        assertEquals("{(1+1~2}", state.expression)
        assertEquals("1", value(state))
        // ( ) nel denominatore non chiude una parentesi aperta fuori dalla frazione.
        val outside = type("(2+F1")
        val den = type("P", start = right(outside))
        assertEquals("(2+{1~(}", den.expression) // apre una parentesi nuova invece di chiudere quella fuori
    }

    @Test
    fun displayAndLinearText() {
        val f = DisplayFormatter(',', '.')
        assertEquals("{3~4}+1", f.layout("{3~4}+1").text)
        assertEquals("3/4+1", f.format("{3~4}+1"))
        assertEquals("(1+2)/3", f.format("{1+2~3}"))
        assertEquals("1/(1/2)", f.format("{1~{1~2}}"))
        assertEquals("3 quarti più 1", SpokenMath.describe(f.layout("{3~4}+1").text, "it"))
    }
}
