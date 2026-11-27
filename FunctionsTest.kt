package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.math.abs

class FunctionsTest {

    private fun f(expression: String, unit: AngleUnit = AngleUnit.RADIANS) = UserFunction(expression, unit)
    private fun n(s: String) = BigDecimal(s)
    private fun show(x: BigDecimal) = ResultFormatter.toInternal(x)

    // ------------------------------------------------------------ valutazione veloce

    /** Il calcolo in double deve dare gli stessi valori del motore preciso (a meno degli arrotondamenti). */
    @Test
    fun fastEvaluationMatchesThePreciseEngine() {
        val expressions = listOf(
            "X^2-3X+2", "sin(X)/X", "e^(-X^2)*cos(3X)", "ln(X^2+1)+√(X^2)", "(X^3-1)/(X-2)",
            "2X", "-X^2", "2^X^2", "X!", "200+X%", "50*X%", "√X+1", "√-X", "abs(X-1)", "root(3;X)",
            "log(2;X)", "log(X)", "tan(X)", "asin(X/10)", "ncr(10;abs(X))", "1/X", "3(X+1)(X-1)", "Xπ",
            "e^X", "10^(X/3)", "gcd(12;X)", "normc(-1;X)", "binp(10;0.5;abs(X))",
        )
        val xs = listOf(-7.5, -3.0, -1.0, -0.5, 0.0, 0.25, 1.0, 2.0, 3.0, 4.5, 6.0)
        for (unit in AngleUnit.entries) {
            for (e in expressions) {
                val fast = FastEval.compile(e, unit)!!
                for (x in xs) {
                    val precise = evaluate(e, unit, variables = mapOf('X' to BigDecimal(x)))
                    val v = fast(x)
                    when (precise) {
                        is EvalResult.Failure -> assertTrue("$e @ $x ($unit): atteso non definito, ottenuto $v", !v.isFinite())
                        is EvalResult.Success -> {
                            val p = precise.value.toDouble()
                            val ok = abs(v - p) <= 1e-9 * maxOf(1.0, abs(p))
                            assertTrue("$e @ $x ($unit): $v invece di $p", ok)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun fastEvaluationRejectsInvalidExpressions() {
        assertNull(FastEval.compile("2+", AngleUnit.RADIANS)?.invoke(1.0)?.takeIf { it.isFinite() })
        assertNull(FastEval.compile("(1;2)", AngleUnit.RADIANS))
        assertNull(FastEval.compile("", AngleUnit.RADIANS))
    }

    // ------------------------------------------------------------ derivate

    @Test
    fun derivatives() {
        assertEquals("10", show(FunctionAnalysis.derivative(f("X^3-2X"), n("2"))))
        assertEquals("1", show(FunctionAnalysis.derivative(f("sin(X)"), n("0"))))
        assertEquals("0.5", show(FunctionAnalysis.derivative(f("√X"), n("1"))))
        assertEquals("0.333333333333333", show(FunctionAnalysis.derivative(f("ln(X)"), n("3"))))
        assertEquals("7.38905609893065", show(FunctionAnalysis.derivative(f("e^X"), n("2"))))
        assertEquals("0", show(FunctionAnalysis.derivative(f("X^2"), n("0"))))
        // In gradi: d/dx sin(x°) = π/180 · cos(x°).
        assertEquals("0.0174532925199433", show(FunctionAnalysis.derivative(f("sin(X)", AngleUnit.DEGREES), n("0"))))
    }

    @Test
    fun nonDifferentiablePoints() {
        assertEquals(CalcError.UNDEFINED, runCatching { FunctionAnalysis.derivative(f("abs(X)"), n("0")) }.exceptionOrNull()?.let { (it as CalcException).error })
        assertEquals(CalcError.UNDEFINED, runCatching { FunctionAnalysis.derivative(f("√X"), n("0")) }.exceptionOrNull()?.let { (it as CalcException).error })
        assertEquals("1", show(FunctionAnalysis.derivative(f("abs(X)"), n("2"))))
    }

    // ------------------------------------------------------------ integrali

    private fun bound(text: String) = (evaluate(text) as EvalResult.Success).value
    private fun integral(e: String, a: String, b: String) = FunctionAnalysis.integral(f(e), bound(a), bound(b))

    @Test
    fun exactIntegralsOfPolynomials() {
        assertEquals("9", show(integral("X^2", "0", "3").value))
        assertEquals("0", show(integral("X^3", "-2", "2").value))
        assertEquals("-9", show(integral("X^2", "3", "0").value))
        assertFalse(integral("X^2", "0", "3").approximate)
    }

    @Test
    fun integralsOfTranscendentalFunctions() {
        assertEquals("2", show(integral("sin(X)", "0", "π").value))
        assertEquals("0", show(integral("sin(X)", "-1", "1").value))
        assertEquals("1.71828182845905", show(integral("e^X", "0", "1").value))
        assertEquals("1", show(integral("1/X", "1", "e").value))
        assertEquals("0.785398163397448", show(integral("1/(1+X^2)", "0", "1").value))
        assertEquals("0", show(integral("X", "2", "2").value))
    }

    @Test
    fun integralsWithEndpointSingularities() {
        // Singolarità integrabili agli estremi: le risolve la quadratura tanh-sinh.
        val cases = listOf(
            Triple("√X", "0" to "1", "0.666666666666667"),
            Triple("1/√X", "0" to "1", "2"),
            Triple("ln(X)", "0" to "1", "-1"),
            Triple("√(1-X^2)", "-1" to "1", "1.5707963267949"),
            Triple("root(3;X)", "0" to "8", "12"),
        )
        for ((e, bounds, expected) in cases) {
            val r = integral(e, bounds.first, bounds.second)
            assertEquals(e, expected, show(r.value))
            assertFalse(e, r.approximate)
        }
    }

    @Test
    fun integralsWithKinks() {
        // Lo spigolo di |x| in 0 non cade su un punto di suddivisione di [−1, 2].
        val r = integral("abs(X)", "-1", "2")
        assertEquals("2.5", show(r.value))
        assertFalse(r.approximate)
    }

    @Test
    fun divergentIntegralsAreMarkedApproximate() {
        assertTrue(integral("1/X", "0", "1").approximate)
        assertTrue(integral("1/X^2", "0", "1").approximate)
        // Le due metà si annullano, ma l'integrale non esiste: niente "0" esatto.
        assertTrue(integral("1/X", "-1", "1").approximate)
    }

    @Test
    fun integralOfUndefinedFunction() {
        val error = runCatching { integral("√X", "-1", "1") }.exceptionOrNull() as CalcException
        assertEquals(CalcError.UNDEFINED, error.error)
    }

    // ------------------------------------------------------------ zeri

    private fun zeros(e: String, a: String, b: String, unit: AngleUnit = AngleUnit.RADIANS) =
        FunctionAnalysis.zeros(f(e, unit), n(a), n(b)).map(::show)

    @Test
    fun zerosInAnInterval() {
        assertEquals(listOf("1", "2"), zeros("X^2-3X+2", "-10", "10"))
        assertEquals(listOf("-1.4142135623731", "1.4142135623731"), zeros("X^2-2", "-5", "5"))
        assertEquals(listOf("0", "3.14159265358979", "6.28318530717959"), zeros("sin(X)", "-0.5", "7"))
        // Zero doppio: nessun cambio di segno.
        assertEquals(listOf("1"), zeros("(X-1)^2", "-3", "4"))
        // 1/x cambia segno in 0 ma non ha zeri.
        assertEquals(emptyList<String>(), zeros("1/X", "-1", "1"))
        assertEquals(listOf("0", "180", "360"), zeros("sin(X)", "0", "360", AngleUnit.DEGREES))
    }

    // ------------------------------------------------------------ tabella e schermata

    @Test
    fun tableUsesExactSteps() {
        val rows = FunctionAnalysis.table(f("X^2"), n("-1"), n("0.1"), 21)
        assertEquals("-1", show(rows[0].first))
        assertEquals("0", show(rows[10].first))
        assertEquals("0.01", show((rows[11].second as EvalResult.Success).value))
        assertEquals("1", show(rows[20].first))
        val reciprocal = FunctionAnalysis.table(f("1/X"), n("-1"), n("1"), 3)
        assertEquals(EvalResult.Failure(CalcError.DIVISION_BY_ZERO), reciprocal[1].second)
    }

    @Test
    fun sheetEditingAndAnalysis() {
        val sheet = FunctionSheet()
        for (key in listOf(CalcKey.Variable('X'), CalcKey.Operator('^'), CalcKey.Digit('2'))) sheet.onKey(key)
        assertEquals("X^2", sheet.expression)
        sheet.setTab(FunctionTab.ANALYSIS)
        sheet.select(FunctionCell.POINT)
        sheet.onKey(CalcKey.Clear)
        sheet.onKey(CalcKey.Digit('3'))
        // La X non entra nei parametri.
        sheet.onKey(CalcKey.Variable('X'))
        assertEquals("3", sheet.cell(FunctionCell.POINT))
        sheet.select(FunctionCell.FROM); sheet.onKey(CalcKey.Clear); sheet.onKey(CalcKey.Operator('-')); sheet.onKey(CalcKey.Digit('1'))
        sheet.select(FunctionCell.TO); sheet.onKey(CalcKey.Clear); sheet.onKey(CalcKey.Digit('2'))
        val result = sheet.analyze().getOrThrow()
        assertEquals("9", show((result.value as AnalysisItem.Value).value))
        assertEquals("6", show((result.derivative as AnalysisItem.Value).value))
        assertEquals("3", show((result.integral as AnalysisItem.Value).value))
        assertEquals(listOf("0"), (result.zeros as AnalysisItem.Values).values.map(::show))
        // La vista Grafico mostra solo la funzione: la selezione torna lì.
        sheet.setTab(FunctionTab.GRAPH)
        assertEquals(FunctionCell.EXPRESSION, sheet.selected)
    }

    @Test
    fun sheetReportsInvalidInput() {
        val sheet = FunctionSheet()
        assertTrue(sheet.table().isFailure)
        sheet.onKey(CalcKey.Variable('X'))
        sheet.select(FunctionCell.TABLE_STEP); sheet.onKey(CalcKey.Clear); sheet.onKey(CalcKey.Digit('0'))
        assertEquals(CalcError.UNDEFINED, (sheet.table().exceptionOrNull() as CalcException).error)
    }

    @Test
    fun secondFunctionTableAndIntersections() {
        val sheet = FunctionSheet()
        for (key in listOf(CalcKey.Variable('X'), CalcKey.Operator('^'), CalcKey.Digit('2'))) sheet.onKey(key)
        sheet.select(FunctionCell.SECOND)
        for (key in listOf(CalcKey.Variable('X'), CalcKey.Operator('+'), CalcKey.Digit('2'))) sheet.onKey(key)
        assertEquals("X+2", sheet.secondExpression)

        // Tabella con f e g.
        val rows = sheet.table(rows = 3).getOrThrow()
        assertEquals("-5", show(rows[0].x))
        assertEquals("25", show((rows[0].f as EvalResult.Success).value))
        assertEquals("-3", show((rows[0].g as EvalResult.Success).value))

        // x² = x + 2 per x = −1 e x = 2: punti (−1; 1) e (2; 4).
        sheet.setTab(FunctionTab.ANALYSIS)
        sheet.select(FunctionCell.FROM); sheet.onKey(CalcKey.Clear); sheet.onKey(CalcKey.Operator('-')); sheet.onKey(CalcKey.Digit('5'))
        sheet.select(FunctionCell.TO); sheet.onKey(CalcKey.Clear); sheet.onKey(CalcKey.Digit('5'))
        val points = (sheet.analyze().getOrThrow().intersections as AnalysisItem.Points).points
        assertEquals(listOf("-1" to "1", "2" to "4"), points.map { (x, y) -> show(x) to show(y) })

        // Il grafico mostra entrambe le funzioni: la g resta selezionabile.
        sheet.setTab(FunctionTab.GRAPH)
        sheet.select(FunctionCell.SECOND)
        assertEquals(FunctionCell.SECOND, sheet.selected)
    }

    @Test
    fun secondFunctionIsOptional() {
        val sheet = FunctionSheet()
        sheet.onKey(CalcKey.Variable('X'))
        assertEquals(null, sheet.secondFunction())
        assertEquals(null, sheet.table(rows = 1).getOrThrow()[0].g)
        assertEquals(null, sheet.analyze().getOrThrow().intersections)
        // Una g non valida è un errore come una f non valida.
        sheet.select(FunctionCell.SECOND)
        sheet.onKey(CalcKey.Variable('X'))
        sheet.onKey(CalcKey.Operator('+'))
        assertTrue(sheet.table().isFailure)
    }
}
