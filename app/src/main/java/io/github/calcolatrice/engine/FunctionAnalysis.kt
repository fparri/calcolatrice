package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.cos

/** Una funzione f(X) scritta nella calcolatrice, valutata con il motore a 34 cifre. */
class UserFunction(
    val expression: String,
    val angleUnit: AngleUnit,
    val variables: Map<Char, BigDecimal> = emptyMap(),
) {
    private val tokens: List<Token> = Tokenizer.tokenize(expression)

    /** f(x); lancia [CalcException] fuori dal dominio. */
    fun at(x: BigDecimal): BigDecimal = Parser(tokens, angleUnit, null, variables + ('X' to x)).parse()

    fun atOrNull(x: BigDecimal): BigDecimal? = try {
        at(x)
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }

    fun result(x: BigDecimal): EvalResult = try {
        EvalResult.Success(at(x))
    } catch (e: CalcException) {
        EvalResult.Failure(e.error)
    } catch (e: ArithmeticException) {
        EvalResult.Failure(CalcError.OVERFLOW)
    }

    /** La stessa funzione in double, per i grafici e per cercare dove f cambia segno. */
    fun fast(): (Double) -> Double = FastEval.compile(expression, angleUnit, variables) ?: { Double.NaN }
}

/** Integrale definito; [approximate] se l'integrazione adattiva non ha raggiunto la precisione piena. */
data class IntegralResult(val value: BigDecimal, val approximate: Boolean)

/**
 * Strumenti numerici per la quinta superiore: derivata in un punto, integrale definito, zeri
 * in un intervallo, tabella dei valori. Servono a verificare i risultati fatti a mano.
 */
object FunctionAnalysis {
    private val MC = MathSupport.MC
    private val WORK = BigMath.WORK
    private val TWO = BigDecimal(2)

    // ------------------------------------------------------------ derivata

    /**
     * f'(x₀) con differenze centrali ed estrapolazione di Richardson (errore dell'ordine di h⁶).
     * Se la derivata destra e quella sinistra sono diverse (|x| in 0) la funzione non è derivabile:
     * errore [CalcError.UNDEFINED] invece di una media senza senso.
     */
    fun derivative(f: UserFunction, x0: BigDecimal): BigDecimal {
        val fx = f.at(x0)
        val h = x0.abs().max(BigDecimal.ONE).multiply(BigDecimal("1E-6"))

        fun central(step: BigDecimal): BigDecimal =
            f.at(x0.add(step, WORK)).subtract(f.at(x0.subtract(step, WORK)), WORK).divide(step.multiply(TWO), WORK)

        fun forward(step: BigDecimal): BigDecimal = f.at(x0.add(step, WORK)).subtract(fx, WORK).divide(step, WORK)
        fun backward(step: BigDecimal): BigDecimal = fx.subtract(f.at(x0.subtract(step, WORK)), WORK).divide(step, WORK)

        // Derivate unilaterali, migliorate con un passo di Richardson (errore ~h²).
        val half = h.divide(TWO)
        val right = TWO.multiply(forward(half)).subtract(forward(h), WORK)
        val left = TWO.multiply(backward(half)).subtract(backward(h), WORK)
        val scale = right.abs().max(left.abs()).max(BigDecimal.ONE)
        if (right.subtract(left).abs() > scale.multiply(BigDecimal("1E-6"))) throw CalcException(CalcError.UNDEFINED)

        // Richardson su differenze centrali: D(h), D(h/2), D(h/4).
        val d1 = central(h)
        val d2 = central(half)
        val d3 = central(half.divide(TWO))
        val r1 = BigDecimal(4).multiply(d2).subtract(d1).divide(BigDecimal(3), WORK)
        val r2 = BigDecimal(4).multiply(d3).subtract(d2).divide(BigDecimal(3), WORK)
        val result = BigDecimal(16).multiply(r2).subtract(r1).divide(BigDecimal(15), WORK)
        return tidy(result)
    }

    /** Toglie il rumore delle ultime cifre: le derivate numeriche hanno circa 25 cifre corrette. */
    private fun tidy(x: BigDecimal): BigDecimal {
        if (x.abs() < BigDecimal("1E-24")) return BigDecimal.ZERO
        return MathSupport.checked(x.round(MathContext(22, RoundingMode.HALF_EVEN)).stripTrailingZeros())
    }

    // ------------------------------------------------------------ integrale

    /** Nodi e pesi di Gauss–Legendre a 20 punti, calcolati una volta con 50 cifre. */
    private val GAUSS: List<Pair<BigDecimal, BigDecimal>> by lazy { gaussLegendre(20) }

    private fun gaussLegendre(n: Int): List<Pair<BigDecimal, BigDecimal>> {
        val nodes = ArrayList<Pair<BigDecimal, BigDecimal>>()
        for (i in 1..n) {
            // Stima iniziale classica, poi Newton sul polinomio di Legendre Pₙ.
            var x = BigDecimal(cos(Math.PI * (i - 0.25) / (n + 0.5)))
            var derivative = BigDecimal.ONE
            repeat(12) {
                var p0 = BigDecimal.ONE
                var p1 = x
                for (k in 2..n) {
                    val p2 = BigDecimal(2 * k - 1).multiply(x).multiply(p1, WORK).subtract(BigDecimal(k - 1).multiply(p0), WORK)
                        .divide(BigDecimal(k), WORK)
                    p0 = p1
                    p1 = p2
                }
                // P'ₙ(x) = n(x·Pₙ − Pₙ₋₁)/(x² − 1)
                derivative = BigDecimal(n).multiply(x.multiply(p1, WORK).subtract(p0, WORK), WORK)
                    .divide(x.multiply(x, WORK).subtract(BigDecimal.ONE), WORK)
                x = x.subtract(p1.divide(derivative, WORK), WORK)
            }
            val weight = TWO.divide(BigDecimal.ONE.subtract(x.multiply(x, WORK)).multiply(derivative.multiply(derivative, WORK), WORK), WORK)
            nodes += x to weight
        }
        return nodes
    }

    private class Budget(var evaluations: Int)

    private const val MAX_EVALUATIONS = 6_000
    private const val MAX_DEPTH = 50

    /** Precisione relativa richiesta agli integrali: ben oltre le 15 cifre mostrate. */
    private val TOLERANCE = BigDecimal("1E-22")
    private const val MAX_TANH_SINH_LEVEL = 8

    /**
     * ∫ₐᵇ f(x) dx. Prima Gauss–Legendre adattivo: si dimezza l'intervallo dove la stima cambia
     * (esatto a 34 cifre per i polinomi fino al grado 39). La stima deve stabilizzarsi sia per f sia per |f|:
     * così ∫₋₁¹ 1/x dx, dove le due metà si annullano, non passa per un "0" esatto.
     * Se non basta (singolarità agli estremi, come √x o 1/√x in 0) si usa la quadratura tanh-sinh,
     * fatta apposta per questi casi. Se nemmeno questa converge il valore è segnato come approssimato.
     */
    fun integral(f: UserFunction, a: BigDecimal, b: BigDecimal): IntegralResult {
        if (a.compareTo(b) == 0) return IntegralResult(BigDecimal.ZERO, approximate = false)
        val gauss = adaptiveGauss(f, a, b)
        if (!gauss.approximate) return gauss
        val tanhSinh = try {
            tanhSinh(f, a, b)
        } catch (e: CalcException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
        return tanhSinh ?: gauss
    }

    private fun adaptiveGauss(f: UserFunction, a: BigDecimal, b: BigDecimal): IntegralResult {
        val budget = Budget(0)
        // Scala per la tolleranza: integrale di |f|, così anche ∫ sin x su [−1, 1] = 0 si riconosce.
        val (whole, absolute) = gauss(f, a, b, budget)
        val tolerance = absolute.max(BigDecimal("1E-300")).multiply(TOLERANCE)
        val length = b.subtract(a, WORK).abs()
        var approximate = false
        fun adapt(low: BigDecimal, high: BigDecimal, estimate: BigDecimal, absEstimate: BigDecimal, depth: Int): BigDecimal {
            val middle = low.add(high, WORK).divide(TWO, WORK)
            val (left, leftAbs) = gauss(f, low, middle, budget)
            val (right, rightAbs) = gauss(f, middle, high, budget)
            val sum = left.add(right, WORK)
            val absSum = leftAbs.add(rightAbs, WORK)
            // Tolleranza proporzionale alla lunghezza, ma non sotto 10⁻⁴: uno spigolo (|x| in 0) si isola in ~45 passi.
            val allowed = tolerance.multiply(high.subtract(low, WORK).abs().divide(length, WORK).max(BigDecimal("1E-4")))
            if (sum.subtract(estimate, WORK).abs() <= allowed && absSum.subtract(absEstimate, WORK).abs() <= allowed) return sum
            if (depth >= MAX_DEPTH || budget.evaluations >= MAX_EVALUATIONS) {
                approximate = true
                return sum
            }
            return adapt(low, middle, left, leftAbs, depth + 1).add(adapt(middle, high, right, rightAbs, depth + 1), WORK)
        }
        val value = adapt(a, b, whole, absolute, 0)
        val rounded = if (approximate) value.round(MC) else value.round(MathContext(22, RoundingMode.HALF_EVEN))
        val clean = if (rounded.abs() <= tolerance) BigDecimal.ZERO else rounded.stripTrailingZeros()
        return IntegralResult(MathSupport.checked(clean), approximate)
    }

    /**
     * Quadratura tanh-sinh (doppio esponenziale): x = tanh(π/2·sinh t) addensa i nodi verso gli estremi,
     * dove i pesi decrescono più in fretta di qualunque singolarità integrabile. Restituisce null se i livelli
     * successivi non si stabilizzano o se i termini agli estremi non sono trascurabili (integrale divergente).
     */
    private fun tanhSinh(f: UserFunction, a: BigDecimal, b: BigDecimal): IntegralResult? {
        val half = b.subtract(a, WORK).divide(TWO, WORK)
        val middle = a.add(half, WORK)
        val halfPi = BigMath.HALF_PI
        // Oltre questo valore di u il nodo dista dall'estremo meno di 10⁻⁵⁰ volte l'intervallo.
        val maxU = BigDecimal(58)

        /** Contributo dei nodi in ±t: (w·(f(x₊) + f(x₋)), w·(|f(x₊)| + |f(x₋)|)), oppure null se t è oltre il limite. */
        fun pair(t: BigDecimal): Pair<BigDecimal, BigDecimal>? {
            val et = BigMath.exp(t)
            val sinh = et.subtract(BigDecimal.ONE.divide(et, WORK), WORK).divide(TWO, WORK)
            val cosh = et.add(BigDecimal.ONE.divide(et, WORK), WORK).divide(TWO, WORK)
            val u = halfPi.multiply(sinh, WORK)
            if (u > maxU) return null
            val e2u = BigMath.exp(u.multiply(TWO, WORK))
            val plusOne = e2u.add(BigDecimal.ONE, WORK)
            // 1 − tanh u = 2/(e^{2u} + 1), calcolato direttamente per non perdere cifre vicino agli estremi.
            val gap = half.multiply(TWO, WORK).divide(plusOne, WORK)
            val weight = halfPi.multiply(cosh, WORK).multiply(BigDecimal(4).multiply(e2u, WORK), WORK)
                .divide(plusOne.multiply(plusOne, WORK), WORK)
            val right = f.at(b.subtract(gap, WORK))
            val left = f.at(a.add(gap, WORK))
            return weight.multiply(right.add(left, WORK), WORK) to weight.multiply(right.abs().add(left.abs(), WORK), WORK)
        }

        val center = f.at(middle)
        var sum = halfPi.multiply(center, WORK)
        var absSum = halfPi.multiply(center.abs(), WORK)
        // Livello 0: t = 1, 2, 3…; l'ultimo termine dice se gli estremi contano ancora (integrale divergente).
        var lastTerm = BigDecimal.ZERO
        var k = 1
        while (true) {
            val (s, abs) = pair(BigDecimal(k)) ?: break
            sum = sum.add(s, WORK)
            absSum = absSum.add(abs, WORK)
            lastTerm = abs
            k++
        }
        var h = BigDecimal.ONE
        var previous = half.multiply(sum, WORK)
        for (level in 1..MAX_TANH_SINH_LEVEL) {
            h = h.divide(TWO)
            var j = 1
            while (true) {
                val (s, abs) = pair(h.multiply(BigDecimal(j))) ?: break
                sum = sum.add(s, WORK)
                absSum = absSum.add(abs, WORK)
                j += 2
            }
            val estimate = half.multiply(h, WORK).multiply(sum, WORK)
            val scale = half.abs().multiply(h, WORK).multiply(absSum, WORK).max(BigDecimal("1E-300"))
            val converged = estimate.subtract(previous, WORK).abs() <= scale.multiply(BigDecimal("1E-21"))
            if (converged && level >= 3) {
                // Termini agli estremi ancora grandi: l'integrale non converge (come ∫₀¹ 1/x dx).
                if (lastTerm.multiply(half.abs(), WORK) > scale.multiply(BigDecimal("1E-12"))) return null
                val rounded = estimate.round(MathContext(22, RoundingMode.HALF_EVEN))
                val clean = if (rounded.abs() <= scale.multiply(BigDecimal("1E-22"))) BigDecimal.ZERO else rounded.stripTrailingZeros()
                return IntegralResult(MathSupport.checked(clean), approximate = false)
            }
            previous = estimate
        }
        return null
    }

    /** Somma di Gauss su [low, high]: (∫f, ∫|f|). */
    private fun gauss(f: UserFunction, low: BigDecimal, high: BigDecimal, budget: Budget): Pair<BigDecimal, BigDecimal> {
        val half = high.subtract(low, WORK).divide(TWO, WORK)
        val middle = low.add(half, WORK)
        var sum = BigDecimal.ZERO
        var absolute = BigDecimal.ZERO
        for ((node, weight) in GAUSS) {
            val value = f.at(middle.add(half.multiply(node, WORK), WORK))
            sum = sum.add(weight.multiply(value, WORK), WORK)
            absolute = absolute.add(weight.multiply(value.abs(), WORK), WORK)
        }
        budget.evaluations += GAUSS.size
        return sum.multiply(half, WORK) to absolute.multiply(half.abs(), WORK)
    }

    // ------------------------------------------------------------ zeri

    /**
     * Zeri di f in [a, b]: dove f cambia segno (cercato su 1000 punti con il calcolo veloce e poi
     * raffinato a 34 cifre) e dove |f| ha un minimo vicino a 0 (zeri doppi come in (x − 1)²).
     */
    fun zeros(f: UserFunction, a: BigDecimal, b: BigDecimal, limit: Int = 20): List<BigDecimal> {
        val low = a.min(b)
        val high = a.max(b)
        val fast = f.fast()
        val samples = 1000
        val step = high.subtract(low, WORK).divide(BigDecimal(samples), WORK)
        val xs = List(samples + 1) { low.add(step.multiply(BigDecimal(it), WORK), WORK) }
        val ys = xs.map { fast(it.toDouble()) }
        val found = ArrayList<BigDecimal>()
        val exact: (BigDecimal) -> BigDecimal? = { f.atOrNull(it) }
        for (i in 0 until samples) {
            if (found.size >= limit) break
            val y0 = ys[i]
            val y1 = ys[i + 1]
            if (!y0.isFinite() || !y1.isFinite()) continue
            if (y0 == 0.0 && exact(xs[i])?.signum() == 0) {
                found += xs[i]
                continue
            }
            if ((y0 < 0 && y1 > 0) || (y0 > 0 && y1 < 0)) {
                NumericSolver.rootBetween(exact, xs[i], xs[i + 1])?.let { found += it }
            } else if (i > 0) {
                // Minimo di |f| senza cambio di segno: forse uno zero doppio, da verificare con SOLVE.
                val previous = ys[i - 1]
                if (previous.isFinite() && kotlin.math.abs(y0) < kotlin.math.abs(previous) && kotlin.math.abs(y0) <= kotlin.math.abs(y1) &&
                    previous * y1 > 0 && y0 * y1 >= 0
                ) {
                    val result = NumericSolver.solve(f.expression, f.angleUnit, null, f.variables, 'X', xs[i])
                    val root = (result as? EvalResult.Success)?.value
                    if (root != null && root >= xs[i - 1] && root <= xs[i + 1]) found += root
                }
            }
        }
        if (found.size < limit && ys[samples] == 0.0 && exact(xs[samples])?.signum() == 0) found += xs[samples]
        // Radici trovate due volte (sul confine tra due tratti) si contano una volta.
        val tolerance = step.abs().divide(BigDecimal(1000), WORK)
        return found.map { it.round(MC).stripTrailingZeros() }
            .sorted()
            .fold(ArrayList<BigDecimal>()) { list, x ->
                if (list.isEmpty() || x.subtract(list.last()).abs() > tolerance) list += x
                list
            }
    }

    // ------------------------------------------------------------ tabella

    /** Valori di f a partire da [start] con passo [step]: x calcolati esatti (−5, −4,5, −4…). */
    fun table(f: UserFunction, start: BigDecimal, step: BigDecimal, rows: Int): List<Pair<BigDecimal, EvalResult>> =
        List(rows) { i ->
            val x = start.add(step.multiply(BigDecimal(i))).stripTrailingZeros().let { if (it.signum() == 0) BigDecimal.ZERO else it }
            x to f.result(x)
        }
}
