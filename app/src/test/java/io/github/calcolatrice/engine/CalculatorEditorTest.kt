package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorEditorTest {

    /** Applica una sequenza di tasti partendo da uno stato vuoto. */
    private fun type(vararg keys: CalcKey, start: CalculatorState = CalculatorState()): Transition {
        var transition = Transition(start)
        for (key in keys) transition = CalculatorEditor.onKey(transition.state, key, AngleUnit.DEGREES)
        return transition
    }

    private fun digits(text: String): Array<CalcKey> = text.map { c ->
        when (c) {
            '.' -> CalcKey.Decimal
            in "+-*/^" -> CalcKey.Operator(c)
            else -> CalcKey.Digit(c)
        }
    }.toTypedArray()

    private fun expr(vararg keys: CalcKey) = type(*keys).state.expression

    @Test
    fun typingNumbersAndOperators() {
        assertEquals("12+3", expr(*digits("12+3")))
        assertEquals("12-3", expr(*digits("12+-3")))
        assertEquals("5", expr(*digits("05")))
        assertEquals("0.5", expr(*digits(".5")))
        assertEquals("1.5", expr(*digits("1.5.")))
        assertEquals("2+0.", expr(*digits("2+.")))
    }

    @Test
    fun operatorReplacement() {
        assertEquals("2*", expr(*digits("2+*")))
        assertEquals("2*-", expr(*digits("2*-")))
        assertEquals("2+", expr(*digits("2*-+")))
        assertEquals("", expr(CalcKey.Operator('*')))
        assertEquals("-", expr(CalcKey.Operator('-')))
    }

    @Test
    fun implicitMultiplicationIsExplicitInText() {
        assertEquals("2*π", expr(CalcKey.Digit('2'), CalcKey.Constant('π')))
        assertEquals("π*2", expr(CalcKey.Constant('π'), CalcKey.Digit('2')))
        assertEquals("2*sin(", expr(CalcKey.Digit('2'), CalcKey.Function("sin")))
        assertEquals("2*(", expr(CalcKey.Digit('2'), CalcKey.Parentheses))
    }

    @Test
    fun smartParentheses() {
        assertEquals(
            "(2+3)",
            expr(CalcKey.Parentheses, *digits("2+3"), CalcKey.Parentheses),
        )
        assertEquals("(", expr(CalcKey.Parentheses))
        assertEquals("2+(", expr(*digits("2+"), CalcKey.Parentheses))
    }

    @Test
    fun equalsAndContinue() {
        val t = type(*digits("6*7"), CalcKey.Equals)
        assertEquals("@", t.state.expression)
        assertEquals("42", t.state.ans?.toPlainString())
        assertEquals(CompletedCalculation("6*7", "42"), t.completed)

        // Un operatore continua dal risultato
        val cont = type(CalcKey.Operator('+'), CalcKey.Digit('1'), CalcKey.Equals, start = t.state)
        assertEquals("43", cont.state.ans?.toPlainString())
        assertEquals(CompletedCalculation("42+1", "43"), cont.completed)

        // Una cifra inizia un nuovo calcolo
        assertEquals("5", type(CalcKey.Digit('5'), start = t.state).state.expression)
    }

    @Test
    fun ansKeepsFullPrecision() {
        val t = type(*digits("1/3"), CalcKey.Equals, *digits("*3"), CalcKey.Equals)
        assertEquals("1", t.state.ans?.let { ResultFormatter.toInternal(it) })
    }

    @Test
    fun backspace() {
        assertEquals("12", expr(*digits("123"), CalcKey.Backspace))
        assertEquals("2*", expr(CalcKey.Digit('2'), CalcKey.Function("asin"), CalcKey.Backspace))
        val t = type(*digits("10*5"), CalcKey.Equals, CalcKey.Backspace)
        assertEquals("5", t.state.expression)
        assertEquals("", expr(CalcKey.Backspace))
    }

    @Test
    fun errorsKeepExpression() {
        val t = type(*digits("1/0"), CalcKey.Equals)
        assertEquals(CalcError.DIVISION_BY_ZERO, t.state.error)
        assertEquals("1/0", t.state.expression)
        val fixed = type(CalcKey.Backspace, CalcKey.Digit('2'), CalcKey.Equals, start = t.state)
        assertEquals("0.5", fixed.state.ans?.toPlainString())
    }

    @Test
    fun singleNumberNotSavedInHistory() {
        assertNull(type(*digits("5"), CalcKey.Equals).completed)
    }

    @Test
    fun preview() {
        val s = type(*digits("2+3*4")).state
        assertEquals("14", CalculatorEditor.preview(s, AngleUnit.DEGREES)?.toPlainString())
        assertNull(CalculatorEditor.preview(type(*digits("2+")).state, AngleUnit.DEGREES))
        assertNull(CalculatorEditor.preview(type(*digits("25")).state, AngleUnit.DEGREES))
    }

    @Test
    fun postfixKeys() {
        assertEquals("5!", expr(CalcKey.Digit('5'), CalcKey.Factorial))
        assertEquals("3^2", expr(CalcKey.Digit('3'), CalcKey.Square))
        assertEquals("4^-1", expr(CalcKey.Digit('4'), CalcKey.Reciprocal))
        assertEquals("", expr(CalcKey.Percent))
        val afterEquals = type(*digits("2+2"), CalcKey.Equals, CalcKey.Square, CalcKey.Equals)
        assertEquals("16", afterEquals.state.ans?.toPlainString())
    }

    @Test
    fun insertValueFromHistory() {
        val base = type(*digits("2+")).state
        assertEquals("2+(-5)", CalculatorEditor.insertValue(base, "-5").expression)
        val afterNumber = type(*digits("2")).state
        assertEquals("2*7", CalculatorEditor.insertValue(afterNumber, "7").expression)
    }

    @Test
    fun negativeAnsFollowedByPostfix() {
        val minusFive = type(*digits("0-5"), CalcKey.Equals).state
        val squared = type(CalcKey.Square, start = minusFive)
        assertEquals("(-5)^2", ExpressionText.expandAns(squared.state.expression, minusFive.ans!!))
        assertEquals("(−5)^2", DisplayFormatter().format(squared.state.expression, squared.state.ans))
        assertEquals("25", CalculatorEditor.preview(squared.state, AngleUnit.DEGREES)?.toPlainString())
        val done = type(CalcKey.Equals, start = squared.state)
        assertEquals(CompletedCalculation("(-5)^2", "25"), done.completed)
        // Da solo o seguito da un operatore il numero resta senza parentesi.
        assertEquals("-5+1", ExpressionText.expandAns("@+1", minusFive.ans!!))
        assertEquals("-5", ExpressionText.expandAns("@", minusFive.ans!!))
    }

    @Test
    fun negativeHistoryValueOnEmptyDisplay() {
        val inserted = CalculatorEditor.insertValue(CalculatorState(), "-5")
        assertEquals("@", inserted.expression)
        assertEquals("-5", inserted.ans?.toPlainString())
        val squared = type(CalcKey.Square, CalcKey.Equals, start = inserted)
        assertEquals("25", squared.state.ans?.toPlainString())
        // Una cifra digitata subito dopo sostituisce il valore, come dopo "=".
        assertEquals("7", type(CalcKey.Digit('7'), start = inserted).state.expression)
    }

    @Test
    fun scientificNotationHistoryValueStaysAtomic() {
        val base = type(*digits("2+")).state
        val inserted = CalculatorEditor.insertValue(base, "1.5E20")
        assertEquals("2+(1.5E20)", inserted.expression)
        assertEquals("2+(1.5E20)*3", type(CalcKey.Digit('3'), start = inserted).state.expression)
    }

    @Test
    fun backspaceNeverLeavesEmptyExponent() {
        val small = CalculatorState(expression = "@", ans = java.math.BigDecimal("2.5E-10"), justEvaluated = true)
        val one = type(CalcKey.Backspace, start = small).state
        assertEquals("2.5E-1", one.expression)
        val two = type(CalcKey.Backspace, start = one).state
        assertEquals("2.5", two.expression)
        val plusOne = type(CalcKey.Operator('+'), CalcKey.Digit('1'), start = two).state
        assertEquals("2.5+1", plusOne.expression)
        val big = CalculatorState(expression = "@", ans = java.math.BigDecimal("3E20"), justEvaluated = true)
        assertEquals("3E2", type(CalcKey.Backspace, start = big).state.expression)
        assertEquals("3", type(CalcKey.Backspace, CalcKey.Backspace, start = big).state.expression)
    }
}
