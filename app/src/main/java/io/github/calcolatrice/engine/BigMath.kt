package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.log2

/**
 * Funzioni matematiche in BigDecimal, senza librerie: esponenziale, logaritmo, trigonometria.
 * Lavorano a [WORK] cifre (16 di margine) così i risultati arrotondati a 34 cifre sono corretti.
 */
internal object BigMath {
    val WORK = MathContext(50, RoundingMode.HALF_EVEN)

    /** Sotto questa soglia un termine di una serie non cambia più il risultato. */
    private val EPSILON = BigDecimal("1E-56")
    private val TWO = BigDecimal(2)

    /** π a 50 cifre con la formula di Machin: π = 16·atan(1/5) − 4·atan(1/239). */
    val PI: BigDecimal by lazy {
        atanOfInverse(5).multiply(BigDecimal(16)).subtract(atanOfInverse(239).multiply(BigDecimal(4)), WORK)
    }

    val HALF_PI: BigDecimal by lazy { PI.divide(TWO, WORK) }

    /** ln 10, usato per ridurre i logaritmi e per log₁₀. */
    val LN10: BigDecimal by lazy { lnNearOne(BigDecimal.TEN) }

    private val DEGREES_PER_RADIAN: BigDecimal by lazy { BigDecimal(180).divide(PI, WORK) }
    private val RADIANS_PER_DEGREE: BigDecimal by lazy { PI.divide(BigDecimal(180), WORK) }

    fun toRadians(degrees: BigDecimal): BigDecimal = degrees.multiply(RADIANS_PER_DEGREE, WORK)
    fun toDegrees(radians: BigDecimal): BigDecimal = radians.multiply(DEGREES_PER_RADIAN, WORK)

    /** atan(1/n) con la serie di Taylor (converge in fretta per n ≥ 5). */
    private fun atanOfInverse(n: Int): BigDecimal {
        val x = BigDecimal.ONE.divide(BigDecimal(n), WORK)
        val x2 = x.multiply(x, WORK)
        var power = x
        var sum = x
        var k = 1
        while (true) {
            power = power.multiply(x2, WORK)
            val term = power.divide(BigDecimal(2 * k + 1), WORK)
            if (term < EPSILON) return sum
            sum = if (k % 2 == 1) sum.subtract(term, WORK) else sum.add(term, WORK)
            k++
        }
    }

    /** Radice quadrata con il metodo di Newton. */
    fun sqrt(x: BigDecimal, mc: MathContext = WORK): BigDecimal {
        require(x.signum() >= 0)
        if (x.signum() == 0) return BigDecimal.ZERO
        // Stima iniziale: metà dell'esponente decimale, mantissa in double.
        val exponent = MathSupport.exponentOf(x)
        val even = if (exponent % 2 == 0) exponent else exponent - 1
        val mantissa = x.movePointLeft(even).toDouble()
        var y = BigDecimal(kotlin.math.sqrt(mantissa)).movePointRight(even / 2)
        val work = MathContext(mc.precision + 4, RoundingMode.HALF_EVEN)
        // Ogni passo raddoppia le cifre corrette: da ~16 bastano 3 passi, il quarto è margine.
        repeat(4) { y = y.add(x.divide(y, work), work).divide(TWO, work) }
        return y.round(mc)
    }

    /** e^x: x viene dimezzato finché è piccolo, poi serie di Taylor e quadrati successivi. */
    fun exp(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ONE
        val magnitude = x.abs().toDouble()
        val halvings = if (magnitude > 1e-3) ceil(log2(magnitude / 1e-3)).toInt() else 0
        // Un dimezzamento in più ogni 3 raddoppia l'errore: si compensa con cifre extra.
        val work = MathContext(WORK.precision + halvings / 3 + 2, RoundingMode.HALF_EVEN)
        var r = x
        repeat(halvings) { r = r.divide(TWO, work) }
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        var k = 1
        while (true) {
            term = term.multiply(r, work).divide(BigDecimal(k), work)
            if (term.abs() < EPSILON) break
            sum = sum.add(term, work)
            k++
        }
        repeat(halvings) { sum = sum.multiply(sum, work) }
        return sum.round(WORK)
    }

    /** ln(m) per m vicino a [1, 10]: stima in double e due passi di Halley. */
    private fun lnNearOne(m: BigDecimal): BigDecimal {
        if (m.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ZERO
        var y = BigDecimal(ln(m.toDouble()))
        repeat(3) {
            val e = exp(y)
            y = y.add(TWO.multiply(m.subtract(e, WORK)).divide(m.add(e, WORK), WORK), WORK)
        }
        return y
    }

    /** Logaritmo naturale di x > 0, per qualunque ordine di grandezza. */
    fun ln(x: BigDecimal): BigDecimal {
        require(x.signum() > 0)
        val k = MathSupport.exponentOf(x)
        val m = x.movePointLeft(k)
        val lnM = lnNearOne(m)
        return if (k == 0) lnM else lnM.add(LN10.multiply(BigDecimal(k), WORK), WORK)
    }

    /** Logaritmo in base 10: esatto per le potenze di 10. */
    fun log10(x: BigDecimal): BigDecimal {
        require(x.signum() > 0)
        val k = MathSupport.exponentOf(x)
        val m = x.movePointLeft(k)
        return BigDecimal(k).add(lnNearOne(m).divide(LN10, WORK), WORK)
    }

    /** Seno e coseno di un angolo in radianti. */
    fun sinCos(x: BigDecimal): Pair<BigDecimal, BigDecimal> {
        // Riduzione a [−π/4, π/4]: x = q·π/2 + r.
        val q = x.divide(HALF_PI, WORK).setScale(0, RoundingMode.HALF_EVEN)
        val r = x.subtract(q.multiply(HALF_PI, WORK), WORK)
        val r2 = r.multiply(r, WORK)
        var sinTerm = r
        var sin = r
        var cosTerm = BigDecimal.ONE
        var cos = BigDecimal.ONE
        var k = 1
        while (true) {
            // sin: r^(2k+1)/(2k+1)!, cos: r^(2k)/(2k)!, con segni alterni.
            cosTerm = cosTerm.multiply(r2, WORK).divide(BigDecimal((2 * k - 1) * (2 * k)), WORK).negate()
            sinTerm = sinTerm.multiply(r2, WORK).divide(BigDecimal((2 * k) * (2 * k + 1)), WORK).negate()
            if (cosTerm.abs() < EPSILON && sinTerm.abs() < EPSILON) break
            cos = cos.add(cosTerm, WORK)
            sin = sin.add(sinTerm, WORK)
            k++
        }
        return when (q.toBigInteger().mod(4.toBigInteger()).toInt()) {
            0 -> sin to cos
            1 -> cos to sin.negate()
            2 -> sin.negate() to cos.negate()
            else -> cos.negate() to sin
        }
    }

    /** Arcotangente in radianti, in (−π/2, π/2). */
    fun atan(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        if (x.abs() > BigDecimal.ONE) {
            val inner = atan(BigDecimal.ONE.divide(x, WORK))
            return if (x.signum() > 0) HALF_PI.subtract(inner, WORK) else HALF_PI.negate().subtract(inner, WORK)
        }
        // atan(x) = 2·atan(x / (1 + √(1 + x²))): si dimezza l'angolo finché la serie converge in fretta.
        var y = x
        var doublings = 0
        val small = BigDecimal("0.05")
        while (y.abs() > small) {
            y = y.divide(BigDecimal.ONE.add(sqrt(BigDecimal.ONE.add(y.multiply(y, WORK), WORK)), WORK), WORK)
            doublings++
        }
        val y2 = y.multiply(y, WORK)
        var power = y
        var sum = y
        var k = 1
        while (true) {
            power = power.multiply(y2, WORK)
            val term = power.divide(BigDecimal(2 * k + 1), WORK)
            if (term.abs() < EPSILON) break
            sum = if (k % 2 == 1) sum.subtract(term, WORK) else sum.add(term, WORK)
            k++
        }
        return sum.multiply(BigDecimal.ONE.shiftLeft(doublings), WORK)
    }

    /** Arcoseno in radianti, per |x| ≤ 1. */
    fun asin(x: BigDecimal): BigDecimal {
        require(x.abs() <= BigDecimal.ONE)
        if (x.abs().compareTo(BigDecimal.ONE) == 0) return if (x.signum() > 0) HALF_PI else HALF_PI.negate()
        val cosine = sqrt(BigDecimal.ONE.subtract(x.multiply(x, WORK), WORK))
        return atan(x.divide(cosine, WORK))
    }

    fun acos(x: BigDecimal): BigDecimal = HALF_PI.subtract(asin(x), WORK)

    private val SQRT_PI: BigDecimal by lazy { sqrt(PI) }

    /**
     * Funzione d'errore complementare erfc(x) = 1 − erf(x), per la distribuzione normale.
     * Per |x| < 3 si usa la serie di erf (si perdono al più 4 delle 50 cifre); nelle code la frazione
     * continua, che calcola erfc direttamente e mantiene tutte le cifre anche per valori minuscoli.
     */
    fun erfc(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) return TWO.subtract(erfc(x.negate()), WORK)
        if (x.signum() == 0) return BigDecimal.ONE
        val x2 = x.multiply(x, WORK)
        // Oltre x ≈ 151,7 erfc(x) < 10⁻¹⁰⁰⁰⁰: sotto la soglia dei numeri gestiti.
        if (x2 > BigDecimal(23_030)) return BigDecimal.ZERO
        return if (x < BigDecimal(3)) BigDecimal.ONE.subtract(erfSeries(x, x2), WORK) else erfcFraction(x, x2)
    }

    /** erf(x) = 2/√π · e^(−x²) · Σ 2ⁿ x^(2n+1) / (1·3·…·(2n+1)): termini tutti positivi, nessuna cancellazione. */
    private fun erfSeries(x: BigDecimal, x2: BigDecimal): BigDecimal {
        val twoX2 = x2.multiply(TWO, WORK)
        var term = x
        var sum = x
        var n = 0
        while (true) {
            term = term.multiply(twoX2, WORK).divide(BigDecimal(2 * n + 3), WORK)
            if (term < EPSILON) break
            sum = sum.add(term, WORK)
            n++
        }
        return sum.multiply(exp(x2.negate()), WORK).multiply(TWO, WORK).divide(SQRT_PI, WORK)
    }

    /**
     * erfc(x) = e^(−x²)/√π · 1/(x + (1/2)/(x + 1/(x + (3/2)/(x + …)))), valutata con il metodo di Lentz.
     */
    private fun erfcFraction(x: BigDecimal, x2: BigDecimal): BigDecimal {
        val tiny = BigDecimal("1E-200")
        val tolerance = BigDecimal("1E-52")
        var f = x
        var c = x
        var d = BigDecimal.ZERO
        var n = 1
        while (n < 20_000) {
            val a = BigDecimal(n).divide(TWO, WORK)
            d = x.add(a.multiply(d, WORK), WORK)
            if (d.signum() == 0) d = tiny
            c = x.add(a.divide(c, WORK), WORK)
            if (c.signum() == 0) c = tiny
            d = BigDecimal.ONE.divide(d, WORK)
            val delta = c.multiply(d, WORK)
            f = f.multiply(delta, WORK)
            if (delta.subtract(BigDecimal.ONE).abs() < tolerance) break
            n++
        }
        return exp(x2.negate()).divide(SQRT_PI.multiply(f, WORK), WORK)
    }

    private fun BigDecimal.shiftLeft(n: Int): BigDecimal = this.multiply(TWO.pow(n))
}
