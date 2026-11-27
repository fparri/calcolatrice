package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

/** Funzioni scientifiche a piena precisione, confrontate con valori di riferimento a 40 cifre. */
class PrecisionTest {

    /** Confronto per valore (30 e 3E+1 sono lo stesso numero). */
    private fun exact(expected: Int, expression: String, unit: AngleUnit = AngleUnit.RADIANS) {
        val value = (evaluate(expression, unit) as EvalResult.Success).value
        assertEquals(expression, 0, BigDecimal(expected).compareTo(value))
    }

    /** Come lo vede l'utente: 15 cifre. */
    private fun shown(expected: String, expression: String, unit: AngleUnit = AngleUnit.RADIANS) =
        assertEquals(expression, expected, ResultFormatter.toInternal((evaluate(expression, unit) as EvalResult.Success).value))

    private fun check(expected: String, expression: String, unit: AngleUnit = AngleUnit.RADIANS) {
        val result = evaluate(expression, unit)
        val value = (result as? EvalResult.Success)?.value ?: error("$expression -> $result")
        // 33 cifre: l'ultima delle 34 può differire di un arrotondamento.
        val mc = MathContext(33)
        assertEquals(expression, BigDecimal(expected).round(mc), value.round(mc))
    }

    @Test
    fun trigonometry() {
        check("0.841470984807896506652502321630299", "sin(1)")
        check("0.5403023058681397174009366074429766", "cos(1)")
        check("1.55740772465490223050697480745836", "tan(1)")
        check("-0.487506025087510691527794294348106", "sin(10^10)")
        check("0.1736481776669303488517166267693148", "sin(10)", AngleUnit.DEGREES)
        check("-0.9396926207859083840541092773247315", "cos(200)", AngleUnit.DEGREES)
        shown("0.5", "sin(30)", AngleUnit.DEGREES)
        exact(0, "sin(π)")
        assertEquals(EvalResult.Failure(CalcError.UNDEFINED), evaluate("sin(10^20)", AngleUnit.RADIANS))
    }

    @Test
    fun inverseTrigonometry() {
        check("1.107148717794090503017065460178537", "atan(2)")
        check("0.3046926540153975079720029612275292", "asin(0.3)")
        check("72.54239687627790770975395420755506", "acos(0.3)", AngleUnit.DEGREES)
        exact(30, "asin(0.5)", AngleUnit.DEGREES)
        exact(60, "acos(0.5)", AngleUnit.DEGREES)
        exact(0, "acos(1)", AngleUnit.DEGREES)
    }

    @Test
    fun logarithmsAndExponentials() {
        check("0.6931471805599453094172321214581766", "ln(2)")
        check("2.302585092994045684017991454684364", "ln(10)")
        check("-690.7755278982137052053974364053093", "ln(10^-300)")
        check("18.6314017661680180331939333479632", "ln(123456789)")
        check("0.8450980400142568307122162585926362", "log(7)")
        check("2.718281828459045235360287471352662", "e^1")
        check("1.928749847963917783017342816527013E-22", "e^-50")
        check("2.688117141816135448412625551580014E43", "e^100")
        check("12.18249396070347343807017595116797", "e^2.5")
        exact(3, "log(1000)")
        exact(-300, "log(10^-300)")
    }

    @Test
    fun powersAndRoots() {
        check("1.414213562373095048801688724209698", "2^0.5")
        check("1.414213562373095048801688724209698", "√2")
        check("1.732050807568877293527446341505872", "√3")
        check("31.54428070019754396054630311740571", "3^π")
        check("1.995262314968879601352455396739536", "10^0.3")
        check("2.718281830724053590252747715280798", "1.0001^10000.5")
        check("3.31580539828970282161523483668119E845", "7^1000.5")
        exact(2, "√4")
        exact(5, "25^0.5")
    }

    @Test
    fun chainedCalculationsKeepPrecision() {
        // Con il double l'errore si vedeva già alla quindicesima cifra.
        exact(2, "(√2)^2")
        exact(1, "sin(1)^2+cos(1)^2", AngleUnit.RADIANS)
        shown("7", "ln(e^7)")
        exact(7, "ln(e^7)")
    }
}
