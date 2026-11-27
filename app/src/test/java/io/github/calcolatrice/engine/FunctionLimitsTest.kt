package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

class FunctionLimitsTest {

    private fun limit(expression: String, x0: String, side: LimitSide = LimitSide.BOTH) =
        FunctionLimits.limit(UserFunction(expression, AngleUnit.RADIANS), BigDecimal(x0), side)

    private fun value(r: LimitResult): String =
        ResultFormatter.toInternal(((r as LimitResult.Value).value).round(MathContext(15)))

    @Test
    fun finiteLimits() {
        assertEquals("1", value(limit("sin(X)/X", "0")))
        assertEquals(ResultFormatter.toInternal(BigDecimal(-1).divide(BigDecimal(6), MathSupport.MC).round(MathContext(15))), value(limit("(sin(X)-X)/X^3", "0")))
        assertEquals("2", value(limit("(X^2-1)/(X-1)", "1")))
        assertEquals("2.71828182845905", value(limit("(1+X)^(1/X)", "0")))
        assertEquals(Reliability.APPROXIMATE, limit("sin(X)/X", "0").reliability)
    }

    @Test
    fun infiniteAndOneSided() {
        assertEquals(LimitResult.Different(LimitResult.Infinite(-1, Reliability.APPROXIMATE), LimitResult.Infinite(1, Reliability.APPROXIMATE)), limit("1/X", "0"))
        assertEquals(LimitResult.Infinite(1, Reliability.APPROXIMATE), limit("1/X^2", "0"))
        val ln = limit("ln(X)", "0", LimitSide.RIGHT)
        assertEquals(LimitResult.Infinite(-1, Reliability.RISKY), ln) // diverge lentamente: da verificare
        assertEquals(LimitResult.Undefined, limit("√(X)", "0", LimitSide.LEFT))
        assertEquals("0", value(limit("√(X)", "0"))) // solo da destra
        val sign = limit("abs(X)/X", "0") as LimitResult.Different
        assertEquals("-1", value(sign.left))
        assertEquals("1", value(sign.right))
        assertEquals(LimitResult.Infinite(1, Reliability.RISKY), limit("e^(1/X)", "0", LimitSide.RIGHT))
        assertEquals("0", value(limit("e^(1/X)", "0", LimitSide.LEFT)))
    }

    @Test
    fun atInfinityAndOscillations() {
        assertEquals("2", value(limit("(2X+1)/(X-3)", "0", LimitSide.PLUS_INFINITY)))
        assertEquals("0", value(limit("e^X", "0", LimitSide.MINUS_INFINITY)))
        assertEquals(LimitResult.Infinite(1, Reliability.APPROXIMATE), limit("X^2", "0", LimitSide.MINUS_INFINITY))
        assertTrue(limit("sin(1/X)", "0") is LimitResult.Oscillating)
        assertTrue(limit("sin(X)", "0", LimitSide.PLUS_INFINITY) is LimitResult.Oscillating)
        val damped = limit("X·sin(1/X)".replace("·", "*"), "0")
        assertEquals("0", value(damped))
        assertEquals(Reliability.RISKY, damped.reliability)
    }
}
