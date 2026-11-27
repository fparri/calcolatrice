package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FunctionStudyTest {

    private val display = DisplayFormatter('.', null)
    private val text = InequalityText({ display.format(ResultFormatter.toInternal(it.round(java.math.MathContext(12)))) }, italianIntervals = true)

    private fun study(expression: String, from: String = "-10", to: String = "10") =
        FunctionStudy.study(UserFunction(expression, AngleUnit.RADIANS), BigDecimal(from), BigDecimal(to))

    private fun show(x: BigDecimal) = ResultFormatter.toInternal(x.round(java.math.MathContext(12)))
    private fun points(list: List<StudyPoint>) = list.map { show(it.x) to show(it.y) }

    @Test
    fun cubic() {
        val s = study("X^3-3X")
        assertEquals(Parity.ODD, s.parity)
        assertEquals("0", show(s.yIntercept!!))
        assertEquals(listOf("-1.73205080757", "0", "1.73205080757"), s.zeros.map(::show))
        assertEquals("x < −1 ∨ x > 1", text.algebraic(s.increasing))
        assertEquals("−1 < x < 1", text.algebraic(s.decreasing))
        assertEquals(listOf("-1" to "2"), points(s.maxima))
        assertEquals(listOf("1" to "-2"), points(s.minima))
        assertEquals("x > 0", text.algebraic(s.convex))
        assertEquals("x < 0", text.algebraic(s.concave))
        assertEquals(listOf("0" to "0"), points(s.inflections))
        assertTrue(s.vertical.isEmpty() && s.lines.isEmpty())
        assertTrue(s.domain.isAll)
        assertEquals(Reliability.RELIABLE, s.domainReliability)
    }

    @Test
    fun rational() {
        val s = study("(X-1)/(X+2)")
        assertEquals("x ≠ −2", text.algebraic(s.domain))
        assertEquals(listOf("-2"), s.vertical.map(::show))
        assertEquals(listOf(Asymptote.Line(BigDecimal.ZERO, BigDecimal.ONE, 0)), s.lines)
        assertEquals("x < −2 ∨ x > 1", text.algebraic(s.positive))
        assertEquals("x ≠ −2", text.algebraic(s.increasing)) // crescente in ]−∞; −2[ e in ]−2; +∞[
        assertEquals("]−∞; −2[ ∪ ]−2; +∞[", text.intervals(s.increasing))
        assertEquals(Parity.NONE, s.parity)
    }

    @Test
    fun logarithmAndRoot() {
        val ln = study("ln(X)")
        assertEquals("x > 0", text.algebraic(ln.domain))
        assertEquals(listOf("0"), ln.vertical.map(::show))
        assertEquals(listOf("1"), ln.zeros.map(::show))
        assertEquals("x > 0", text.algebraic(ln.increasing))
        assertEquals("x > 0", text.algebraic(ln.concave))
        assertTrue(ln.lines.isEmpty()) // ln x cresce senza limite: nessun asintoto orizzontale

        val root = study("√(X-2)")
        assertEquals("x ≥ 2", text.algebraic(root.domain))
        assertTrue(root.vertical.isEmpty())
        assertEquals("x > 2", text.algebraic(root.positive)) // in 2 vale 0
    }

    @Test
    fun evenPoleCornerAndOblique() {
        val inverseSquare = study("1/X^2")
        assertEquals(Parity.EVEN, inverseSquare.parity)
        assertEquals(listOf("0"), inverseSquare.vertical.map(::show))
        assertEquals(listOf(Asymptote.Line(BigDecimal.ZERO, BigDecimal.ZERO, 0)), inverseSquare.lines)

        val abs = study("abs(X)")
        assertEquals(listOf("0" to "0"), points(abs.minima))
        assertTrue(abs.inflections.isEmpty())

        val oblique = study("X+1/X")
        assertEquals(listOf(Asymptote.Line(BigDecimal.ONE, BigDecimal.ZERO, 0)), oblique.lines)
        assertEquals(listOf("-1" to "-2"), points(oblique.maxima))
        assertEquals(listOf("1" to "2"), points(oblique.minima))

        val exp = study("e^X")
        assertEquals(listOf(Asymptote.Line(BigDecimal.ZERO, BigDecimal.ZERO, -1)), exp.lines)
        assertTrue(exp.zeros.isEmpty())
    }

    @Test
    fun tangentHasManyPoles() {
        val tan = study("tan(X)", "-3", "3")
        assertEquals(listOf("-1.57079632679", "1.57079632679"), tan.vertical.map(::show))
        assertEquals(Parity.ODD, tan.parity)
    }
}
