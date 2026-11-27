package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Variabili, memoria, SOLVE, cursore, formati di visualizzazione ed esponenti in apice. */
class FeaturesTest {

    private fun n(text: String) = BigDecimal(text)
    private fun show(x: BigDecimal?) = x?.let { ResultFormatter.toInternal(it) }

    private fun CalculatorSession.type(text: String) = text.forEach { c ->
        onKey(
            when (c) {
                '.' -> CalcKey.Decimal
                '=' -> CalcKey.Equals
                in "+-*/^" -> CalcKey.Operator(c)
                in VARIABLES -> CalcKey.Variable(c)
                '(' -> CalcKey.OpenParen
                ')' -> CalcKey.CloseParen
                '<' -> CalcKey.Backspace
                else -> CalcKey.Digit(c)
            },
        )
    }

    // ------------------------------------------------------------ variabili

    @Test
    fun variablesInExpressions() {
        val vars = mapOf('A' to n("3"), 'X' to n("-2"))
        assertEquals(EvalResult.Success(n("9")), evaluate("A^2", variables = vars))
        assertEquals(EvalResult.Success(n("-6")), evaluate("AX", variables = vars))
        assertEquals(EvalResult.Success(n("0")), evaluate("B", variables = vars)) // non definita = 0
        assertEquals(EvalResult.Failure(CalcError.SYNTAX), evaluate("E")) // E non è una variabile
        assertEquals("150", show((evaluate("1.5E2") as EvalResult.Success).value))
    }

    @Test
    fun variableKeysUseImplicitMultiplication() {
        var state = CalculatorState()
        for (key in listOf(CalcKey.Digit('2'), CalcKey.Variable('A'), CalcKey.Digit('3'))) {
            state = CalculatorEditor.onKey(state, key, AngleUnit.DEGREES).state
        }
        assertEquals("2*A*3", state.expression)
    }

    @Test
    fun storeVariables() {
        val session = CalculatorSession()
        session.type("6*7")
        assertTrue(session.store('A')) // calcola e salva in cronologia
        assertEquals("42", show(session.variables['A']))
        assertTrue(session.calc.justEvaluated)

        session.type("A+1=")
        assertEquals("43", show(session.calc.ans))

        // Un'espressione non valida non cambia la variabile e mostra l'errore.
        session.type("2+")
        session.store('A')
        assertEquals("42", show(session.variables['A']))
        assertEquals(CalcError.SYNTAX, session.calc.error)

        // La vecchia memoria M non è più una variabile: un valore salvato da versioni precedenti si ignora.
        assertTrue('M' !in CalculatorSession(variables = mapOf('M' to n("5"))).variables)

        session.clearVariables()
        assertTrue(session.variables.isEmpty())
    }

    @Test
    fun previewUsesVariables() {
        val session = CalculatorSession(variables = mapOf('X' to n("5")))
        session.type("X^2")
        assertEquals("25", show(session.preview))
    }

    // ------------------------------------------------------------ risolutore numerico (zeri delle funzioni)

    private fun solve(expression: String, start: String = "0", unit: AngleUnit = AngleUnit.RADIANS): String =
        when (val r = NumericSolver.solve(expression, unit, null, emptyMap(), 'X', n(start))) {
            is EvalResult.Success -> ResultFormatter.toInternal(r.value)
            is EvalResult.Failure -> r.error.name
        }

    @Test
    fun solveFindsRoots() {
        assertEquals("1.4142135623731", solve("X^2-2", "1"))
        assertEquals("-1.4142135623731", solve("X^2-2", "-1"))
        assertEquals("3", solve("2X-6"))
        assertEquals("0.739085133215161", solve("cos(X)-X")) // nessuna soluzione "a mano"
        assertEquals("30", solve("sin(X)-0.5", "10", AngleUnit.DEGREES))
        assertEquals("1E15", solve("X^2-10^30", "1")) // partenza lontana
        assertEquals("0", solve("X^3", "1")) // radice tripla: torna esattamente 0
        assertEquals("2", solve("ln(X)-ln(2)", "5"))
    }

    @Test
    fun solveReportsFailures() {
        assertEquals("NO_SOLUTION", solve("X^2+1"))
        assertEquals("NO_SOLUTION", solve("1/X", "1")) // cambio di segno su un polo
        assertEquals("NO_SOLUTION", solve("2+3")) // manca X
        assertEquals("SYNTAX", solve("X+"))
    }

    // ------------------------------------------------------------ cursore

    @Test
    fun cursorEditing() {
        val session = CalculatorSession()
        session.type("12+34")
        session.moveCursorTo(1) // 1|2+34
        session.type("5")
        assertEquals("152+34", session.calc.expression)
        assertEquals(2, session.calc.cursor)
        session.type("<")
        assertEquals("12+34", session.calc.expression)
        session.moveCursor(+1) // 12|+34
        session.type("*")
        assertEquals("12*+34", session.calc.expression) // il testo dopo il cursore resta com'è
        session.moveCursor(+1)
        session.moveCursor(+1)
        session.moveCursor(+1)
        session.moveCursor(+1)
        assertNull(session.calc.cursor) // in fondo
    }

    @Test
    fun cursorSkipsFunctionTokens() {
        val state = CalculatorState("2*sin(30)")
        val inside = CalculatorEditor.moveCursorTo(state, 4) // dentro "sin("
        assertEquals(6, inside.cursor)
        val left = CalculatorEditor.moveCursor(inside, -1)
        assertEquals(2, left.cursor)
    }

    @Test
    fun cursorAfterEqualsEditsTheResult() {
        val session = CalculatorSession()
        session.type("6*7=")
        session.moveCursorTo(1) // 4|2
        session.type("5")
        assertEquals("452", session.calc.expression)
        assertEquals("452", show(session.preview) ?: session.calc.expression)
    }

    @Test
    fun displayLayoutMapsPositions() {
        val layout = DisplayFormatter(',', '.').layout("1234+2^3")
        assertEquals("1.234+2³", layout.text)
        assertEquals(0, layout.positions[0])
        assertEquals(1, layout.positions[1]) // il cursore va prima del separatore
        assertEquals(5, layout.positions[4]) // "+"
        assertEquals(7, layout.positions[6]) // "^" coincide con l'apice
        assertEquals(8, layout.positions[8]) // fine
    }

    // ------------------------------------------------------------ apici e formati

    @Test
    fun superscripts() {
        val f = DisplayFormatter(',', '.')
        assertEquals("3²", f.format("3^2"))
        assertEquals("4⁻¹", f.format("4^-1"))
        assertEquals("2¹⁰+1", f.format("2^10+1"))
        assertEquals("(1+2)³", f.format("(1+2)^3"))
        assertEquals("2^0,5", f.format("2^0.5"))
        assertEquals("2^3!", f.format("2^3!"))
        assertEquals("2^3²", f.format("2^3^2")) // = 2^(3²), come lo calcola il parser
        assertEquals("e^(2)", f.format("e^(2)"))
        assertEquals("2^−", f.format("2^-"))
        assertEquals("1×10¹⁰⁰⁰", f.format("1E1000")) // niente separatori nell'esponente
    }

    @Test
    fun numberFormats() {
        val x = n("1234.5678")
        assertEquals("1234.57", ResultFormatter.format(x, NumberFormat.Fix(2)))
        assertEquals("1235", ResultFormatter.format(x, NumberFormat.Fix(0)))
        assertEquals("0.00", ResultFormatter.format(n("0.001"), NumberFormat.Fix(2)))
        assertEquals("1.23E3", ResultFormatter.format(x, NumberFormat.Sci(3)))
        assertEquals("1.000E0", ResultFormatter.format(n("1"), NumberFormat.Sci(4)))
        assertEquals("-4.5E-7", ResultFormatter.format(n("-0.00000045"), NumberFormat.Sci(2)))
        assertEquals("1.23456789012346E19", ResultFormatter.format(n("12345678901234567890"), NumberFormat.Fix(2)))
        for (format in listOf(NumberFormat.Normal, NumberFormat.Fix(3), NumberFormat.Sci(10))) {
            assertEquals(format, NumberFormat.parse(format.code))
        }
        assertEquals(NumberFormat.Normal, NumberFormat.parse("FIX 12"))
        assertEquals(NumberFormat.Normal, NumberFormat.parse(null))
        // Il formato ENG non c'è più: chi l'aveva scelto torna al normale.
        assertEquals(NumberFormat.Normal, NumberFormat.parse("ENG"))
    }

    @Test
    fun solutionFormatterUsesNumberFormat() {
        val f = SolutionFormatter(DisplayFormatter(',', '.'), NumberFormat.Fix(2))
        assertEquals("0,33", f.value(n("0.333333"), asFraction = false))
        assertEquals("3/4", f.value(n("0.75"), asFraction = true))
    }
}
