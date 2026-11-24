package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorSessionTest {

    private fun CalculatorSession.type(text: String) = text.forEach { c ->
        onKey(
            when (c) {
                '.' -> CalcKey.Decimal
                '=' -> CalcKey.Equals
                in "+-*/^" -> CalcKey.Operator(c)
                else -> CalcKey.Digit(c)
            },
        )
    }

    @Test
    fun historyAndLastExpression() {
        val session = CalculatorSession()
        session.type("2+3")
        assertEquals("5", session.preview?.toPlainString())
        session.type("=")
        assertEquals("2+3", session.lastExpression)
        assertEquals(listOf(HistoryEntry("2+3", "5")), session.history)
        assertNull(session.preview)

        session.type("*2=")
        assertEquals("5*2", session.lastExpression)
        assertEquals(HistoryEntry("5*2", "10"), session.history.first())

        session.type("7")
        assertNull(session.lastExpression)
    }

    @Test
    fun angleUnitUpdatesPreview() {
        val session = CalculatorSession()
        session.onKey(CalcKey.Function("sin"))
        session.type("90")
        assertEquals("1", session.preview?.toPlainString())
        session.toggleAngleUnit()
        assertEquals(AngleUnit.RADIANS, session.angleUnit)
        assertEquals("0.893996663600558", session.preview?.let { ResultFormatter.toInternal(it) })
    }

    @Test
    fun reuseHistoryValue() {
        val session = CalculatorSession()
        session.type("6*7=")
        session.type("1+")
        session.useHistoryValue(session.history.first())
        assertEquals("1+42", session.calc.expression)
        session.clearHistory()
        assertEquals(emptyList<HistoryEntry>(), session.history)
    }
}
