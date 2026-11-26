package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Funzioni del menu MATH: radice n-esima, logaritmo in base b, calcolo combinatorio,
 * MCD e mcm, distribuzioni binomiale e normale. Tutte a 34 cifre come il resto del motore.
 */
internal object MoreMath {
    private val MC = MathSupport.MC
    private val WORK = BigMath.WORK

    /** Oltre questo n le distribuzioni binomiali richiederebbero troppi calcoli. */
    private const val MAX_BINOMIAL_N = 1_000_000

    private fun integer(x: BigDecimal): BigInteger {
        if (!MathSupport.isInteger(x)) throw CalcException(CalcError.UNDEFINED)
        return x.setScale(0, RoundingMode.UNNECESSARY).toBigIntegerExact()
    }

    private fun nonNegativeInteger(x: BigDecimal): BigInteger {
        val n = integer(x)
        if (n.signum() < 0) throw CalcException(CalcError.UNDEFINED)
        return n
    }

    // ------------------------------------------------------------ radici e logaritmi

    /**
     * Radice n-esima di x. Con indice dispari si accettano anche i negativi (³√−8 = −2).
     * Quando il risultato è "tondo" (³√8, ⁴√0,0016) viene restituito esatto.
     */
    fun root(index: BigDecimal, x: BigDecimal): BigDecimal {
        if (index.signum() == 0) throw CalcException(CalcError.UNDEFINED)
        val integerIndex = MathSupport.isInteger(index)
        val odd = integerIndex && index.toBigInteger().testBit(0)
        if (x.signum() < 0 && !odd) throw CalcException(CalcError.UNDEFINED)
        if (x.signum() == 0) {
            if (index.signum() < 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
            return BigDecimal.ZERO
        }
        val magnitude = MathSupport.log10Abs(x) / index.toDouble()
        if (magnitude > MathSupport.MAX_EXPONENT + 1) throw CalcException(CalcError.OVERFLOW)
        if (magnitude < -MathSupport.MAX_EXPONENT - 1) return BigDecimal.ZERO

        val absolute = x.abs()
        var value = BigMath.exp(BigMath.ln(absolute).divide(index, WORK))
        // Risultato esatto: si prova il valore arrotondato e lo si eleva alla potenza.
        if (integerIndex && index.abs() <= BigDecimal(1000)) {
            val n = index.abs().toInt()
            val candidate = value.round(MathContext(25, RoundingMode.HALF_EVEN)).stripTrailingZeros()
            val power = candidate.pow(n)
            val exact = if (index.signum() > 0) power.compareTo(absolute) == 0 else power.multiply(absolute).compareTo(BigDecimal.ONE) == 0
            if (exact) value = candidate
        }
        val signed = if (x.signum() < 0) value.negate() else value
        return MathSupport.checked(signed.round(MC))
    }

    /** Logaritmo di x in base b: ln x / ln b. */
    fun logBase(base: BigDecimal, x: BigDecimal): BigDecimal {
        if (base.signum() <= 0 || base.compareTo(BigDecimal.ONE) == 0 || x.signum() <= 0) {
            throw CalcException(CalcError.UNDEFINED)
        }
        val value = BigMath.ln(x).divide(BigMath.ln(base), WORK)
        // log₂ 8 = 3 esattamente, senza l'ultima cifra sbagliata.
        val rounded = value.setScale(0, RoundingMode.HALF_EVEN)
        if (value.subtract(rounded).abs() < BigDecimal("1E-40") && rounded.abs() <= BigDecimal(10_000)) {
            val k = rounded.toInt()
            val check = if (k >= 0) base.pow(k) else BigDecimal.ONE.divide(base.pow(-k), WORK)
            if (check.compareTo(x) == 0) return rounded.stripTrailingZeros()
        }
        return value.round(MC)
    }

    // ------------------------------------------------------------ calcolo combinatorio

    /**
     * Oltre questi valori il risultato supera certamente 10⁹⁹⁹⁹: C(n, k) ≥ 2^k per k ≤ n/2,
     * e n!/(n − k)! ≥ k!.
     */
    private const val MAX_COMBINATION_K = 33_300
    private const val MAX_PERMUTATION_K = 3_250

    /** log₁₀ di un prodotto di frazioni (a_i / b_i), per riconoscere i risultati troppo grandi prima di calcolarli. */
    private fun tooLarge(terms: Int, factor: (Int) -> Pair<BigInteger, BigInteger>): Boolean {
        var log = 0.0
        for (i in 1..terms) {
            val (numerator, denominator) = factor(i)
            log += MathSupport.log10Abs(BigDecimal(numerator)) - MathSupport.log10Abs(BigDecimal(denominator))
        }
        return log > MathSupport.MAX_EXPONENT + 1
    }

    /** Combinazioni semplici C(n, k) = n! / (k!·(n − k)!). Vale 0 se k > n. */
    fun combinations(nValue: BigDecimal, kValue: BigDecimal): BigDecimal {
        val n = nonNegativeInteger(nValue)
        val k = nonNegativeInteger(kValue)
        if (k > n) return BigDecimal.ZERO
        val small = k.min(n - k)
        if (small > BigInteger.valueOf(MAX_COMBINATION_K.toLong())) throw CalcException(CalcError.OVERFLOW)
        if (tooLarge(small.toInt()) { i -> (n - small + BigInteger.valueOf(i.toLong())) to BigInteger.valueOf(i.toLong()) }) {
            throw CalcException(CalcError.OVERFLOW)
        }
        var result = BigInteger.ONE
        var i = BigInteger.ONE
        while (i <= small) {
            // Prodotto e divisione alternati: ogni passo dà ancora un intero (C(n − small + i, i)).
            result = result * (n - small + i) / i
            i += BigInteger.ONE
        }
        return MathSupport.checked(BigDecimal(result).round(MC))
    }

    /** Disposizioni semplici (permutazioni di k su n) n! / (n − k)!. Vale 0 se k > n. */
    fun permutations(nValue: BigDecimal, kValue: BigDecimal): BigDecimal {
        val n = nonNegativeInteger(nValue)
        val k = nonNegativeInteger(kValue)
        if (k > n) return BigDecimal.ZERO
        if (k > BigInteger.valueOf(MAX_PERMUTATION_K.toLong())) throw CalcException(CalcError.OVERFLOW)
        if (tooLarge(k.toInt()) { i -> (n - k + BigInteger.valueOf(i.toLong())) to BigInteger.ONE }) {
            throw CalcException(CalcError.OVERFLOW)
        }
        var result = BigInteger.ONE
        var factor = n - k + BigInteger.ONE
        while (factor <= n) {
            result *= factor
            factor += BigInteger.ONE
        }
        return MathSupport.checked(BigDecimal(result).round(MC))
    }

    // ------------------------------------------------------------ MCD e mcm

    fun gcd(values: List<BigDecimal>): BigDecimal {
        val result = values.map { integer(it).abs() }.reduce(BigInteger::gcd)
        return BigDecimal(result)
    }

    fun lcm(values: List<BigDecimal>): BigDecimal {
        val integers = values.map { integer(it).abs() }
        if (integers.any { it.signum() == 0 }) return BigDecimal.ZERO
        val result = integers.reduce { a, b -> a / a.gcd(b) * b }
        return MathSupport.checked(BigDecimal(result).round(MC))
    }

    // ------------------------------------------------------------ distribuzioni

    private fun checkBinomial(nValue: BigDecimal, p: BigDecimal): Int {
        val n = nonNegativeInteger(nValue)
        if (n > BigInteger.valueOf(MAX_BINOMIAL_N.toLong())) throw CalcException(CalcError.OVERFLOW)
        if (p.signum() < 0 || p > BigDecimal.ONE) throw CalcException(CalcError.UNDEFINED)
        return n.toInt()
    }

    /** Binomiale: P(X = k) con n prove e probabilità di successo p. */
    fun binomialPdf(nValue: BigDecimal, p: BigDecimal, kValue: BigDecimal): BigDecimal {
        val n = checkBinomial(nValue, p)
        val kInteger = integer(kValue)
        if (kInteger.signum() < 0 || kInteger > BigInteger.valueOf(n.toLong())) return BigDecimal.ZERO
        val k = kInteger.toInt()
        if (p.signum() == 0) return if (k == 0) BigDecimal.ONE else BigDecimal.ZERO
        if (p.compareTo(BigDecimal.ONE) == 0) return if (k == n) BigDecimal.ONE else BigDecimal.ZERO
        // C(n, k) in BigDecimal a 50 cifre: niente limiti di grandezza intermedi (C(10⁵, 5·10⁴) è enorme
        // ma moltiplicato per p^k(1−p)^(n−k) dà una probabilità normale).
        val small = minOf(k, n - k)
        var coefficient = BigDecimal.ONE
        for (i in 1..small) {
            coefficient = coefficient.multiply(BigDecimal(n - small + i), WORK).divide(BigDecimal(i), WORK)
        }
        val q = BigDecimal.ONE.subtract(p)
        val value = coefficient.multiply(p.pow(k, WORK), WORK).multiply(q.pow(n - k, WORK), WORK)
        return MathSupport.checked(value.round(MC))
    }

    /** Binomiale cumulativa: P(X ≤ k). */
    fun binomialCdf(nValue: BigDecimal, p: BigDecimal, kValue: BigDecimal): BigDecimal {
        val n = checkBinomial(nValue, p)
        val kInteger = integer(kValue)
        if (kInteger.signum() < 0) return BigDecimal.ZERO
        if (kInteger >= BigInteger.valueOf(n.toLong())) return BigDecimal.ONE
        val k = kInteger.toInt()
        if (p.signum() == 0) return BigDecimal.ONE
        if (p.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ZERO
        // Termini successivi: P(i + 1) = P(i) · (n − i)/(i + 1) · p/(1 − p), partendo da (1 − p)^n.
        val q = BigDecimal.ONE.subtract(p)
        val ratio = p.divide(q, WORK)
        var term = q.pow(n, WORK)
        var sum = term
        for (i in 0 until k) {
            term = term.multiply(BigDecimal(n - i), WORK).divide(BigDecimal(i + 1), WORK).multiply(ratio, WORK)
            sum = sum.add(term, WORK)
        }
        val value = sum.min(BigDecimal.ONE)
        return MathSupport.checked(value.round(MC))
    }

    /** Normale: P(a ≤ X ≤ b) per X con media μ e deviazione standard σ. Gli estremi si possono scambiare. */
    fun normalCdf(a: BigDecimal, b: BigDecimal, mean: BigDecimal, sd: BigDecimal): BigDecimal {
        if (sd.signum() <= 0) throw CalcException(CalcError.UNDEFINED)
        val low = a.min(b)
        val high = a.max(b)
        val zLow = low.subtract(mean, WORK).divide(sd, WORK)
        val zHigh = high.subtract(mean, WORK).divide(sd, WORK)
        // Entrambi nella coda destra: si usano le code per non perdere cifre (1 − 0,999… ).
        val value = if (zLow.signum() > 0) {
            upperTail(zLow).subtract(upperTail(zHigh), WORK)
        } else {
            lowerTail(zHigh).subtract(lowerTail(zLow), WORK)
        }
        return MathSupport.checked(value.max(BigDecimal.ZERO).round(MC))
    }

    private val SQRT2: BigDecimal by lazy { BigMath.sqrt(BigDecimal(2)) }

    /** Φ(z) = P(Z ≤ z) = erfc(−z/√2)/2. */
    private fun lowerTail(z: BigDecimal): BigDecimal =
        BigMath.erfc(z.negate().divide(SQRT2, WORK)).divide(BigDecimal(2), WORK)

    /** 1 − Φ(z) = erfc(z/√2)/2. */
    private fun upperTail(z: BigDecimal): BigDecimal =
        BigMath.erfc(z.divide(SQRT2, WORK)).divide(BigDecimal(2), WORK)
}
