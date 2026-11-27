package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InequalitiesTest {

    private val solutions = SolutionFormatter(DisplayFormatter(',', '.'))
    private val text = InequalityText({ solutions.value(it, asFraction = true) }, italianIntervals = true)
    private val english = InequalityText({ SolutionFormatter(DisplayFormatter('.', ',')).value(it, asFraction = true) }, italianIntervals = false)

    private fun n(s: String) = BigDecimal(s)
    private fun solve(a: String, b: String, c: String, r: Relation) = Inequalities.solve(n(a), n(b), n(c), r)
    private fun algebraic(a: String, b: String, c: String, r: Relation) = text.algebraic(solve(a, b, c, r))
    private fun intervals(a: String, b: String, c: String, r: Relation) = text.intervals(solve(a, b, c, r))

    @Test
    fun secondDegreeWithTwoRoots() {
        // x² − 2x − 3 = (x + 1)(x − 3)
        assertEquals("x < −1 ∨ x > 3", algebraic("1", "-2", "-3", Relation.GREATER))
        assertEquals("x ≤ −1 ∨ x ≥ 3", algebraic("1", "-2", "-3", Relation.GREATER_EQUAL))
        assertEquals("−1 < x < 3", algebraic("1", "-2", "-3", Relation.LESS))
        assertEquals("−1 ≤ x ≤ 3", algebraic("1", "-2", "-3", Relation.LESS_EQUAL))
        // a < 0: i versi si scambiano.
        assertEquals("−1 < x < 3", algebraic("-1", "2", "3", Relation.GREATER))
        assertEquals("]−∞; −1[ ∪ ]3; +∞[", intervals("1", "-2", "-3", Relation.GREATER))
        assertEquals("[−1; 3]", intervals("1", "-2", "-3", Relation.LESS_EQUAL))
        assertEquals("(−∞, −1) ∪ (3, +∞)", english.intervals(solve("1", "-2", "-3", Relation.GREATER)))
    }

    @Test
    fun irrationalAndFractionalRoots() {
        assertEquals("−√2 < x < √2", algebraic("1", "0", "-2", Relation.LESS))
        assertEquals("x ≤ −1/2 ∨ x ≥ 2/3", algebraic("6", "-1", "-2", Relation.GREATER_EQUAL))
    }

    @Test
    fun doubleRoot() {
        // (x − 2)²
        assertEquals("x ≠ 2", algebraic("1", "-4", "4", Relation.GREATER))
        assertTrue(solve("1", "-4", "4", Relation.GREATER_EQUAL).isAll)
        assertTrue(solve("1", "-4", "4", Relation.LESS).isEmpty)
        assertEquals("x = 2", algebraic("1", "-4", "4", Relation.LESS_EQUAL))
        assertEquals("{2}", intervals("1", "-4", "4", Relation.LESS_EQUAL))
        assertEquals("]−∞; 2[ ∪ ]2; +∞[", intervals("1", "-4", "4", Relation.GREATER))
    }

    @Test
    fun noRealRoots() {
        assertTrue(solve("1", "0", "1", Relation.GREATER).isAll)
        assertTrue(solve("1", "0", "1", Relation.LESS_EQUAL).isEmpty)
        assertTrue(solve("-1", "0", "-1", Relation.LESS).isAll)
        assertNull(text.algebraic(SolutionSet.ALL))
        assertNull(text.intervals(SolutionSet.NONE))
        assertEquals("]−∞; +∞[", text.intervals(SolutionSet.ALL))
    }

    @Test
    fun firstDegree() {
        assertEquals("x > 3/2", algebraic("0", "2", "-3", Relation.GREATER))
        // Coefficiente negativo: il verso si inverte.
        assertEquals("x < 3/2", algebraic("0", "-2", "3", Relation.GREATER))
        assertEquals("x ≤ 5", algebraic("0", "1", "-5", Relation.LESS_EQUAL))
        // Costanti: 3 > 0 sempre vera, −3 > 0 mai, 0 ≥ 0 sempre.
        assertTrue(solve("0", "0", "3", Relation.GREATER).isAll)
        assertTrue(solve("0", "0", "-3", Relation.GREATER).isEmpty)
        assertTrue(solve("0", "0", "0", Relation.GREATER_EQUAL).isAll)
        assertTrue(solve("0", "0", "0", Relation.GREATER).isEmpty)
    }

    @Test
    fun systems() {
        val outside = solve("1", "-2", "-3", Relation.GREATER) // x < −1 ∨ x > 3
        val upTo5 = solve("0", "1", "-5", Relation.LESS_EQUAL) // x ≤ 5
        assertEquals("x < −1 ∨ 3 < x ≤ 5", text.algebraic(Inequalities.intersect(outside, upTo5)))
        val notTwo = solve("1", "-4", "4", Relation.GREATER) // x ≠ 2
        val between = solve("1", "-4", "3", Relation.LESS_EQUAL) // 1 ≤ x ≤ 3
        assertEquals("1 ≤ x < 2 ∨ 2 < x ≤ 3", text.algebraic(Inequalities.intersect(notTwo, between)))
        // Estremi comuni: chiuso solo se lo è in entrambe.
        val atLeast3 = solve("0", "1", "-3", Relation.GREATER_EQUAL)
        val upTo3 = solve("0", "1", "-3", Relation.LESS_EQUAL)
        val above3 = solve("0", "1", "-3", Relation.GREATER)
        assertEquals("x = 3", text.algebraic(Inequalities.intersect(atLeast3, upTo3)))
        assertTrue(Inequalities.intersect(above3, upTo3).isEmpty)
    }

    @Test
    fun sheetSolvesInequalitiesAndSystems() {
        val sheet = EquationSheet(EquationMode.INEQUALITY)
        fun typeCell(index: Int, text: String) {
            sheet.select(index)
            for (ch in text) sheet.onKey(if (ch == '-') CalcKey.Operator('-') else CalcKey.Digit(ch))
        }
        typeCell(0, "1"); typeCell(1, "-2"); typeCell(2, "-3")
        val single = sheet.solve() as EquationOutcome.Inequality
        assertEquals(false, single.system)
        assertEquals("x < −1 ∨ x > 3", text.algebraic(single.solution))
        sheet.cycleRelation(0) // ≥
        typeCell(4, "1"); typeCell(5, "-5")
        sheet.cycleRelation(1); sheet.cycleRelation(1); sheet.cycleRelation(1) // ≤
        val system = sheet.solve() as EquationOutcome.Inequality
        assertEquals(true, system.system)
        assertEquals("x ≤ −1 ∨ 3 ≤ x ≤ 5", text.algebraic(system.solution))
    }

    @Test
    fun systemOfThree() {
        val sheet = EquationSheet(EquationMode.INEQUALITY)
        fun typeCell(index: Int, text: String) {
            sheet.select(index)
            for (ch in text) sheet.onKey(if (ch == '-') CalcKey.Operator('-') else CalcKey.Digit(ch))
        }
        // x > 0, x − 5 < 0, x² − 4 ≥ 0  →  2 ≤ x < 5
        typeCell(1, "1")
        typeCell(4, "1"); typeCell(5, "-5")
        sheet.cycleRelation(1); sheet.cycleRelation(1) // <
        typeCell(6, "1"); typeCell(8, "-4")
        sheet.cycleRelation(2) // ≥
        val system = sheet.solve() as EquationOutcome.Inequality
        assertEquals(3, system.count)
        assertEquals("2 ≤ x < 5", text.algebraic(system.solution))
    }

    private fun rational(n: List<String>, d: List<String>, r: Relation) =
        Inequalities.solveRational(n.map(::n), d.map(::n), r)

    @Test
    fun rationalInequalities() {
        // (x − 1)/(x + 2) ≥ 0: il −2 è escluso anche se la disequazione ammette l'uguale.
        val first = rational(listOf("0", "1", "-1"), listOf("0", "1", "2"), Relation.GREATER_EQUAL)
        assertEquals("x < −2 ∨ x ≥ 1", text.algebraic(first.solution))
        assertEquals("]−∞; −2[ ∪ [1; +∞[", text.intervals(first.solution))
        assertEquals(listOf("-2"), first.excluded.map { it.stripTrailingZeros().toPlainString() })

        // (x² − 1)/(x − 1) > 0: è x + 1 > 0 tranne in x = 1, dove la frazione non esiste.
        val hole = rational(listOf("1", "0", "-1"), listOf("0", "1", "-1"), Relation.GREATER)
        assertEquals("−1 < x < 1 ∨ x > 1", text.algebraic(hole.solution))

        // x / x² ≥ 0: lo zero del numeratore è anche del denominatore, quindi escluso.
        val shared = rational(listOf("0", "1", "0"), listOf("1", "0", "0"), Relation.GREATER_EQUAL)
        assertEquals("x > 0", text.algebraic(shared.solution))

        // 1/(x² + 1) > 0 per ogni x; (x² − 4)/(x² + 1) ≤ 0 tra −2 e 2.
        assertTrue(rational(listOf("0", "0", "1"), listOf("1", "0", "1"), Relation.GREATER).solution.isAll)
        assertEquals("−2 ≤ x ≤ 2", text.algebraic(rational(listOf("1", "0", "-4"), listOf("1", "0", "1"), Relation.LESS_EQUAL).solution))

        // (x + 3)/(x² − 4) < 0: segni alternati su quattro tratti.
        assertEquals("x < −3 ∨ −2 < x < 2", text.algebraic(rational(listOf("0", "1", "3"), listOf("1", "0", "-4"), Relation.LESS).solution))
    }

    @Test
    fun rationalWithZeroDenominatorIsUndefined() {
        val sheet = EquationSheet(EquationMode.RATIONAL)
        sheet.select(1)
        sheet.onKey(CalcKey.Digit('1'))
        val outcome = sheet.solve()
        assertEquals(EquationOutcome.Error(CalcError.UNDEFINED), outcome)
    }
}
