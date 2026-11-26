package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.BigInteger

/** Forma esatta di un risultato, come la mostrano le calcolatrici scolastiche. */
sealed interface ExactForm {
    /** p/q (anche intero). */
    data class Rational(val value: Fraction) : ExactForm

    /** (p/q)·√r, con r > 1 senza fattori quadrati. */
    data class Radical(val coefficient: Fraction, val radicand: Long) : ExactForm

    /** (p/q)·π. */
    data class PiMultiple(val coefficient: Fraction) : ExactForm
}

/**
 * Riconosce le forme esatte dal valore numerico, senza un motore simbolico: con 34 cifre
 * il valore basta per decidere in modo affidabile. √8 → 2√2, sin 60° → √3/2, arccos 0 → π/2.
 *
 * - p/q: frazioni continue ([Fractions]);
 * - (p/q)·√r: si riconosce x² come frazione a/b, poi √(a/b) = √(a·b)/b e si portano fuori i quadrati;
 * - (p/q)·π: si riconosce x/π come frazione.
 *
 * Restano decimali le somme di radicali diversi (√2 + √3) e le forme come (1 + √5)/2.
 * Un numero digitato con poche cifre (1,4142135623) non viene scambiato per √2: il suo quadrato
 * non è una frazione con tutte le 34 cifre.
 */
object ExactForms {
    private val MAX_SQUARE_NUMERATOR = BigInteger.valueOf(1_000_000)
    private val MAX_SQUARE_DENOMINATOR = BigInteger.valueOf(10_000)
    private val MAX_PI_TERM = BigInteger.valueOf(1_000)

    fun recognize(x: BigDecimal): ExactForm? {
        Fractions.toFraction(x)?.let { return ExactForm.Rational(it) }
        radical(x)?.let { return it }
        return piMultiple(x)
    }

    private fun radical(x: BigDecimal): ExactForm.Radical? {
        val square = Fractions.toFraction(x.multiply(x, MathSupport.MC)) ?: return null
        val a = square.numerator
        val b = square.denominator
        if (a.signum() <= 0 || a > MAX_SQUARE_NUMERATOR || b > MAX_SQUARE_DENOMINATOR) return null
        // √(a/b) = √(a·b)/b = s·√r/b con a·b = s²·r.
        val (s, r) = splitSquare(a.toLong() * b.toLong())
        if (r == 1L) return null // sarebbe razionale: già escluso dalle frazioni continue
        var numerator = BigInteger.valueOf(s)
        var denominator = b
        val common = numerator.gcd(denominator)
        numerator /= common
        denominator /= common
        if (x.signum() < 0) numerator = numerator.negate()
        return ExactForm.Radical(Fraction(numerator, denominator), r)
    }

    private fun piMultiple(x: BigDecimal): ExactForm.PiMultiple? {
        val ratio = Fractions.toFraction(x.divide(MathSupport.PI, MathSupport.MC)) ?: return null
        if (ratio.numerator.signum() == 0) return null
        if (ratio.numerator.abs() > MAX_PI_TERM || ratio.denominator > MAX_PI_TERM) return null
        return ExactForm.PiMultiple(ratio)
    }

    /** n = s²·r con r senza fattori quadrati. */
    internal fun splitSquare(n: Long): Pair<Long, Long> {
        var s = 1L
        var r = n
        var f = 2L
        while (f * f <= r) {
            while (r % (f * f) == 0L) {
                r /= f * f
                s *= f
            }
            f++
        }
        return s to r
    }
}
