package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Quanto ci si può fidare di un risultato dello studio di funzione:
 * - [RELIABLE]: calcolato a 34 cifre con un metodo che non sbaglia per le funzioni di scuola (zeri, segno…);
 * - [APPROXIMATE]: trovato numericamente, preciso ma non garantito (confini del dominio, flessi, asintoti verticali);
 * - [RISKY]: stimato con valori molto grandi di x, da controllare con i limiti (asintoti orizzontali e obliqui).
 */
enum class Reliability { RELIABLE, APPROXIMATE, RISKY }

enum class Parity { EVEN, ODD, NONE }

/** Un punto notevole del grafico (massimo, minimo, flesso). */
data class StudyPoint(val x: BigDecimal, val y: BigDecimal)

/** Asintoto: verticale x = [x], oppure y = [m]x + [q] (orizzontale se m = 0) verso [side] (−1, +1 o 0 per entrambi). */
sealed interface Asymptote {
    data class Vertical(val x: BigDecimal) : Asymptote
    data class Line(val m: BigDecimal, val q: BigDecimal, val side: Int) : Asymptote
}

/**
 * Risultato dello studio di f in [from, to]. Gli insiemi usano −∞ e +∞ per i tratti che arrivano agli
 * estremi dell'intervallo studiato (si legge: "nell'intervallo studiato").
 */
data class FunctionStudyResult(
    val from: BigDecimal,
    val to: BigDecimal,
    val domain: SolutionSet,
    val domainReliability: Reliability,
    /** null se l'intervallo non è simmetrico rispetto a 0 (non si può dire). */
    val parity: Parity?,
    val yIntercept: BigDecimal?,
    val zeros: List<BigDecimal>,
    val positive: SolutionSet,
    val negative: SolutionSet,
    val increasing: SolutionSet,
    val decreasing: SolutionSet,
    val maxima: List<StudyPoint>,
    val minima: List<StudyPoint>,
    val convex: SolutionSet,
    val concave: SolutionSet,
    val inflections: List<StudyPoint>,
    val vertical: List<BigDecimal>,
    val lines: List<Asymptote.Line>,
)

/**
 * Studio di funzione numerico: dominio, simmetrie, intersezioni, segno, crescenza, massimi e minimi,
 * concavità e flessi, asintoti. Prima si campiona con il calcolo veloce in double, poi ogni punto
 * trovato si raffina a 34 cifre. Può richiedere qualche secondo: va fatto fuori dal thread principale.
 */
object FunctionStudy {
    private val MC = MathSupport.MC
    private val WORK = BigMath.WORK
    private val TWO = BigDecimal(2)
    private const val SAMPLES = 800

    fun study(f: UserFunction, from: BigDecimal, to: BigDecimal): FunctionStudyResult {
        val low = from.min(to)
        val high = from.max(to)
        if (low.compareTo(high) == 0) throw CalcException(CalcError.UNDEFINED)
        val fast = f.fast()
        val step = high.subtract(low, WORK).divide(BigDecimal(SAMPLES), WORK)
        val xs = List(SAMPLES + 1) { low.add(step.multiply(BigDecimal(it), WORK), WORK) }
        val ys = xs.map { fast(it.toDouble()) }

        // ---------------------------------------------------------------- dominio e poli
        val boundaries = ArrayList<Pair<BigDecimal, Boolean>>() // (punto, appartiene al dominio)
        val poles = ArrayList<BigDecimal>()
        for (i in 0 until SAMPLES) {
            val a = ys[i].isFinite()
            val b = ys[i + 1].isFinite()
            when {
                a && !b -> boundaries += boundary(f, xs[i], xs[i + 1])
                !a && b -> boundaries += boundary(f, xs[i + 1], xs[i])
                a && b -> pole(f, xs[i], xs[i + 1], ys[i], ys[i + 1])?.let { poles += it }
            }
        }
        // Poli di ordine pari (1/x²): massimi molto alti di |f| senza cambio di segno.
        for (i in 1 until SAMPLES) {
            val y = abs(ys[i])
            if (!y.isFinite() || !ys[i - 1].isFinite() || !ys[i + 1].isFinite()) continue
            if (y > 1e6 && y > abs(ys[i - 1]) && y > abs(ys[i + 1])) {
                evenPole(f, fast, xs[i - 1], xs[i + 1])?.let { p -> if (poles.none { near(it, p, step) }) poles += p }
            }
        }
        val domainPieces = domain(f, low, high, ys, boundaries, poles)
        val domainReliability = if (boundaries.isEmpty() && poles.isEmpty()) Reliability.RELIABLE else Reliability.APPROXIMATE

        // ---------------------------------------------------------------- simmetrie e intersezioni
        val parity = parity(f, low, high)
        val zero = BigDecimal.ZERO
        val yIntercept = if (zero in low..high) f.atOrNull(zero) else null
        val zeros = FunctionAnalysis.zeros(f, low, high).filter { z -> poles.none { near(it, z, step) } }

        // ---------------------------------------------------------------- segno
        val breaks = (zeros + poles + boundaries.map { it.first }).sorted()
        val sign = signs(domainPieces, breaks) { x -> f.atOrNull(x)?.signum() }

        // ---------------------------------------------------------------- derivata prima
        val d1: (BigDecimal) -> BigDecimal? = { x -> runCatching { FunctionAnalysis.derivative(f, x) }.getOrNull() }
        val fastD1: (Double) -> Double = { x ->
            val h = 1e-6 * maxOf(1.0, abs(x))
            (fast(x + h) - fast(x - h)) / (2 * h)
        }
        // Poli e confini del dominio: lì la derivata può cambiare segno senza che ci sia un punto stazionario.
        val barriers = poles + boundaries.map { it.first }
        val stationary = changes(xs, fastD1, d1, barriers, step)
        val d1Breaks = (stationary + poles + boundaries.map { it.first }).sorted()
        val growth = signs(domainPieces, d1Breaks) { x -> d1(x)?.signum() }
        val maxima = ArrayList<StudyPoint>()
        val minima = ArrayList<StudyPoint>()
        for (x in stationary) {
            val y = f.atOrNull(x) ?: continue
            val before = d1(x.subtract(step.divide(BigDecimal(4), WORK), WORK))?.signum() ?: continue
            val after = d1(x.add(step.divide(BigDecimal(4), WORK), WORK))?.signum() ?: continue
            if (before > 0 && after < 0) maxima += StudyPoint(x, tidy(y))
            if (before < 0 && after > 0) minima += StudyPoint(x, tidy(y))
        }

        // ---------------------------------------------------------------- derivata seconda
        val d2: (BigDecimal) -> BigDecimal? = { x -> secondDerivative(f, x) }
        val fastD2: (Double) -> Double = { x ->
            val h = 1e-4 * maxOf(1.0, abs(x))
            (fast(x + h) - 2 * fast(x) + fast(x - h)) / (h * h)
        }
        val flexes = changes(xs, fastD2, d2, barriers, step).filter { x -> f.atOrNull(x) != null }
        val d2Breaks = (flexes + poles + boundaries.map { it.first }).sorted()
        val curvature = signs(domainPieces, d2Breaks) { x -> d2(x)?.signum() }
        val inflections = flexes.mapNotNull { x -> f.atOrNull(x)?.let { StudyPoint(x, tidy(it)) } }

        // ---------------------------------------------------------------- asintoti
        val vertical = (poles + boundaries.filter { !it.second }.map { it.first }.filter { diverges(f, it) })
            .sorted()
            .fold(ArrayList<BigDecimal>()) { list, x -> if (list.none { near(it, x, step) }) list += x; list }
        val lines = ArrayList<Asymptote.Line>()
        // Verso +∞ e −∞, solo se il dominio arriva fino all'estremo dell'intervallo studiato.
        val right = if (domainPieces.lastOrNull()?.high == null) lineAt(f, 1) else null
        val left = if (domainPieces.firstOrNull()?.low == null) lineAt(f, -1) else null
        if (left != null && right != null && left.m.compareTo(right.m) == 0 && left.q.compareTo(right.q) == 0) {
            lines += Asymptote.Line(left.m, left.q, 0)
        } else {
            listOfNotNull(left, right).forEach { lines += it }
        }

        return FunctionStudyResult(
            from = low,
            to = high,
            domain = SolutionSet(domainPieces),
            domainReliability = domainReliability,
            parity = parity,
            yIntercept = yIntercept?.let(::tidy),
            zeros = zeros,
            positive = sign.first,
            negative = sign.second,
            increasing = growth.first,
            decreasing = growth.second,
            maxima = maxima,
            minima = minima,
            convex = curvature.first,
            concave = curvature.second,
            inflections = inflections,
            vertical = vertical,
            lines = lines,
        )
    }

    // -------------------------------------------------------------------- dominio

    /** Confine del dominio tra [inside] (definita) e [outside] (non definita), per bisezione a 34 cifre. */
    private fun boundary(f: UserFunction, inside: BigDecimal, outside: BigDecimal): Pair<BigDecimal, Boolean> {
        var a = inside
        var b = outside
        repeat(110) {
            val mid = a.add(b, WORK).divide(TWO, WORK)
            if (f.atOrNull(mid) != null) a = mid else b = mid
        }
        val point = clean(a)
        return point to (f.atOrNull(point) != null)
    }

    /**
     * Polo tra due campioni definiti con segni opposti: lo zero "finto" che lo strumento degli zeri scarta.
     * Si cerca per bisezione sul segno e si conferma che |f| cresce senza limite.
     */
    private fun pole(f: UserFunction, a: BigDecimal, b: BigDecimal, ya: Double, yb: Double): BigDecimal? {
        if (ya == 0.0 || yb == 0.0 || (ya > 0) == (yb > 0)) return null
        if (abs(ya) + abs(yb) < 1.0) return null
        var lo = a
        var hi = b
        val loSign = ya > 0
        repeat(110) {
            val mid = lo.add(hi, WORK).divide(TWO, WORK)
            val y = f.atOrNull(mid)
            when {
                y == null -> return clean(mid) // non definita proprio nel polo
                (y.signum() > 0) == loSign -> lo = mid
                else -> hi = mid
            }
        }
        val x = clean(lo.add(hi, WORK).divide(TWO, WORK))
        return x.takeIf { diverges(f, it) }
    }

    /** Polo di ordine pari tra [a] e [b]: massimo di |f| (ricerca ternaria) dove |f| esplode. */
    private fun evenPole(f: UserFunction, fast: (Double) -> Double, a: BigDecimal, b: BigDecimal): BigDecimal? {
        var lo = a.toDouble()
        var hi = b.toDouble()
        repeat(200) {
            val m1 = lo + (hi - lo) / 3
            val m2 = hi - (hi - lo) / 3
            val y1 = abs(fast(m1)).let { if (it.isNaN()) Double.POSITIVE_INFINITY else it }
            val y2 = abs(fast(m2)).let { if (it.isNaN()) Double.POSITIVE_INFINITY else it }
            if (y1 < y2) lo = m1 else hi = m2
        }
        val x = clean(BigDecimal((lo + hi) / 2))
        return x.takeIf { diverges(f, it) }
    }

    /** True se |f| cresce senza limite avvicinandosi a [x] da almeno un lato (o se f non vi è definita per overflow). */
    private fun diverges(f: UserFunction, x: BigDecimal): Boolean {
        val scale = x.abs().max(BigDecimal.ONE)
        return listOf(1, -1).any { side ->
            val values = listOf("1E-3", "1E-6", "1E-9", "1E-12").map { h ->
                when (val r = f.result(x.add(scale.multiply(BigDecimal(h)).multiply(BigDecimal(side)), WORK))) {
                    is EvalResult.Success -> r.value.abs().toDouble()
                    is EvalResult.Failure -> if (r.error == CalcError.OVERFLOW) Double.POSITIVE_INFINITY else Double.NaN
                }
            }
            values.none { it.isNaN() } &&
                values.zipWithNext().all { (p, q) -> q > p } &&
                values.last() >= 2 * values.first() + 10
        }
    }

    /** Tratti del dominio dentro [low, high], tra i confini trovati e senza i poli. */
    private fun domain(
        f: UserFunction,
        low: BigDecimal,
        high: BigDecimal,
        ys: List<Double>,
        boundaries: List<Pair<BigDecimal, Boolean>>,
        poles: List<BigDecimal>,
    ): List<Interval> {
        val points = (boundaries.map { it.first } + poles).sorted()
        val cuts = listOf(low) + points + listOf(high)
        val pieces = ArrayList<Interval>()
        for (i in 0 until cuts.size - 1) {
            val a = cuts[i]
            val b = cuts[i + 1]
            if (a.compareTo(b) >= 0) continue
            val mid = a.add(b, WORK).divide(TWO, WORK)
            if (f.atOrNull(mid) == null) continue
            fun bound(x: BigDecimal, isEdge: Boolean): Bound? {
                if (isEdge) return null
                val closed = boundaries.firstOrNull { it.first.compareTo(x) == 0 }?.second == true && poles.none { it.compareTo(x) == 0 }
                return Bound(x, closed)
            }
            pieces += Interval(bound(a, i == 0), bound(b, i + 1 == cuts.size - 1))
        }
        // Tratti consecutivi separati da un punto del dominio si uniscono.
        val merged = ArrayList<Interval>()
        for (piece in pieces) {
            val last = merged.lastOrNull()
            if (last != null && last.high != null && piece.low != null &&
                last.high.value.compareTo(piece.low.value) == 0 && (last.high.closed || piece.low.closed)
            ) {
                merged[merged.size - 1] = Interval(last.low, piece.high)
            } else {
                merged += piece
            }
        }
        return merged
    }

    // -------------------------------------------------------------------- simmetrie

    private fun parity(f: UserFunction, low: BigDecimal, high: BigDecimal): Parity? {
        val reach = high.min(low.negate())
        if (reach.signum() <= 0) return null
        var even = true
        var odd = true
        var tested = 0
        for (k in 1..24) {
            val x = reach.multiply(BigDecimal(k)).divide(BigDecimal(25), WORK).add(BigDecimal("0.0123456789"), WORK)
            if (x > reach) continue
            val p = f.atOrNull(x)
            val n = f.atOrNull(x.negate())
            if ((p == null) != (n == null)) return Parity.NONE // dominio non simmetrico
            if (p == null || n == null) continue
            val tolerance = BigDecimal("1E-25").multiply(p.abs().max(n.abs()).max(BigDecimal.ONE))
            if (p.subtract(n).abs() > tolerance) even = false
            if (p.add(n).abs() > tolerance) odd = false
            tested++
        }
        return when {
            tested < 5 -> null
            even && !odd -> Parity.EVEN
            odd && !even -> Parity.ODD
            even && odd -> Parity.EVEN // f = 0
            else -> Parity.NONE
        }
    }

    // -------------------------------------------------------------------- segni

    /**
     * Insiemi dove [sign] è positivo e negativo, dentro i tratti del dominio, spezzati nei punti [breaks].
     * Il segno di ogni tratto si legge nel suo punto medio a 34 cifre.
     */
    private fun signs(pieces: List<Interval>, breaks: List<BigDecimal>, sign: (BigDecimal) -> Int?): Pair<SolutionSet, SolutionSet> {
        val plus = ArrayList<Interval>()
        val minus = ArrayList<Interval>()
        for (piece in pieces) {
            val inner = breaks.filter { x ->
                (piece.low == null || x > piece.low.value) && (piece.high == null || x < piece.high.value)
            }.distinct()
            val lows = listOf(piece.low) + inner.map { Bound(it, false) }
            val highs = inner.map { Bound(it, false) } + listOf(piece.high)
            for (i in lows.indices) {
                val a = lows[i]
                val b = highs[i]
                val test = when {
                    a == null && b == null -> BigDecimal.ZERO
                    a == null -> b!!.value.subtract(b.value.abs().max(BigDecimal.ONE).divide(BigDecimal(1000), WORK), WORK)
                    b == null -> a.value.add(a.value.abs().max(BigDecimal.ONE).divide(BigDecimal(1000), WORK), WORK)
                    else -> a.value.add(b.value, WORK).divide(TWO, WORK)
                }
                val s = sign(test) ?: continue
                if (s == 0) continue
                // Gli estremi interni sono zeri o punti esclusi: aperti. Un estremo chiuso del dominio resta
                // chiuso solo se lì il segno è lo stesso del tratto (√x in 0 vale 0: non è positiva).
                fun keep(bound: Bound?, edge: Boolean) =
                    bound?.let { Bound(it.value, it.closed && edge && sign(it.value) == s) }
                val interval = Interval(keep(a, i == 0), keep(b, i == lows.size - 1))
                if (s > 0) plus += interval else minus += interval
            }
        }
        return SolutionSet(plus) to SolutionSet(minus)
    }

    /**
     * Punti dove g cambia segno: cercati con la stima veloce [fastG] sui campioni, confermati con [exactG]
     * a 34 cifre (così il rumore di una stima vicina a zero, come f″ di una retta, non conta) e raffinati.
     * Se il raffinamento non riesce (spigolo, come |x| in 0) si tiene il punto trovato per bisezione.
     */
    private fun changes(
        xs: List<BigDecimal>,
        fastG: (Double) -> Double,
        exactG: (BigDecimal) -> BigDecimal?,
        barriers: List<BigDecimal>,
        step: BigDecimal,
    ): List<BigDecimal> {
        val signs = xs.map { x -> fastG(x.toDouble()).let { if (it.isFinite()) it.compareTo(0.0).coerceIn(-1, 1) else 0 } }
        val found = ArrayList<BigDecimal>()
        var previous = -1
        for (i in xs.indices) {
            if (signs[i] == 0) continue
            val start = previous
            previous = i
            if (start < 0 || signs[start] == signs[i]) continue
            val a = xs[start]
            val b = xs[i]
            if (barriers.any { it >= a.subtract(step, WORK) && it <= b.add(step, WORK) }) continue
            // Conferma a 34 cifre: segni opposti e non nulli agli estremi.
            val ea = exactG(a)?.signum()
            val eb = exactG(b)?.signum()
            if (ea != null && eb != null) {
                if (ea == eb) continue // rumore della stima veloce (f″ di una retta, per esempio)
                if (ea == 0 || eb == 0) {
                    // Zero esatto su un campione: vale se a sinistra e a destra il segno cambia davvero.
                    val p = if (ea == 0) a else b
                    val delta = step.divide(TWO, WORK)
                    val left = exactG(p.subtract(delta, WORK))?.signum()
                    val right = exactG(p.add(delta, WORK))?.signum()
                    if (left != null && right != null && left != 0 && right != 0 && left != right && found.none { near(it, p, step) }) {
                        found += clean(p)
                    }
                    continue
                }
            }
            val root = NumericSolver.rootBetween(exactG, a, b) ?: run {
                var lo = a.toDouble()
                var hi = b.toDouble()
                val loSign = signs[start]
                repeat(80) {
                    val mid = (lo + hi) / 2
                    if (fastG(mid).compareTo(0.0).coerceIn(-1, 1) == loSign) lo = mid else hi = mid
                }
                // Trovato in double: 13 cifre al massimo.
                val mid = (lo + hi) / 2
                if (abs(mid) < 1e-13 * maxOf(1.0, abs(a.toDouble()))) BigDecimal.ZERO else BigDecimal(mid).round(MathContext(13))
            }
            val x = clean(root)
            if (found.none { near(it, x, step) }) found += x
        }
        return found
    }

    /** f″(x) con differenze centrali a 34 cifre (circa 15 cifre corrette); null dove non si può calcolare. */
    private fun secondDerivative(f: UserFunction, x: BigDecimal): BigDecimal? {
        val h = x.abs().max(BigDecimal.ONE).multiply(BigDecimal("1E-8"))
        val center = f.atOrNull(x) ?: return null
        val plus = f.atOrNull(x.add(h, WORK)) ?: return null
        val minus = f.atOrNull(x.subtract(h, WORK)) ?: return null
        val value = plus.subtract(center.multiply(TWO), WORK).add(minus, WORK).divide(h.multiply(h, WORK), WORK)
        return if (value.abs() < BigDecimal("1E-12")) BigDecimal.ZERO else value
    }

    // -------------------------------------------------------------------- asintoti orizzontali e obliqui

    /**
     * Retta y = mx + q a cui f si avvicina per x → ±∞ ([side]), stimata con x = 10⁶, 10⁹, 10¹²:
     * m e q devono stabilizzarsi. Rischioso per natura (convergenze lente, oscillazioni): null se i
     * valori non si stabilizzano.
     */
    private fun lineAt(f: UserFunction, side: Int): Asymptote.Line? {
        val points = listOf("1E6", "1E9", "1E12").map { BigDecimal(it).multiply(BigDecimal(side)) }
        val values = points.map { f.atOrNull(it) ?: return null }
        fun slope(i: Int, j: Int) = values[j].subtract(values[i], WORK).divide(points[j].subtract(points[i], WORK), WORK)
        val m1 = slope(0, 1)
        val m2 = slope(1, 2)
        val mScale = m2.abs().max(BigDecimal.ONE)
        if (m1.subtract(m2, WORK).abs() > mScale.multiply(BigDecimal("1E-5"))) return null
        val m = round(m2)
        val q1 = values[1].subtract(m.multiply(points[1], WORK), WORK)
        val q2 = values[2].subtract(m.multiply(points[2], WORK), WORK)
        val qScale = q2.abs().max(BigDecimal.ONE)
        if (q1.subtract(q2, WORK).abs() > qScale.multiply(BigDecimal("1E-5"))) return null
        val q = round(q2)
        // Una retta non ha asintoti: f coincide già con y = mx + q (si controlla vicino all'origine).
        val exact = listOf("1.37", "2.91", "4.53").all { t ->
            val x = BigDecimal(t).multiply(BigDecimal(side))
            val y = f.atOrNull(x) ?: return@all false
            y.subtract(m.multiply(x, WORK).add(q, WORK), WORK).abs() < BigDecimal("1E-20").multiply(y.abs().max(BigDecimal.ONE))
        }
        return if (exact) null else Asymptote.Line(m, q, side)
    }

    // -------------------------------------------------------------------- utilità

    /** Pendenza e quota di un asintoto: 10 cifre significative (le stime ai grandi x non danno di più). */
    private fun round(x: BigDecimal): BigDecimal {
        if (x.abs() < BigDecimal("1E-9")) return BigDecimal.ZERO
        return x.round(MathContext(10, RoundingMode.HALF_EVEN)).stripTrailingZeros()
    }

    /** Toglie il rumore delle ultime cifre dei punti trovati per bisezione o con il risolutore. */
    private fun clean(x: BigDecimal): BigDecimal {
        if (x.abs() < BigDecimal("1E-24")) return BigDecimal.ZERO
        return x.round(MathContext(26, RoundingMode.HALF_EVEN)).stripTrailingZeros()
    }

    private fun tidy(y: BigDecimal): BigDecimal {
        if (y.abs() < BigDecimal("1E-20")) return BigDecimal.ZERO
        return y.round(MathContext(22, RoundingMode.HALF_EVEN)).stripTrailingZeros()
    }

    private fun near(a: BigDecimal, b: BigDecimal, step: BigDecimal) = a.subtract(b, WORK).abs() <= step.divide(BigDecimal(100), WORK)
}
