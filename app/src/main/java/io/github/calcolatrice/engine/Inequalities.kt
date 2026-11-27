package io.github.calcolatrice.engine

import java.math.BigDecimal

/** Verso di una disequazione p(x) ⋚ 0. */
enum class Relation(val symbol: String) {
    GREATER(">"),
    GREATER_EQUAL("≥"),
    LESS("<"),
    LESS_EQUAL("≤"),
    ;

    /** Il verso successivo, per il tasto che li fa scorrere: > ≥ < ≤. */
    fun next(): Relation = entries[(ordinal + 1) % entries.size]

    /** True se un valore con questo segno soddisfa la relazione con 0. */
    fun holds(sign: Int): Boolean = when (this) {
        GREATER -> sign > 0
        GREATER_EQUAL -> sign >= 0
        LESS -> sign < 0
        LESS_EQUAL -> sign <= 0
    }
}

/** Estremo di un intervallo; [closed] se l'estremo appartiene all'intervallo. */
data class Bound(val value: BigDecimal, val closed: Boolean)

/** Intervallo della retta reale; null = −∞ (low) o +∞ (high). Un punto è [a, a]. */
data class Interval(val low: Bound?, val high: Bound?)

/** Insieme delle soluzioni: intervalli disgiunti, in ordine crescente. */
data class SolutionSet(val intervals: List<Interval>) {
    val isEmpty: Boolean get() = intervals.isEmpty()
    val isAll: Boolean get() = intervals.size == 1 && intervals[0].low == null && intervals[0].high == null

    companion object {
        val ALL = SolutionSet(listOf(Interval(null, null)))
        val NONE = SolutionSet(emptyList())
    }
}

/** Soluzioni di una disequazione fratta e valori esclusi perché annullano il denominatore. */
data class RationalSolution(val solution: SolutionSet, val excluded: List<BigDecimal>)

/**
 * Disequazioni polinomiali di primo e secondo grado, fratte e loro sistemi.
 * Le radici vengono dalle equazioni ([QuadraticEquation]); il segno tra una radice e l'altra
 * si controlla calcolando il polinomio in un punto interno, quindi il metodo vale per ogni grado.
 */
object Inequalities {
    private val MC = BigMath.WORK

    /** Risolve ax² + bx + c [relation] 0 (con a = 0: disequazione di primo grado). */
    fun solve(a: BigDecimal, b: BigDecimal, c: BigDecimal, relation: Relation): SolutionSet {
        val p = polynomial(a, b, c)
        return fromRoots(roots(a, b, c), relation, sign = { x -> p(x).signum() }, atRoot = { relation.holds(0) })
    }

    /**
     * Risolve (ax² + bx + c) / (dx² + ex + f) [relation] 0. Il segno è il prodotto dei segni di numeratore e
     * denominatore; gli zeri del denominatore non appartengono mai alle soluzioni (condizione di esistenza).
     * Se il denominatore è il polinomio nullo la disequazione non ha senso: [CalcError.UNDEFINED].
     */
    fun solveRational(numerator: List<BigDecimal>, denominator: List<BigDecimal>, relation: Relation): RationalSolution {
        val (a, b, c) = numerator
        val (d, e, f) = denominator
        if (d.signum() == 0 && e.signum() == 0 && f.signum() == 0) throw CalcException(CalcError.UNDEFINED)
        val n = polynomial(a, b, c)
        val den = polynomial(d, e, f)
        val poles = roots(d, e, f)
        fun isPole(x: BigDecimal) = poles.any { close(it, x) }
        // Radici di numeratore e denominatore insieme, senza doppioni (un fattore comune dà lo stesso punto).
        val all = (poles + roots(a, b, c).filter { !isPole(it) }).sorted()
        val solution = fromRoots(
            all,
            relation,
            sign = { x -> n(x).signum() * den(x).signum() },
            atRoot = { x -> !isPole(x) && relation.holds(0) },
        )
        return RationalSolution(solution, poles)
    }

    private fun polynomial(a: BigDecimal, b: BigDecimal, c: BigDecimal): (BigDecimal) -> BigDecimal =
        { x -> a.multiply(x, MC).add(b, MC).multiply(x, MC).add(c, MC) }

    /** Radici reali distinte di ax² + bx + c, in ordine crescente (nessuna se il polinomio è costante). */
    private fun roots(a: BigDecimal, b: BigDecimal, c: BigDecimal): List<BigDecimal> = when (val solution = QuadraticEquation.solve(a, b, c)) {
        is QuadraticSolution.TwoReal -> listOf(solution.x2, solution.x1).sorted()
        is QuadraticSolution.DoubleRoot -> listOf(solution.x)
        is QuadraticSolution.ComplexPair -> emptyList()
        is QuadraticSolution.Linear -> when (val linear = solution.solution) {
            is LinearSolution.Unique -> linear.values
            // b = 0: il polinomio è la costante c, sempre dello stesso segno.
            LinearSolution.Infinite, LinearSolution.None -> emptyList()
        }
    }

    /** Stesso punto a meno degli arrotondamenti delle 34 cifre. */
    private fun close(x: BigDecimal, y: BigDecimal): Boolean =
        x.subtract(y, MC).abs() <= TOLERANCE.multiply(x.abs().max(BigDecimal.ONE), MC)

    private val TOLERANCE = BigDecimal("1E-25")

    /**
     * Costruisce l'insieme a partire dalle radici distinte in ordine crescente: per ogni tratto tra due radici
     * vale il segno ([sign]) in un punto interno; ogni radice appartiene alle soluzioni se [atRoot] lo dice.
     */
    internal fun fromRoots(
        roots: List<BigDecimal>,
        relation: Relation,
        sign: (BigDecimal) -> Int,
        atRoot: (BigDecimal) -> Boolean,
    ): SolutionSet {
        // Pezzi della retta in ordine: (−∞, r₁), [r₁], (r₁, r₂), [r₂], …, (rₙ, +∞).
        val pieces = ArrayList<Pair<Interval, Boolean>>()
        for (i in 0..roots.size) {
            val low = roots.getOrNull(i - 1)
            val high = roots.getOrNull(i)
            val test = when {
                low == null && high == null -> BigDecimal.ZERO
                low == null -> high!!.subtract(high.abs().max(BigDecimal.ONE), MC)
                high == null -> low.add(low.abs().max(BigDecimal.ONE), MC)
                else -> low.add(high, MC).divide(BigDecimal(2), MC)
            }
            val region = Interval(low?.let { Bound(it, false) }, high?.let { Bound(it, false) })
            pieces += region to relation.holds(sign(test))
            if (high != null) pieces += Interval(Bound(high, true), Bound(high, true)) to atRoot(high)
        }
        // Pezzi consecutivi inclusi diventano un solo intervallo.
        val intervals = ArrayList<Interval>()
        var start: Interval? = null
        var end: Interval? = null
        for ((piece, included) in pieces) {
            if (included) {
                if (start == null) start = piece
                end = piece
            } else if (start != null) {
                intervals += Interval(start.low, end!!.high)
                start = null
            }
        }
        if (start != null) intervals += Interval(start.low, end!!.high)
        return SolutionSet(intervals)
    }

    /** Soluzioni comuni a due disequazioni (sistema; per più disequazioni si applica più volte). */
    fun intersect(x: SolutionSet, y: SolutionSet): SolutionSet {
        val result = ArrayList<Interval>()
        for (i in x.intervals) {
            for (j in y.intervals) {
                val low = maxLow(i.low, j.low)
                val high = minHigh(i.high, j.high)
                if (low == null || high == null) {
                    result += Interval(low, high)
                    continue
                }
                val compare = low.value.compareTo(high.value)
                if (compare < 0 || (compare == 0 && low.closed && high.closed)) result += Interval(low, high)
            }
        }
        return SolutionSet(result.sortedWith(compareBy(nullsFirst(naturalOrder<BigDecimal>())) { it.low?.value }))
    }

    private fun maxLow(a: Bound?, b: Bound?): Bound? = when {
        a == null -> b
        b == null -> a
        a.value > b.value -> a
        b.value > a.value -> b
        else -> Bound(a.value, a.closed && b.closed)
    }

    private fun minHigh(a: Bound?, b: Bound?): Bound? = when {
        a == null -> b
        b == null -> a
        a.value < b.value -> a
        b.value < a.value -> b
        else -> Bound(a.value, a.closed && b.closed)
    }
}

/**
 * Testo delle soluzioni, come si scrive a scuola: forma algebrica ("x < −1 ∨ x > 3", "x ≠ 2")
 * e notazione degli intervalli, all'italiana con ] [ e ";" oppure all'inglese con ( ) e ",".
 */
class InequalityText(private val value: (BigDecimal) -> String, private val italianIntervals: Boolean) {

    /** Forma algebrica; null se l'insieme è vuoto o è tutto ℝ (lì serve una frase). */
    fun algebraic(set: SolutionSet): String? {
        if (set.isEmpty || set.isAll) return null
        val intervals = set.intervals
        // ℝ meno un punto: x ≠ a.
        if (intervals.size == 2 && intervals[0].low == null && intervals[1].high == null) {
            val left = intervals[0].high
            val right = intervals[1].low
            if (left != null && right != null && !left.closed && !right.closed && left.value.compareTo(right.value) == 0) {
                return "x ≠ ${value(left.value)}"
            }
        }
        return intervals.joinToString(" ∨ ") { interval ->
            val low = interval.low
            val high = interval.high
            when {
                low != null && high != null && low.value.compareTo(high.value) == 0 -> "x = ${value(low.value)}"
                low == null && high != null -> "x ${if (high.closed) "≤" else "<"} ${value(high.value)}"
                high == null && low != null -> "x ${if (low.closed) "≥" else ">"} ${value(low.value)}"
                low != null && high != null ->
                    "${value(low.value)} ${if (low.closed) "≤" else "<"} x ${if (high.closed) "≤" else "<"} ${value(high.value)}"
                else -> "x ∈ ℝ"
            }
        }
    }

    /** Notazione degli intervalli, es. "]−∞; −1[ ∪ ]3; +∞[" oppure "(−∞, −1) ∪ (3, +∞)"; null se vuoto. */
    fun intervals(set: SolutionSet): String? {
        if (set.isEmpty) return null
        val separator = if (italianIntervals) "; " else ", "
        return set.intervals.joinToString(" ∪ ") { interval ->
            val low = interval.low
            val high = interval.high
            if (low != null && high != null && low.value.compareTo(high.value) == 0) return@joinToString "{${value(low.value)}}"
            val open = when {
                low == null -> if (italianIntervals) "]" else "("
                low.closed -> "["
                else -> if (italianIntervals) "]" else "("
            }
            val close = when {
                high == null -> if (italianIntervals) "[" else ")"
                high.closed -> "]"
                else -> if (italianIntervals) "[" else ")"
            }
            val lowText = low?.let { value(it.value) } ?: "−∞"
            val highText = high?.let { value(it.value) } ?: "+∞"
            "$open$lowText$separator$highText$close"
        }
    }
}
