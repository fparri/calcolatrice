package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Casi limite emersi dall'analisi del motore: ognuno era un risultato sbagliato. */
class NumericFixesTest {

    private fun n(text: String) = BigDecimal(text)
    private fun show(x: BigDecimal) = ResultFormatter.toInternal(x)
    private fun eval(text: String) = evaluate(text)
    private fun solve(expression: String, start: String = "0", unit: AngleUnit = AngleUnit.DEGREES) =
        NumericSolver.solve(expression, unit, null, emptyMap(), 'X', BigDecimal(start))

    private fun solved(expression: String, start: String = "0", unit: AngleUnit = AngleUnit.DEGREES): String =
        show((solve(expression, start, unit) as EvalResult.Success).value)

    private fun noSolution(expression: String, start: String = "0") =
        assertEquals(EvalResult.Failure(CalcError.NO_SOLUTION), solve(expression, start))

    // ------------------------------------------------------------ potenze

    @Test
    fun powersOfOneWithHugeExponents() {
        assertEquals(EvalResult.Success(BigDecimal.ONE), eval("1^1E400"))
        assertEquals(EvalResult.Success(BigDecimal.ONE), eval("(-1)^1E400"))
        assertEquals(EvalResult.Success(BigDecimal.ONE.negate()), eval("(-1)^(1E30+1)"))
        assertEquals(EvalResult.Failure(CalcError.UNDEFINED), eval("(-1)^0.5"))
    }

    // ------------------------------------------------------------ SOLVE

    @Test
    fun solveRejectsFunctionsThatNeverReachZero() {
        noSolution("(X-1)^2+1E-30")
        noSolution("X^2+1E-30", "5")
        noSolution("(X-1)^2+1E-20")
        noSolution("X^2+1")
        noSolution("1/X")
    }

    @Test
    fun solveFindsTangentRoots() {
        assertEquals("1", solved("(X-1)^2"))
        assertEquals("0", solved("X^2", "5"))
        assertEquals("1.4142135623731", solved("(X-√2)^2"))
        // sin(X) − 1 tocca lo zero senza attraversarlo (π/2 in radianti).
        assertEquals("1.5707963267949", solved("sin(X)-1", "1", AngleUnit.RADIANS))
    }

    @Test
    fun solveFindsRootsFarFromTheStart() {
        assertEquals("1E25", solved("X-1E25"))
        assertEquals("1E30", solved("X-1E30"))
        assertEquals("-1E26", solved("1E-26X+1"))
        assertEquals("1E35", solved("X/1E35-1"))
    }

    @Test
    fun solveStillWorksOnOrdinaryEquations() {
        assertEquals("1.4142135623731", solved("X^2-2", "1"))
        assertEquals("30", solved("sin(X)-0.5"))
        assertEquals("0", solved("X^3-X"))
        assertEquals("115.129254649702", solved("e^X-1E50"))
        assertEquals("0.739085133215161", solved("cos(X)-X", "0", AngleUnit.RADIANS))
    }

    // ------------------------------------------------------------ sistemi lineari

    private fun unique(solution: LinearSolution): List<String> =
        (solution as LinearSolution.Unique).values.map(::show)

    private fun system(vararg rows: String) = LinearSystem.solve(rows.map { r -> r.split(' ').map(::n) })

    @Test
    fun systemsWithVeryDifferentScales() {
        assertEquals(listOf("1", "2"), unique(system("1E15 0 1E15", "0 1E-15 2E-15")))
        assertEquals(listOf("1", "1"), unique(system("1 0 1", "0 1E-26 1E-26")))
        assertEquals(listOf("1", "1"), unique(system("1E30 0 1E30", "0 1E-10 1E-10")))
        // Colonne di scala diversa: y è enorme ma il sistema è determinato.
        assertEquals(listOf("0", "1E30"), unique(system("1 1E-30 1", "1 2E-30 2")))
    }

    @Test
    fun singularSystemsAreStillRecognised() {
        val third = (eval("1/3") as EvalResult.Success).value
        val twoThirds = (eval("2/3") as EvalResult.Success).value
        val singular = LinearSystem.solve(
            listOf(listOf(n("1"), n("2"), n("3")), listOf(third, twoThirds, BigDecimal.ONE)),
        )
        assertEquals(LinearSolution.Infinite, singular)
        assertEquals(LinearSolution.None, system("1 1 1", "1 1 2"))
        assertEquals(LinearSolution.None, system("0 0 1", "1 1 2"))
        assertEquals(LinearSolution.Infinite, system("0 0 0", "1 1 2"))
        // Incoerente per una differenza piccola ma vera.
        assertEquals(LinearSolution.None, system("1 1 1", "1 1 1.00000000000000000001"))
    }

    // ------------------------------------------------------------ secondo e terzo grado

    @Test
    fun smallQuadraticRootsAreKept() {
        val big = QuadraticEquation.solve(n("1"), n("-1E26"), n("1")) as QuadraticSolution.TwoReal
        assertEquals("1E26", show(big.x1))
        assertEquals("1E-26", show(big.x2))
        val medium = QuadraticEquation.solve(n("1"), n("-1E13"), n("1")) as QuadraticSolution.TwoReal
        assertEquals("1E-13", show(medium.x2))
    }

    @Test
    fun smallCubicRootsAreKept() {
        val roots = CubicEquation.solve(n("1"), n("-1E13"), n("1"), n("0")) as CubicSolution.Roots
        assertEquals(listOf("10000000000000", "1E-13", "0"), roots.real.map(::show))
        // (x + 1)(x² + 1): la parte reale delle radici complesse è esattamente 0.
        val complex = CubicEquation.solve(n("1"), n("1"), n("1"), n("1")) as CubicSolution.Roots
        assertEquals(listOf("-1"), complex.real.map(::show))
        assertEquals("0", show(complex.complex!!.first))
        assertEquals("1", show(complex.complex!!.second))
    }

    @Test
    fun cubicResidueRootsBecomeZero() {
        // d è un residuo di 1/3·3 − 1: 0 è una radice e va mostrata come 0.
        val d = (eval("1/3*3-1") as EvalResult.Success).value
        val roots = CubicEquation.solve(n("1"), n("-3"), n("2"), d) as CubicSolution.Roots
        assertTrue(roots.real.map(::show).contains("0"))
    }
}
