package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Funzioni del menu MATH: valori, errori, editor e testo a schermo. */
class MathFunctionsTest {

    private fun value(text: String): String = when (val r = evaluate(text)) {
        is EvalResult.Success -> ResultFormatter.toInternal(r.value)
        is EvalResult.Failure -> r.error.name
    }

    @Test
    fun rootsAndLogarithms() {
        assertEquals("2", value("root(3;8)"))
        assertEquals("-3", value("root(3;-27)"))
        assertEquals("0.2", value("root(4;0.0016)"))
        assertEquals("0.5", value("root(-2;4)"))
        assertEquals("1.14869835499704", value("root(5;2)"))
        assertEquals("UNDEFINED", value("root(2;-4)"))
        assertEquals("UNDEFINED", value("root(0;5)"))
        assertEquals("3", value("log(2;8)"))
        assertEquals("-3", value("log(10;0.001)"))
        assertEquals("0.333333333333333", value("log(8;2)"))
        assertEquals("2", value("log(100)"))
        assertEquals("UNDEFINED", value("log(1;5)"))
        assertEquals("UNDEFINED", value("log(2;-8)"))
        assertEquals("3.5", value("abs(-3.5)"))
        assertEquals("7", value("abs(3-10)"))
    }

    @Test
    fun combinatorics() {
        assertEquals("120", value("ncr(10;3)"))
        assertEquals("720", value("npr(10;3)"))
        assertEquals("1", value("ncr(5;0)"))
        assertEquals("0", value("ncr(3;5)"))
        assertEquals("1.00891344545564E29", value("ncr(100;50)"))
        assertEquals("5E39", value("ncr(1E20;2)"))
        assertEquals("UNDEFINED", value("ncr(5.5;2)"))
        assertEquals("UNDEFINED", value("ncr(-5;2)"))
        assertEquals("OVERFLOW", value("ncr(100000;50000)"))
        assertEquals("OVERFLOW", value("npr(5000;4000)"))
        // Con implicita: 2·C(4, 2) = 12.
        assertEquals("12", value("2ncr(4;2)"))
    }

    @Test
    fun gcdAndLcm() {
        assertEquals("6", value("gcd(12;18)"))
        assertEquals("3", value("gcd(12;18;27)"))
        assertEquals("12", value("lcm(4;6)"))
        assertEquals("60", value("lcm(4;6;10)"))
        assertEquals("6", value("gcd(-12;18)"))
        assertEquals("0", value("lcm(0;5)"))
        assertEquals("UNDEFINED", value("gcd(1.5;3)"))
        assertEquals("SYNTAX", value("gcd(12)"))
    }

    @Test
    fun distributions() {
        assertEquals("0.24609375", value("binp(10;0.5;5)"))
        assertEquals("0.164261985217236", value("binp(20;0.3;7)"))
        assertEquals("0.77227179741816", value("binc(20;0.3;7)"))
        assertEquals("0", value("binp(10;0.5;11)"))
        assertEquals("1", value("binc(10;0.5;10)"))
        assertEquals("UNDEFINED", value("binp(10;1.5;5)"))
        assertEquals("0.682689492137086", value("normc(-1;1)"))
        assertEquals("0.950004209703559", value("normc(-1.96;1.96)"))
        assertEquals("0.382924922548026", value("normc(170;180;175;10)"))
        assertEquals("0.5", value("normc(0;1E99)"))
        // Coda minuscola: tutte le cifre corrette.
        assertEquals("7.61985302416053E-24", value("normc(-1E99;-10)"))
        assertEquals("UNDEFINED", value("normc(0;1;0;-1)"))
        assertEquals("SYNTAX", value("normc(0;1;2)"))
    }

    @Test
    fun separatorsOutsideFunctionsAreErrors() {
        assertEquals("SYNTAX", value("(1;2)"))
        assertEquals("SYNTAX", value("1;2"))
        assertEquals("SYNTAX", value("sin(1;2)"))
        assertEquals("SYNTAX", value("root(3)"))
    }

    // ------------------------------------------------------------ editor

    private fun type(vararg keys: CalcKey): String {
        var state = CalculatorState()
        for (key in keys) state = CalculatorEditor.onKey(state, key, AngleUnit.DEGREES).state
        return state.expression
    }

    private fun d(c: Char) = CalcKey.Digit(c)

    @Test
    fun separatorKeyOnlyInsideMultiArgumentFunctions() {
        assertEquals("ncr(5;2", type(CalcKey.Function("ncr"), d('5'), CalcKey.Separator, d('2')))
        // Non dopo "(" o dentro sin(, né fuori dalle funzioni.
        assertEquals("ncr(", type(CalcKey.Function("ncr"), CalcKey.Separator))
        assertEquals("sin(5", type(CalcKey.Function("sin"), d('5'), CalcKey.Separator))
        assertEquals("5", type(d('5'), CalcKey.Separator))
        // Dentro una parentesi annidata vale la funzione più interna.
        assertEquals("gcd((2+4);9", type(CalcKey.Function("gcd"), CalcKey.OpenParen, d('2'), CalcKey.Operator('+'), d('4'), CalcKey.CloseParen, CalcKey.Separator, d('9')))
        assertEquals("gcd((2+4", type(CalcKey.Function("gcd"), CalcKey.OpenParen, d('2'), CalcKey.Operator('+'), d('4'), CalcKey.Separator))
        // Dopo ";" si può scrivere un numero negativo, ma non altri operatori.
        assertEquals("log(2;-", type(CalcKey.Function("log"), d('2'), CalcKey.Separator, CalcKey.Operator('-')))
        assertEquals("log(2;", type(CalcKey.Function("log"), d('2'), CalcKey.Separator, CalcKey.Operator('*')))
    }

    @Test
    fun backspaceRemovesWholeFunctionNames() {
        assertEquals("", type(CalcKey.Function("normc"), CalcKey.Backspace))
        assertEquals("ncr(5", type(CalcKey.Function("ncr"), d('5'), CalcKey.Separator, CalcKey.Backspace))
    }

    @Test
    fun openFunctionTracksNesting() {
        assertEquals("log", ExpressionText.openFunction("log(2"))
        assertEquals("", ExpressionText.openFunction("log((2"))
        assertEquals("log", ExpressionText.openFunction("log((2)"))
        assertEquals(null, ExpressionText.openFunction("log(2)"))
        assertEquals("ncr", ExpressionText.openFunction("sin(ncr(5"))
    }

    // ------------------------------------------------------------ testo a schermo

    @Test
    fun displayNames() {
        val en = DisplayFormatter('.', ',')
        assertEquals("nCr(10; 3)", en.format("ncr(10;3)"))
        assertEquals("ⁿ√(3; 8)", en.format("root(3;8)"))
        assertEquals("gcd(12; 18)", en.format("gcd(12;18)"))
        assertEquals("NormC(−1; 1)", en.format("normc(-1;1)"))
        assertEquals("sin⁻¹(0.5)", en.format("asin(0.5)"))
        val it = DisplayFormatter(',', '.', mapOf("gcd" to "MCD", "lcm" to "mcm"))
        assertEquals("MCD(1.200; 18)", it.format("gcd(1200;18)"))
        assertEquals("mcm(4; 6)", it.format("lcm(4;6)"))
        assertEquals("log(2; 8,5)", it.format("log(2;8.5)"))
    }

    @Test
    fun cursorPositionsSkipFunctionNames() {
        val layout = DisplayFormatter('.', ',').layout("ncr(5;2)")
        // "nCr(" occupa le posizioni 0–3, "5" è alla 4, "; " alla 5, "2" alla 7.
        assertEquals(4, layout.positions[4])
        assertEquals(5, layout.positions[5])
        assertEquals(7, layout.positions[6])
    }
}
