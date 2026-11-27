package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Verso del limite: x → x₀ da entrambi i lati, da sinistra (x₀⁻), da destra (x₀⁺), oppure x → ±∞. */
enum class LimitSide { BOTH, LEFT, RIGHT, PLUS_INFINITY, MINUS_INFINITY }

/** Risultato di un limite calcolato numericamente, con la sua affidabilità (mai [Reliability.RELIABLE]). */
sealed interface LimitResult {
    val reliability: Reliability

    data class Value(val value: BigDecimal, override val reliability: Reliability) : LimitResult

    /** +∞ ([sign] = 1) o −∞ ([sign] = −1). */
    data class Infinite(val sign: Int, override val reliability: Reliability) : LimitResult

    /** Limite destro e sinistro diversi: il limite non esiste. */
    data class Different(val left: LimitResult, val right: LimitResult) : LimitResult {
        override val reliability: Reliability get() = maxOf(left.reliability, right.reliability)
    }

    /** La funzione oscilla (sin(1/x) in 0): il limite non esiste. */
    data class Oscillating(override val reliability: Reliability = Reliability.APPROXIMATE) : LimitResult

    /** La funzione non è definita vicino a x₀ da quel lato. */
    data object Undefined : LimitResult {
        override val reliability = Reliability.APPROXIMATE
    }
}

/**
 * Limiti numerici: si calcola f sempre più vicino a x₀ (h = H/2ᵏ, a 34 cifre) e si guarda la successione.
 * Se converge, l'estrapolazione di Richardson (che stima il valore finale dalla tendenza, supponendo un
 * errore che va come h, h², …) dà molte cifre; un valore "bello" (1/6, √2, π/2) si riconosce. Se |f| cresce
 * senza rallentare è ±∞; se continua a cambiare a caso non esiste.
 */
object FunctionLimits {
    private val WORK = BigMath.WORK
    private const val STEPS = 24

    fun limit(f: UserFunction, x0: BigDecimal, side: LimitSide): LimitResult = when (side) {
        LimitSide.LEFT -> oneSided(f, x0, -1)
        LimitSide.RIGHT -> oneSided(f, x0, 1)
        LimitSide.PLUS_INFINITY -> atInfinity(f, 1)
        LimitSide.MINUS_INFINITY -> atInfinity(f, -1)
        LimitSide.BOTH -> {
            val left = oneSided(f, x0, -1)
            val right = oneSided(f, x0, 1)
            when {
                left is LimitResult.Undefined -> right
                right is LimitResult.Undefined -> left
                same(left, right) -> if (left.reliability >= right.reliability) left else right
                left is LimitResult.Oscillating || right is LimitResult.Oscillating -> LimitResult.Oscillating()
                else -> LimitResult.Different(left, right)
            }
        }
    }

    private fun same(a: LimitResult, b: LimitResult): Boolean = when {
        a is LimitResult.Value && b is LimitResult.Value -> {
            val scale = a.value.abs().max(b.value.abs()).max(BigDecimal.ONE)
            a.value.subtract(b.value).abs() <= scale.multiply(BigDecimal("1E-9"))
        }
        a is LimitResult.Infinite && b is LimitResult.Infinite -> a.sign == b.sign
        else -> false
    }

    private fun oneSided(f: UserFunction, x0: BigDecimal, side: Int): LimitResult {
        val start = x0.abs().max(BigDecimal.ONE).multiply(BigDecimal("0.1"))
        return sequence { h -> f.result(x0.add(h.multiply(BigDecimal(side)), WORK)) }.let { analyze(it, start) }
    }

    /** x → ±∞ con x = ±1/h: lo stesso procedimento con h → 0⁺. */
    private fun atInfinity(f: UserFunction, side: Int): LimitResult =
        sequence { h -> f.result(BigDecimal(side).divide(h, WORK)) }.let { analyze(it, BigDecimal("0.1")) }

    /** Valori di g(h) per h = H/2ᵏ; null dove g non è definita, ±∞ dove va in overflow. */
    private fun sequence(g: (BigDecimal) -> EvalResult): (BigDecimal) -> List<Sample> = { start ->
        val two = BigDecimal(2)
        var h = start
        List(STEPS) {
            val sample = when (val r = g(h)) {
                is EvalResult.Success -> Sample(r.value, false)
                is EvalResult.Failure -> if (r.error == CalcError.OVERFLOW) Sample(null, true) else Sample(null, false)
            }
            h = h.divide(two, WORK)
            sample
        }
    }

    private class Sample(val value: BigDecimal?, val overflow: Boolean)

    private fun analyze(values: (BigDecimal) -> List<Sample>, start: BigDecimal): LimitResult {
        val samples = values(start)
        // Gli ultimi valori decidono: vicino a x₀ la funzione deve essere definita.
        val tail = samples.takeLast(12)
        if (tail.all { it.overflow }) return infiniteSign(samples) ?: LimitResult.Infinite(1, Reliability.RISKY)
        if (tail.any { it.value == null && !it.overflow }) return LimitResult.Undefined
        if (tail.any { it.overflow }) return infiniteSign(samples) ?: LimitResult.Oscillating(Reliability.RISKY)
        val v = tail.map { it.value!! }

        // Differenze successive: per una successione che converge si riducono (circa a metà o meno a ogni passo).
        val d = v.zipWithNext { a, b -> b.subtract(a, WORK) }
        val scale = v.maxOf { it.abs() }.max(BigDecimal.ONE)
        val tiny = scale.multiply(BigDecimal("1E-30"))
        if (d.takeLast(4).all { it.abs() <= tiny }) return value(v.last(), Reliability.APPROXIMATE)

        val ratios = d.zipWithNext { a, b -> if (a.abs() <= tiny) null else b.divide(a, MathContext(10)).toDouble() }.takeLast(8)
        val shrinking = ratios.all { it != null && it in -0.8..0.8 }
        if (shrinking) return converge(v)

        // Divergenza: |f| cresce sempre, con lo stesso segno, senza rallentare (anche lentamente come ln).
        val growing = v.zipWithNext().takeLast(8).all { (a, b) -> b.abs() > a.abs() && a.signum() == b.signum() && a.signum() != 0 }
        if (growing) {
            val fast = ratios.all { it != null && it >= 1.5 }
            return LimitResult.Infinite(v.last().signum(), if (fast) Reliability.APPROXIMATE else Reliability.RISKY)
        }
        // Oscilla ma sempre meno (x·sin(1/x) in 0): converge, senza poter estrapolare. Da verificare.
        // Ampiezza delle oscillazioni all'inizio e alla fine della coda.
        fun range(list: List<BigDecimal>) = list.maxOrNull()!!.subtract(list.minOrNull()!!, WORK)
        val early = range(v.take(6))
        val late = range(v.takeLast(4))
        if (late.multiply(BigDecimal(16)) < early) {
            val digits = (-kotlin.math.log10(late.divide(scale, MathContext(5)).toDouble().coerceAtLeast(1e-25))).toInt().coerceIn(3, 25)
            return value(v.last().round(MathContext(digits)), Reliability.RISKY, digits)
        }
        return LimitResult.Oscillating()
    }

    private fun infiniteSign(samples: List<Sample>): LimitResult.Infinite? {
        val last = samples.lastOrNull { it.value != null }?.value ?: return null
        if (last.signum() == 0) return null
        return LimitResult.Infinite(last.signum(), Reliability.RISKY)
    }

    /**
     * Estrapolazione di Richardson sulla successione (passo dimezzato a ogni valore): la tabella elimina
     * gli errori in h, h², h³…; le cifre su cui due stime successive concordano sono quelle buone.
     */
    private fun converge(v: List<BigDecimal>): LimitResult {
        var column = v
        var previousBest: BigDecimal? = null
        var best = v.last()
        var agreement = BigDecimal.ONE
        var factor = BigDecimal(2)
        for (j in 1..8) {
            if (column.size < 2) break
            val next = column.zipWithNext { a, b -> factor.multiply(b, WORK).subtract(a, WORK).divide(factor.subtract(BigDecimal.ONE), WORK) }
            val estimate = next.last()
            val difference = estimate.subtract(best, WORK).abs()
            if (previousBest != null && difference > agreement) break // l'estrapolazione non migliora più
            agreement = difference
            previousBest = best
            best = estimate
            column = next
            factor = factor.multiply(BigDecimal(2))
        }
        // Se Richardson non basta (errore come √h, per esempio √x in 0) prova con Aitken, che regge
        // qualunque riduzione geometrica dell'errore; si tiene la stima più coerente.
        val (aitken, aitkenAgreement) = aitken(v)
        if (aitkenAgreement < agreement) {
            best = aitken
            agreement = aitkenAgreement
        }
        val scale = best.abs().max(BigDecimal.ONE)
        val relative = agreement.divide(scale, MathContext(5)).toDouble()
        val reliability = if (relative < 1e-10) Reliability.APPROXIMATE else Reliability.RISKY
        // Cifre affidabili: quelle su cui le stime concordano (almeno 6, al massimo 25).
        val digits = if (relative <= 0.0) 25 else (-kotlin.math.log10(relative)).toInt().coerceIn(6, 25)
        return value(best.round(MathContext(digits, RoundingMode.HALF_EVEN)), reliability, digits)
    }

    /** Processo Δ² di Aitken ripetuto: stima finale e scarto tra le ultime due stime. */
    private fun aitken(values: List<BigDecimal>): Pair<BigDecimal, BigDecimal> {
        var v = values
        var estimate = v.last()
        var agreement = v.last().subtract(v[v.size - 2]).abs()
        repeat(3) {
            if (v.size < 4) return estimate to agreement
            v = (0 until v.size - 2).map { i ->
                val denominator = v[i + 2].subtract(v[i + 1].multiply(BigDecimal(2)), WORK).add(v[i], WORK)
                if (denominator.signum() == 0) {
                    v[i + 2]
                } else {
                    val delta = v[i + 2].subtract(v[i + 1], WORK)
                    v[i + 2].subtract(delta.multiply(delta, WORK).divide(denominator, WORK), WORK)
                }
            }
            val newAgreement = v.last().subtract(v[v.size - 2]).abs()
            if (newAgreement >= agreement) return estimate to agreement
            estimate = v.last()
            agreement = newAgreement
        }
        return estimate to agreement
    }

    /** Valore finale: se è vicino a una frazione semplice (entro la precisione raggiunta) si usa quella. */
    private fun value(x: BigDecimal, reliability: Reliability, digits: Int = 25): LimitResult.Value {
        if (x.abs() < BigDecimal("1E-${digits - 2}")) return LimitResult.Value(BigDecimal.ZERO, reliability)
        val tolerance = x.abs().max(BigDecimal.ONE).multiply(BigDecimal.ONE.movePointLeft(digits - 2))
        for (q in 1..1000) {
            val qq = BigDecimal(q)
            val p = x.multiply(qq).setScale(0, RoundingMode.HALF_EVEN)
            val candidate = p.divide(qq, WORK)
            if (candidate.subtract(x).abs() <= tolerance) {
                // Frazione esatta a 34 cifre: il formattatore la mostrerà come p/q.
                return LimitResult.Value(p.divide(qq, MathSupport.MC), reliability)
            }
        }
        return LimitResult.Value(x.stripTrailingZeros(), reliability)
    }
}
