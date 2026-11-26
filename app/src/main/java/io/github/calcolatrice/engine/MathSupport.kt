package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.log10

/** Operazioni a 34 cifre usate dalla valutazione: potenze, radici, fattoriale, trigonometria, logaritmi. */
internal object MathSupport {
    /** Precisione dei risultati: 34 cifre significative (come IEEE decimal128). */
    val MC = MathContext(34, RoundingMode.HALF_EVEN)

    /** Oltre 10^MAX_EXPONENT il risultato è considerato troppo grande. */
    const val MAX_EXPONENT = 9999

    val PI = BigDecimal("3.141592653589793238462643383279503")
    val E = BigDecimal("2.718281828459045235360287471352662")
    private val DEG_360 = BigDecimal(360)

    /** Oltre questo valore (in radianti) la riduzione dell'angolo perderebbe tutte le cifre. */
    private val MAX_RADIANS = BigDecimal("1E18")

    /** Residui come sin(π) ≈ 3·10⁻³⁴: π stesso è arrotondato a 34 cifre. */
    private val TRIG_RESIDUE = BigDecimal("1E-32")
    private val TAN_POLE = BigDecimal("1E-30")

    /** Esponente decimale di x (posizione della cifra più significativa). */
    fun exponentOf(x: BigDecimal): Int = x.precision() - x.scale() - 1

    /** log10(|x|) approssimato, per qualunque BigDecimal diverso da zero (serve solo per i controlli). */
    fun log10Abs(x: BigDecimal): Double {
        val exp = exponentOf(x)
        val mantissa = x.abs().movePointLeft(exp).toDouble()
        return log10(mantissa) + exp
    }

    /** Controlla che il valore sia in un intervallo gestibile. */
    fun checked(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        val exp = exponentOf(x)
        if (exp > MAX_EXPONENT) throw CalcException(CalcError.OVERFLOW)
        if (exp < -MAX_EXPONENT) return BigDecimal.ZERO
        return x
    }

    fun isInteger(x: BigDecimal): Boolean =
        x.signum() == 0 || x.stripTrailingZeros().scale() <= 0

    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw CalcException(CalcError.UNDEFINED)
        return BigMath.sqrt(x).round(MC).stripTrailingZeros()
    }

    fun power(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        if (exponent.signum() == 0) return BigDecimal.ONE
        if (base.signum() == 0) {
            if (exponent.signum() < 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
            return BigDecimal.ZERO
        }
        val integerExponent = isInteger(exponent)
        if (!integerExponent && base.signum() < 0) throw CalcException(CalcError.UNDEFINED)

        // ±1 elevato a qualunque esponente: il controllo con i double sotto darebbe 0·∞ = NaN per esponenti enormi.
        if (base.abs().compareTo(BigDecimal.ONE) == 0) {
            val negative = base.signum() < 0 && exponent.toBigInteger().testBit(0)
            return if (negative) BigDecimal.ONE.negate() else BigDecimal.ONE
        }

        // Controllo dell'ordine di grandezza prima di calcolare.
        val magnitude = log10Abs(base) * exponent.toDouble()
        if (magnitude.isNaN()) throw CalcException(CalcError.UNDEFINED)
        if (magnitude > MAX_EXPONENT + 1) throw CalcException(CalcError.OVERFLOW)
        if (magnitude < -MAX_EXPONENT - 1) return BigDecimal.ZERO

        // e^x con la vera esponenziale: la costante e a 34 cifre, elevata a potenza, perderebbe precisione.
        if (base.compareTo(E) == 0) return checked(BigMath.exp(exponent).round(MC))
        if (integerExponent && exponent.abs() <= BigDecimal(100_000)) {
            return checked(base.pow(exponent.intValueExact(), MC))
        }
        // x^y = e^(y·ln|x|), col segno giusto per gli esponenti interi dispari.
        val negative = base.signum() < 0 && exponent.toBigInteger().testBit(0)
        val value = BigMath.exp(exponent.multiply(BigMath.ln(base.abs()), BigMath.WORK))
        return checked((if (negative) value.negate() else value).round(MC))
    }

    fun factorial(x: BigDecimal): BigDecimal {
        if (x.signum() < 0 || !isInteger(x)) throw CalcException(CalcError.UNDEFINED)
        if (x > BigDecimal(3248)) throw CalcException(CalcError.OVERFLOW) // 3249! > 10^10000
        val n = x.intValueExact()
        var result = BigDecimal.ONE
        for (i in 2..n) result = result.multiply(BigDecimal(i), MC)
        return checked(result)
    }

    /** Riduce un angolo in gradi all'intervallo [0, 360). */
    private fun normalizeDegrees(x: BigDecimal): BigDecimal {
        val r = x.remainder(DEG_360)
        return if (r.signum() < 0) r.add(DEG_360) else r
    }

    fun trig(name: String, x: BigDecimal, unit: AngleUnit): BigDecimal {
        val radians: BigDecimal
        if (unit == AngleUnit.DEGREES) {
            val angle = normalizeDegrees(x)
            // Valori esatti per i multipli di 90°.
            if (isInteger(angle) && angle.toInt() % 90 == 0) {
                val quadrant = angle.toInt() / 90
                return when (name) {
                    "sin" -> intArrayOf(0, 1, 0, -1)[quadrant].toBigDecimal()
                    "cos" -> intArrayOf(1, 0, -1, 0)[quadrant].toBigDecimal()
                    else -> if (quadrant % 2 == 1) {
                        throw CalcException(CalcError.UNDEFINED)
                    } else {
                        BigDecimal.ZERO
                    }
                }
            }
            radians = BigMath.toRadians(angle)
        } else {
            if (x.abs() >= MAX_RADIANS) throw CalcException(CalcError.UNDEFINED)
            radians = x
        }
        val (sin, cos) = BigMath.sinCos(radians)
        val value = when (name) {
            "sin" -> sin
            "cos" -> cos
            else -> {
                // Multipli dispari di π/2: il coseno è zero a meno dei residui di π.
                if (cos.abs() < TAN_POLE) throw CalcException(CalcError.UNDEFINED)
                sin.divide(cos, BigMath.WORK)
            }
        }
        if (radians.abs() >= BigDecimal.ONE && value.abs() < radians.abs().multiply(TRIG_RESIDUE)) {
            return BigDecimal.ZERO
        }
        return value.round(MC)
    }

    fun inverseTrig(name: String, x: BigDecimal, unit: AngleUnit): BigDecimal {
        if (name != "atan" && x.abs() > BigDecimal.ONE) throw CalcException(CalcError.UNDEFINED)
        val radians = when (name) {
            "asin" -> BigMath.asin(x)
            "acos" -> BigMath.acos(x)
            else -> BigMath.atan(x)
        }
        val value = if (unit == AngleUnit.DEGREES) BigMath.toDegrees(radians) else radians
        return value.round(MC)
    }

    fun logarithm(name: String, x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw CalcException(CalcError.UNDEFINED)
        val value = if (name == "log") BigMath.log10(x) else BigMath.ln(x)
        return value.round(MC)
    }
}
