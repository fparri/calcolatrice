package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluatorTest {

    private fun eval(expression: String, unit: AngleUnit = AngleUnit.DEGREES): String =
        when (val r = evaluate(expression, unit)) {
            is EvalResult.Success -> ResultFormatter.toInternal(r.value)
            is EvalResult.Failure -> r.error.name
        }

    @Test
    fun basicArithmetic() {
        assertEquals("5", eval("2+3"))
        assertEquals("-1", eval("2-3"))
        assertEquals("6", eval("2*3"))
        assertEquals("2.5", eval("5/2"))
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("14", eval("2+3*4"))
        assertEquals("20", eval("(2+3)*4"))
    }

    @Test
    fun precisionAndRounding() {
        assertEquals("0.333333333333333", eval("1/3"))
        assertEquals("1", eval("1/3*3"))
        assertEquals("0.666666666666667", eval("2/3"))
    }

    @Test
    fun unaryAndPower() {
        assertEquals("-4", eval("-2^2"))
        assertEquals("4", eval("(-2)^2"))
        assertEquals("512", eval("2^3^2"))
        assertEquals("0.125", eval("2^-3"))
        assertEquals("1.4142135623731", eval("2^0.5"))
        assertEquals("UNDEFINED", eval("(-8)^0.5"))
        assertEquals("DIVISION_BY_ZERO", eval("0^-1"))
        assertEquals("1", eval("0^0"))
        assertEquals("1E1000", eval("10^1000"))
        assertEquals("OVERFLOW", eval("10^10001"))
    }

    @Test
    fun implicitMultiplication() {
        assertEquals("6.28318530717959", eval("2π"))
        assertEquals("15", eval("3(4+1)"))
        assertEquals("1", eval("2sin(30)"))
        assertEquals("6", eval("(2)(3)"))
    }

    @Test
    fun postfixOperators() {
        assertEquals("120", eval("5!"))
        assertEquals("1", eval("0!"))
        assertEquals("0.5", eval("50%"))
        assertEquals("UNDEFINED", eval("2.5!"))
        assertEquals("UNDEFINED", eval("(-1)!"))
        assertEquals("OVERFLOW", eval("5000!"))
        assertEquals("4.02387260077094E2567", eval("1000!"))
    }

    @Test
    fun percentRelativeToPreviousValue() {
        assertEquals("220", eval("200+10%"))
        assertEquals("180", eval("200-10%"))
        assertEquals("5", eval("50*10%"))
        assertEquals("500", eval("50/10%"))
        assertEquals("5.5", eval("2+3+10%"))
        assertEquals("160", eval("100+(50+10)%"))
        assertEquals("200.2", eval("200+10%*2")) // il termine non è più una percentuale semplice
        assertEquals("201", eval("200+10%^0")) // idem dopo una potenza
        assertEquals("0.1", eval("10%"))
    }

    @Test
    fun squareRootAppliesToNextOperand() {
        assertEquals("6", eval("√9!"))
        assertEquals("4", eval("√4^2"))
        assertEquals("0.05", eval("√25%"))
        assertEquals("2", eval("√√16"))
        assertEquals("5", eval("√(9+16)"))
        assertEquals("6", eval("2√9"))
    }

    @Test
    fun errors() {
        assertEquals("DIVISION_BY_ZERO", eval("1/0"))
        assertEquals("SYNTAX", eval("2+"))
        assertEquals("SYNTAX", eval("()"))
        assertEquals("SYNTAX", eval("2)"))
        assertEquals("SYNTAX", eval("."))
        assertEquals("UNDEFINED", eval("√-4"))
        assertEquals("UNDEFINED", eval("ln(0)"))
        assertEquals("UNDEFINED", eval("asin(2)"))
        assertEquals("SYNTAX", eval("@+1")) // nessun risultato precedente
    }

    @Test
    fun autoCloseParentheses() {
        assertEquals("5", eval("(2+3"))
        assertEquals("0.5", eval("sin(30"))
    }

    @Test
    fun trigonometryDegrees() {
        assertEquals("0.5", eval("sin(30)"))
        assertEquals("0", eval("sin(180)"))
        assertEquals("-1", eval("sin(270)"))
        assertEquals("-1", eval("cos(180)"))
        assertEquals("0", eval("cos(90)"))
        assertEquals("1", eval("tan(45)"))
        assertEquals("UNDEFINED", eval("tan(90)"))
        assertEquals("UNDEFINED", eval("tan(-90)"))
        assertEquals("0", eval("sin(-360)"))
        assertEquals("30", eval("asin(0.5)"))
        assertEquals("45", eval("atan(1)"))
        assertEquals("0", eval("sin(30)*2-1"))
    }

    @Test
    fun trigonometryRadians() {
        assertEquals("0", eval("sin(π)", AngleUnit.RADIANS))
        assertEquals("-1", eval("cos(π)", AngleUnit.RADIANS))
        assertEquals("1", eval("sin(π/2)", AngleUnit.RADIANS))
        assertEquals("1.5707963267949", eval("asin(1)", AngleUnit.RADIANS))
        assertEquals("UNDEFINED", eval("tan(π/2)", AngleUnit.RADIANS))
        assertEquals("UNDEFINED", eval("tan(3π/2)", AngleUnit.RADIANS))
        assertEquals("1", eval("tan(π/4)", AngleUnit.RADIANS))
    }

    @Test
    fun logarithmsAndRoots() {
        assertEquals("3", eval("log(1000)"))
        assertEquals("1", eval("ln(e)"))
        assertEquals("2", eval("√4"))
        assertEquals("1.4142135623731", eval("√2"))
        assertEquals("6", eval("√9*2"))
        assertEquals("1E100", eval("√(10^200)"))
        assertEquals("500", eval("log(10^500)"))
        assertEquals("1000", eval("10^log(1000)"))
    }

    @Test
    fun ansValue() {
        val r = evaluate("@*2", AngleUnit.DEGREES, java.math.BigDecimal("21"))
        assertEquals(EvalResult.Success(java.math.BigDecimal("42")), r)
    }

    @Test
    fun resultFormatting() {
        assertEquals("1.23456789012346E19", ResultFormatter.toInternal(java.math.BigDecimal("12345678901234567890")))
        assertEquals("999999999999999", ResultFormatter.toInternal(java.math.BigDecimal("999999999999999")))
        assertEquals("1E15", ResultFormatter.toInternal(java.math.BigDecimal("1000000000000000")))
        assertEquals("1.5E-12", ResultFormatter.toInternal(java.math.BigDecimal("0.0000000000015")))
        assertEquals("0.000001", ResultFormatter.toInternal(java.math.BigDecimal("0.000001")))
        assertEquals("-2.5", ResultFormatter.toInternal(java.math.BigDecimal("-2.50")))
    }

    @Test
    fun displayFormatting() {
        val it = DisplayFormatter(decimalSeparator = ',', groupingSeparator = '.')
        assertEquals("1.234.567,89×2", it.format("1234567.89*2"))
        assertEquals("−5÷2", it.format("-5/2"))
        assertEquals("sin⁻¹(0,5)", it.format("asin(0.5)"))
        assertEquals("1,5×10⁻¹²", it.format("1.5E-12"))
        // Per gli appunti la E resta, così il numero si incolla in altre app.
        val plain = DisplayFormatter(decimalSeparator = ',', groupingSeparator = null, scientificPowers = false)
        assertEquals("1,5E−12", plain.format("1.5E-12"))
        assertEquals("3,", it.format("3."))
        val en = DisplayFormatter(decimalSeparator = '.', groupingSeparator = ',')
        assertEquals("12,345+π", en.format("12345+π"))
        assertEquals("2×(−5)", en.format("2*@", java.math.BigDecimal("-5")))
        assertEquals("1234567,5", DisplayFormatter(',', null).format("1234567.5"))
    }
}
