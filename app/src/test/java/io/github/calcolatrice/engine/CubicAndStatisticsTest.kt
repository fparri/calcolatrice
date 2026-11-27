package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CubicAndStatisticsTest {

    private fun n(text: String) = (evaluate(text) as EvalResult.Success).value
    private fun show(x: BigDecimal?) = x?.let { ResultFormatter.toInternal(it) }

    private fun cubic(a: String, b: String, c: String, d: String) =
        CubicEquation.solve(n(a), n(b), n(c), n(d)) as CubicSolution.Roots

    @Test
    fun cubicThreeRealRoots() {
        val s = cubic("1", "-6", "11", "-6") // (x−1)(x−2)(x−3)
        assertEquals(listOf("3", "2", "1"), s.real.map(::show))
        assertNull(s.complex)
        assertEquals(listOf("2", "0", "-2"), cubic("1", "0", "-4", "0").real.map(::show)) // x(x²−4)
    }

    @Test
    fun cubicMultipleRootsAreExact() {
        assertEquals(listOf("1", "1", "-2"), cubic("1", "0", "-3", "2").real.map(::show)) // (x−1)²(x+2)
        assertEquals(listOf("2", "2", "2"), cubic("1", "-6", "12", "-8").real.map(::show)) // (x−2)³
        assertEquals(listOf("1", "1", "1"), cubic("1/3", "-1", "1", "-1/3").real.map(::show)) // coefficienti arrotondati
    }

    @Test
    fun cubicComplexRoots() {
        val s = cubic("1", "0", "0", "-1") // x³ = 1
        assertEquals(listOf("1"), s.real.map(::show))
        assertEquals("-0.5", show(s.complex!!.first))
        assertEquals("0.866025403784439", show(s.complex!!.second))

        val t = cubic("2", "0", "0", "-4") // x³ = 2
        assertEquals(listOf("1.25992104989487"), t.real.map(::show))
        assertEquals("-0.629960524947437", show(t.complex!!.first))
        assertEquals("1.09112363597172", show(t.complex!!.second))

        val u = cubic("1", "0", "1", "0") // x(x² + 1)
        assertEquals(listOf("0"), u.real.map(::show))
        assertEquals("0", show(u.complex!!.first))
        assertEquals("1", show(u.complex!!.second))
    }

    @Test
    fun cubicLargeAndDegenerate() {
        assertEquals(listOf("10000000000"), cubic("1", "0", "0", "-10^30").real.map(::show))
        assertEquals(listOf("-1", "-2", "-3"), cubic("-2", "-12", "-22", "-12").real.map(::show)) // a < 0
        val quadratic = CubicEquation.solve(n("0"), n("1"), n("-3"), n("2")) as CubicSolution.Quadratic
        assertEquals(QuadraticSolution.TwoReal::class, quadratic.solution::class)
    }

    @Test
    fun cubicInEquationSheet() {
        val sheet = EquationSheet(EquationMode.CUBIC, mapOf(EquationMode.CUBIC to listOf("1", "-6", "11", "-6")))
        val outcome = sheet.solve() as EquationOutcome.Cubic
        assertEquals(listOf("3", "2", "1"), (outcome.solution as CubicSolution.Roots).real.map(::show))
    }

    // ------------------------------------------------------------ statistica

    @Test
    fun oneVariable() {
        val s = Statistics.oneVariable(listOf("2", "4", "4", "4", "5", "5", "7", "9").map(::n))
        assertEquals(8, s.n)
        assertEquals("40", show(s.sum))
        assertEquals("232", show(s.sumOfSquares))
        assertEquals("5", show(s.mean))
        assertEquals("2", show(s.populationSd))
        assertEquals("2.1380899352994", show(s.sampleSd))
        assertEquals(listOf("2", "4", "4.5", "6", "9"), listOf(s.min, s.q1, s.median, s.q3, s.max).map(::show))

        val odd = Statistics.oneVariable(listOf("5", "1", "3", "2", "4").map(::n))
        assertEquals(listOf("1", "1.5", "3", "4.5", "5"), listOf(odd.min, odd.q1, odd.median, odd.q3, odd.max).map(::show))

        val single = Statistics.oneVariable(listOf(n("7")))
        assertNull(single.sampleSd)
        assertEquals("0", show(single.populationSd))
        assertEquals("7", show(single.q1))
    }

    @Test
    fun regression() {
        val s = Statistics.linearRegression(listOf("1", "2", "3", "4", "5").map(::n), listOf("2", "4", "5", "4", "5").map(::n))
        assertEquals("2.2", show(s.a))
        assertEquals("0.6", show(s.b))
        assertEquals("0.774596669241483", show(s.r))
        assertEquals("3", show(s.meanX))
        assertEquals("4", show(s.meanY))
        assertEquals("1.4142135623731", show(s.sdX))

        val flat = Statistics.linearRegression(listOf("1", "2").map(::n), listOf("3", "3").map(::n))
        assertNull(flat.r)
        assertEquals("0", show(flat.b))
    }

    @Test
    fun statisticsSheet() {
        val sheet = StatisticsSheet()
        fun type(text: String) = text.forEach { sheet.onKey(CalcKey.Digit(it)) }
        assertEquals(1, sheet.rowCount) // solo la riga vuota
        type("4"); sheet.next(); type("8"); sheet.next(); type("6")
        assertEquals(listOf(listOf("4"), listOf("8"), listOf("6")), sheet.rows())
        assertEquals(4, sheet.rowCount)
        val outcome = sheet.solve() as StatOutcome.OneVariable
        assertEquals("6", show(outcome.stats.mean))

        // Cancellare l'unico carattere di una riga la elimina.
        sheet.select(1, 0)
        sheet.onKey(CalcKey.Backspace)
        assertEquals(listOf(listOf("4"), listOf("6")), sheet.rows())
        sheet.deleteRow()
        assertEquals(listOf(listOf("4")), sheet.rows())

        sheet.setMode(StatMode.REGRESSION)
        assertEquals(StatOutcome.NotEnoughData, sheet.solve())
        type("1"); sheet.next(); type("2"); sheet.next(); type("3")
        // Manca la y della seconda riga: viene selezionata.
        val error = sheet.solve() as StatOutcome.Error
        assertEquals(1 to 1, error.row to error.column)
        assertEquals(1 to 1, sheet.row to sheet.column)
        type("7")
        val regression = sheet.solve() as StatOutcome.Regression
        assertEquals("2.5", show(regression.stats.b))

        val restored = StatisticsSheet(StatMode.REGRESSION, mapOf(StatMode.REGRESSION to listOf(listOf("1", "2"), listOf("", ""))))
        assertEquals(1, restored.rows().size) // le righe vuote salvate vengono scartate
    }
}
