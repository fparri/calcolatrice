package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EquationsTest {

    private fun n(text: String) = (evaluate(text) as EvalResult.Success).value
    private fun rows(vararg rows: String): List<List<BigDecimal>> =
        rows.map { row -> row.split(' ').map(::n) }

    private fun show(x: BigDecimal) = ResultFormatter.toInternal(x)

    private fun unique(solution: LinearSolution): List<String> =
        (solution as LinearSolution.Unique).values.map(::show)

    @Test
    fun systemTwoUnknowns() {
        assertEquals(listOf("1", "2"), unique(LinearSystem.solve(rows("2 3 8", "1 -1 -1"))))
        // Il primo coefficiente è zero: serve lo scambio di righe.
        assertEquals(listOf("3", "2"), unique(LinearSystem.solve(rows("0 1 2", "1 0 3"))))
        assertEquals(listOf("0.25", "0.25"), unique(LinearSystem.solve(rows("3 1 1", "1 -1 0"))))
    }

    @Test
    fun systemThreeUnknowns() {
        assertEquals(
            listOf("5", "3", "-2"),
            unique(LinearSystem.solve(rows("1 1 1 6", "0 2 5 -4", "2 5 -1 27"))),
        )
        assertEquals(
            listOf("0", "0", "0"),
            unique(LinearSystem.solve(rows("1 2 3 0", "4 5 6 0", "7 8 10 0"))),
        )
    }

    @Test
    fun systemWithoutUniqueSolution() {
        assertEquals(LinearSolution.Infinite, LinearSystem.solve(rows("1 1 1", "2 2 2")))
        assertEquals(LinearSolution.None, LinearSystem.solve(rows("1 1 1", "2 2 3")))
        assertEquals(LinearSolution.Infinite, LinearSystem.solve(rows("0 0 0", "0 0 0")))
        assertEquals(LinearSolution.None, LinearSystem.solve(rows("0 0 1", "0 0 0")))
        assertEquals(LinearSolution.Infinite, LinearSystem.solve(rows("1 2 3 6", "2 4 6 12", "1 1 1 3")))
        assertEquals(LinearSolution.None, LinearSystem.solve(rows("1 2 3 6", "2 4 6 13", "1 1 1 3")))
    }

    @Test
    fun roundingResiduesDoNotHideSingularSystems() {
        // 1/3 a 34 cifre non è un terzo esatto, ma le righe restano proporzionali.
        assertEquals(LinearSolution.Infinite, LinearSystem.solve(rows("1/3 1 1", "1 3 3")))
        assertEquals(LinearSolution.None, LinearSystem.solve(rows("1/3 1 1", "1 3 4")))
        // √2·√2 = 2 solo a meno di residui.
        assertEquals(LinearSolution.Infinite, LinearSystem.solve(rows("√2 1 1", "2 √2 √2")))
    }

    @Test
    fun quadraticRealRoots() {
        val s = QuadraticEquation.solve(n("1"), n("-3"), n("2")) as QuadraticSolution.TwoReal
        assertEquals("2", show(s.x1))
        assertEquals("1", show(s.x2))
        assertEquals(Vertex(n("1.5"), n("-0.25"), isMinimum = true), s.vertex)

        val down = QuadraticEquation.solve(n("-1"), n("0"), n("4")) as QuadraticSolution.TwoReal
        assertEquals(listOf("2", "-2"), listOf(show(down.x1), show(down.x2)))
        assertEquals(false, down.vertex.isMinimum)
        assertEquals("4", show(down.vertex.y))

        val irrational = QuadraticEquation.solve(n("1"), n("0"), n("-2")) as QuadraticSolution.TwoReal
        assertEquals("1.4142135623731", show(irrational.x1))
    }

    @Test
    fun quadraticIsNumericallyStable() {
        // Con la formula scolastica la radice piccola perderebbe tutte le cifre.
        val s = QuadraticEquation.solve(n("1"), n("-1E8"), n("1")) as QuadraticSolution.TwoReal
        assertEquals("100000000", show(s.x1))
        assertEquals("0.00000001", show(s.x2))
    }

    @Test
    fun quadraticDoubleAndComplexRoots() {
        val double = QuadraticEquation.solve(n("1"), n("-2"), n("1")) as QuadraticSolution.DoubleRoot
        assertEquals("1", show(double.x))
        // Con coefficienti arrotondati Δ resta zero.
        val rounded = QuadraticEquation.solve(n("1/3"), n("-2/3"), n("1/3")) as QuadraticSolution.DoubleRoot
        assertEquals("1", show(rounded.x))

        val complex = QuadraticEquation.solve(n("1"), n("2"), n("5")) as QuadraticSolution.ComplexPair
        assertEquals("-1", show(complex.re))
        assertEquals("2", show(complex.im))
        val imaginary = QuadraticEquation.solve(n("1"), n("0"), n("1")) as QuadraticSolution.ComplexPair
        assertEquals("0", show(imaginary.re))
        assertEquals("1", show(imaginary.im))
    }

    @Test
    fun quadraticWithZeroLeadingCoefficient() {
        val linear = QuadraticEquation.solve(n("0"), n("2"), n("4")) as QuadraticSolution.Linear
        assertEquals(listOf("-2"), unique(linear.solution))
        assertEquals(
            QuadraticSolution.Linear(LinearSolution.None),
            QuadraticEquation.solve(n("0"), n("0"), n("5")),
        )
        assertEquals(
            QuadraticSolution.Linear(LinearSolution.Infinite),
            QuadraticEquation.solve(n("0"), n("0"), n("0")),
        )
    }

    @Test
    fun fractionRecognition() {
        assertEquals(Fraction(3.toBigInteger(), 4.toBigInteger()), Fractions.toFraction(n("0.75")))
        assertEquals(Fraction((-5).toBigInteger(), 2.toBigInteger()), Fractions.toFraction(n("-2.5")))
        assertEquals(Fraction(1.toBigInteger(), 7.toBigInteger()), Fractions.toFraction(n("1/7")))
        assertEquals(Fraction(3.toBigInteger(), 10.toBigInteger()), Fractions.toFraction(n("0.1+0.2")))
        assertEquals(Fraction(5.toBigInteger(), 1.toBigInteger()), Fractions.toFraction(n("5")))
        assertEquals(Fraction(355.toBigInteger(), 113.toBigInteger()), Fractions.toFraction(n("355/113")))
        assertNull(Fractions.toFraction(n("√2")))
        assertNull(Fractions.toFraction(n("π")))
        assertNull(Fractions.toFraction(n("1/1000003")))
        assertNull(Fractions.toFraction(n("10^20+0.5")))
    }

    @Test
    fun solutionText() {
        val f = SolutionFormatter(DisplayFormatter(',', '.'))
        assertEquals("3/4", f.value(n("0.75"), asFraction = true))
        assertEquals("0,75", f.value(n("0.75"), asFraction = false))
        assertEquals("−5/2", f.value(n("-2.5"), asFraction = true))
        assertEquals("1/3", f.value(n("1/3"), asFraction = true))
        assertEquals("0,333333333333333", f.value(n("1/3"), asFraction = false))
        // Con la forma esatta anche i radicali: √2 resta √2 (prima era decimale).
        assertEquals("√2", f.value(n("√2"), asFraction = true))
        assertEquals("1,4142135623731", f.value(n("√2"), asFraction = false))
        assertEquals("3,14159265358979", f.value(n("π"), asFraction = false))
        assertEquals("1.000.000/3", f.value(n("1000000/3"), asFraction = true))
        assertEquals("−1 + 2i", f.complex(n("-1"), n("2"), plus = true, asFraction = false))
        assertEquals("−1 − 2i", f.complex(n("-1"), n("2"), plus = false, asFraction = false))
        assertEquals("−i", f.complex(n("0"), n("1"), plus = false, asFraction = false))
        assertEquals("1/2 + (3/2)i", f.complex(n("0.5"), n("1.5"), plus = true, asFraction = true))
        assertEquals("0,5 + 1,5i", f.complex(n("0.5"), n("1.5"), plus = true, asFraction = false))
    }

    @Test
    fun sheetEditingAndSolving() {
        val sheet = EquationSheet()
        fun type(text: String) = text.forEach { c ->
            sheet.onKey(
                when (c) {
                    in '0'..'9' -> CalcKey.Digit(c)
                    '.' -> CalcKey.Decimal
                    else -> CalcKey.Operator(c)
                },
            )
        }
        // 2x + 3y = 8; x − y = −1
        type("2"); sheet.next(); type("3"); sheet.next(); type("8"); sheet.next()
        type("1"); sheet.next(); type("-1"); sheet.next(); type("-1")
        assertEquals(listOf("2", "3", "8", "1", "-1", "-1"), sheet.expressions())
        sheet.onKey(CalcKey.Equals)
        val outcome = sheet.outcome as EquationOutcome.System
        assertEquals(listOf("1", "2"), unique(outcome.solution))

        // Modificare un coefficiente cancella il risultato.
        sheet.onKey(CalcKey.Backspace)
        assertNull(sheet.outcome)

        // Un coefficiente non valido viene selezionato.
        sheet.select(1)
        type("+")
        sheet.select(4)
        val error = sheet.solve() as EquationOutcome.Error
        assertEquals(CalcError.SYNTAX, error.error)
        assertEquals(1, error.cell)
        assertEquals(1, sheet.selected)
    }

    @Test
    fun sheetModesKeepTheirCoefficients() {
        val sheet = EquationSheet()
        sheet.onKey(CalcKey.Digit('5'))
        sheet.setMode(EquationMode.QUADRATIC)
        assertEquals(listOf("", "", ""), sheet.expressions())
        // Celle vuote = 0: 0x² + 0x + 0 = 0.
        assertEquals(
            EquationOutcome.Quadratic(QuadraticSolution.Linear(LinearSolution.Infinite)),
            sheet.solve(),
        )
        sheet.setMode(EquationMode.SYSTEM_2)
        assertEquals("5", sheet.expressions()[0])
        sheet.previous()
        assertEquals(5, sheet.selected)
        sheet.next()
        assertEquals(0, sheet.selected)
        sheet.clearAll()
        assertTrue(sheet.expressions().all { it.isEmpty() })

        val restored = EquationSheet(EquationMode.SYSTEM_3, mapOf(EquationMode.SYSTEM_3 to listOf("1", "2")))
        assertEquals(12, restored.expressions().size)
        assertEquals("2", restored.expressions()[1])
    }
}
