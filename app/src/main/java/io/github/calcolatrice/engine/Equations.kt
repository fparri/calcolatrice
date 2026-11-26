package io.github.calcolatrice.engine

import java.math.BigDecimal

/** Soluzione di un sistema lineare (o di un'equazione di primo grado). */
sealed interface LinearSolution {
    data class Unique(val values: List<BigDecimal>) : LinearSolution
    data object None : LinearSolution
    data object Infinite : LinearSolution
}

/** Vertice della parabola y = ax² + bx + c: minimo se a > 0, massimo se a < 0. */
data class Vertex(val x: BigDecimal, val y: BigDecimal, val isMinimum: Boolean)

/** Soluzione di ax² + bx + c = 0. */
sealed interface QuadraticSolution {
    /** Due radici reali distinte, dalla maggiore alla minore. */
    data class TwoReal(val x1: BigDecimal, val x2: BigDecimal, val vertex: Vertex) : QuadraticSolution

    /** Una radice reale doppia. */
    data class DoubleRoot(val x: BigDecimal, val vertex: Vertex) : QuadraticSolution

    /** Due radici complesse coniugate `re ± im·i`, con `im > 0`. */
    data class ComplexPair(val re: BigDecimal, val im: BigDecimal, val vertex: Vertex) : QuadraticSolution

    /** `a = 0`: l'equazione è di primo grado, `bx + c = 0`. */
    data class Linear(val solution: LinearSolution) : QuadraticSolution
}

/**
 * I coefficienti arrivano dal motore a 34 cifre: `1/3` è 0,333…3 e non un terzo esatto.
 * Sotto questa frazione della scala dei dati un valore è considerato zero.
 */
private val RELATIVE_ZERO = BigDecimal("1E-25")
private val TWO = BigDecimal(2)
private val FOUR = BigDecimal(4)

private fun isNegligible(x: BigDecimal, scale: BigDecimal): Boolean =
    x.abs() <= scale.abs().multiply(RELATIVE_ZERO)

/** Azzera i residui di arrotondamento e controlla che il valore sia nell'intervallo gestibile. */
private fun clean(x: BigDecimal, scale: BigDecimal): BigDecimal =
    if (isNegligible(x, scale)) BigDecimal.ZERO else MathSupport.checked(x.round(MathSupport.MC))

object LinearSystem {

    /**
     * Risolve un sistema di n equazioni in n incognite con l'eliminazione di Gauss
     * (pivot parziale, 34 cifre significative).
     *
     * Prima si normalizzano righe e colonne (ogni riga e ogni colonna ha coefficiente massimo 1),
     * così la soglia sotto cui un valore conta come zero non dipende dalla scala dei dati:
     * 10¹⁵x = 10¹⁵ insieme a 10⁻¹⁵y = 2·10⁻¹⁵ si risolve come x = 1 e y = 2.
     * @param rows n righe, ognuna con gli n coefficienti seguiti dal termine noto.
     */
    fun solve(rows: List<List<BigDecimal>>): LinearSolution {
        val n = rows.size
        require(n >= 1 && rows.all { it.size == n + 1 }) { "Servono n righe di n + 1 valori" }
        val mc = MathSupport.MC
        val m = Array(n) { r -> Array(n + 1) { c -> rows[r][c] } }

        // Righe senza coefficienti: "0 = termine noto".
        val emptyRows = (0 until n).filter { r -> (0 until n).all { m[r][it].signum() == 0 } }
        if (emptyRows.any { m[it][n].signum() != 0 }) return LinearSolution.None

        // Normalizzazione delle righe (termine noto compreso) e poi delle colonne dei coefficienti.
        for (r in 0 until n) {
            if (r in emptyRows) continue
            val max = (0 until n).maxOf { m[r][it].abs() }
            for (c in 0..n) m[r][c] = m[r][c].divide(max, mc)
        }
        val columnScale = Array(n) { c -> (0 until n).maxOf { m[it][c].abs() } }
        for (c in 0 until n) {
            val scale = columnScale[c]
            if (scale.signum() == 0) continue
            for (r in 0 until n) m[r][c] = m[r][c].divide(scale, mc)
        }
        val constantScale = (0 until n).maxOf { m[it][n].abs() }

        // Riduzione a scala.
        var row = 0
        for (col in 0 until n) {
            val pivot = (row until n).maxByOrNull { m[it][col].abs() } ?: break
            if (isNegligible(m[pivot][col], BigDecimal.ONE)) continue
            m[pivot] = m[row].also { m[row] = m[pivot] }
            for (r in row + 1 until n) {
                val factor = m[r][col].divide(m[row][col], mc)
                for (c in col..n) m[r][c] = m[r][c].subtract(factor.multiply(m[row][c], mc), mc)
                m[r][col] = BigDecimal.ZERO
            }
            row++
        }

        // Le righe rimaste senza coefficienti dicono "0 = termine noto".
        for (r in row until n) {
            if (!isNegligible(m[r][n], constantScale)) return LinearSolution.None
        }
        if (row < n) return LinearSolution.Infinite

        // Sostituzione all'indietro, poi si torna alle incognite originali.
        val y = Array(n) { BigDecimal.ZERO }
        for (r in n - 1 downTo 0) {
            var sum = m[r][n]
            for (c in r + 1 until n) sum = sum.subtract(m[r][c].multiply(y[c], mc), mc)
            y[r] = sum.divide(m[r][r], mc)
        }
        val x = (0 until n).map { c -> y[c].divide(columnScale[c], mc) }
        // Residui di arrotondamento: rispetto alla propria colonna, non agli altri valori.
        return LinearSolution.Unique(
            x.mapIndexed { c, value ->
                val residue = isNegligible(y[c], y.maxOf { it.abs() })
                if (residue) BigDecimal.ZERO else MathSupport.checked(value.round(mc))
            },
        )
    }
}

object QuadraticEquation {

    /** Risolve ax² + bx + c = 0. */
    fun solve(a: BigDecimal, b: BigDecimal, c: BigDecimal): QuadraticSolution {
        if (a.signum() == 0) {
            return QuadraticSolution.Linear(LinearSystem.solve(listOf(listOf(b, c.negate()))))
        }
        val mc = MathSupport.MC
        val twoA = a.multiply(TWO)

        val vertexX = b.negate().divide(twoA, mc)
        val bSquaredOver4a = b.multiply(b).divide(a.multiply(FOUR), mc)
        val vertexY = c.subtract(bSquaredOver4a, mc)
        val vertex = Vertex(
            x = clean(vertexX, b.divide(a, mc)),
            y = clean(vertexY, c.abs().max(bSquaredOver4a.abs())),
            isMinimum = a.signum() > 0,
        )

        // Δ è esatto (le moltiplicazioni senza MathContext non arrotondano); poi si tolgono i residui.
        val bSquared = b.multiply(b)
        val fourAc = FOUR.multiply(a).multiply(c)
        val rawDelta = bSquared.subtract(fourAc)
        val delta = if (isNegligible(rawDelta, bSquared.max(fourAc.abs()))) BigDecimal.ZERO else rawDelta

        return when (delta.signum()) {
            0 -> QuadraticSolution.DoubleRoot(vertex.x, vertex)
            1 -> {
                // Formula stabile: evita di sottrarre due numeri quasi uguali.
                val root = MathSupport.sqrt(delta)
                val q = (if (b.signum() >= 0) b.add(root) else b.subtract(root)).negate().divide(TWO, mc)
                // Con la formula stabile entrambe le radici hanno tutte le cifre corrette, anche quando
                // una è piccolissima rispetto all'altra (x² − 10¹³x + 1): non vanno azzerate.
                val r1 = MathSupport.checked(q.divide(a, mc))
                val r2 = MathSupport.checked(c.divide(q, mc))
                val (x1, x2) = if (r1 >= r2) r1 to r2 else r2 to r1
                QuadraticSolution.TwoReal(x1, x2, vertex)
            }
            else -> {
                val im = MathSupport.sqrt(delta.negate()).divide(twoA.abs(), mc)
                QuadraticSolution.ComplexPair(vertex.x, MathSupport.checked(im), vertex)
            }
        }
    }
}

/** Soluzione di ax³ + bx² + cx + d = 0. */
sealed interface CubicSolution {
    /**
     * Radici reali dalla maggiore alla minore, ripetute secondo la molteplicità
     * (tre valori, oppure uno solo se le altre due sono la coppia complessa `re ± im·i`).
     */
    data class Roots(val real: List<BigDecimal>, val complex: Pair<BigDecimal, BigDecimal>? = null) : CubicSolution

    /** `a = 0`: l'equazione è di secondo grado. */
    data class Quadratic(val solution: QuadraticSolution) : CubicSolution
}

object CubicEquation {

    fun solve(a: BigDecimal, b: BigDecimal, c: BigDecimal, d: BigDecimal): CubicSolution {
        if (a.signum() == 0) return CubicSolution.Quadratic(QuadraticEquation.solve(b, c, d))
        val mc = BigMath.WORK
        val three = BigDecimal(3)

        // Discriminante calcolato esatto: se è zero ci sono radici multiple, con formule esatte.
        val terms = listOf(
            BigDecimal(18).multiply(a).multiply(b).multiply(c).multiply(d),
            FOUR.multiply(b.pow(3)).multiply(d).negate(),
            b.pow(2).multiply(c.pow(2)),
            FOUR.multiply(a).multiply(c.pow(3)).negate(),
            BigDecimal(27).multiply(a.pow(2)).multiply(d.pow(2)).negate(),
        )
        val discriminant = terms.fold(BigDecimal.ZERO, BigDecimal::add)
        val delta0 = b.pow(2).subtract(three.multiply(a).multiply(c))
        if (isNegligible(discriminant, terms.maxOf { it.abs() })) {
            val roots = if (isNegligible(delta0, b.pow(2).max(three.multiply(a).multiply(c).abs()))) {
                val triple = b.negate().divide(three.multiply(a), mc)
                listOf(triple, triple, triple)
            } else {
                val double = BigDecimal(9).multiply(a).multiply(d).subtract(b.multiply(c))
                    .divide(TWO.multiply(delta0), mc)
                val single = FOUR.multiply(a).multiply(b).multiply(c)
                    .subtract(BigDecimal(9).multiply(a.pow(2)).multiply(d))
                    .subtract(b.pow(3))
                    .divide(a.multiply(delta0), mc)
                listOf(single, double, double)
            }
            return finish(roots, null, a, b, c, d)
        }

        // Una radice reale c'è sempre; le altre due vengono dall'equazione di secondo grado che resta.
        val r = realRoot(a, b, c, d)
        val q1 = b.add(a.multiply(r, mc), mc)
        val q0 = c.add(q1.multiply(r, mc), mc)
        val polish = { x: BigDecimal -> newton(a, b, c, d, x) }
        return when (val rest = QuadraticEquation.solve(a, q1, q0)) {
            is QuadraticSolution.TwoReal -> finish(listOf(r, polish(rest.x1), polish(rest.x2)), null, a, b, c, d)
            is QuadraticSolution.DoubleRoot -> finish(listOf(r, polish(rest.x), polish(rest.x)), null, a, b, c, d)
            is QuadraticSolution.ComplexPair -> finish(listOf(r), rest.re to rest.im, a, b, c, d)
            is QuadraticSolution.Linear -> finish(listOf(r), null, a, b, c, d) // impossibile con a ≠ 0
        }
    }

    /**
     * Ordina e arrotonda le radici. Se 0 è una radice (termine noto trascurabile rispetto agli altri
     * coefficienti), le radici più vicine a 0 diventano esattamente 0, tante quante la sua molteplicità;
     * le altre restano come sono, anche se minuscole: x³ − 10¹³x² + x ha radici 10¹³, 10⁻¹³ e 0.
     * La parte reale delle radici complesse si confronta con la parte immaginaria.
     */
    private fun finish(
        real: List<BigDecimal>,
        complex: Pair<BigDecimal, BigDecimal>?,
        a: BigDecimal,
        b: BigDecimal,
        c: BigDecimal,
        d: BigDecimal,
    ): CubicSolution.Roots {
        val scale = real.maxOf { it.abs() }.max(complex?.let { it.first.abs().max(it.second.abs()) } ?: BigDecimal.ZERO)
        val coefficientScale = a.abs().max(b.abs()).max(c.abs()).max(d.abs())
        // Molteplicità della radice 0: quanti coefficienti finali sono trascurabili (d, poi c, poi b).
        val zeroMultiplicity = listOf(d, c, b).takeWhile { isNegligible(it, coefficientScale) }.size
        val zeroed = real.indices.sortedBy { real[it].abs() }.take(zeroMultiplicity).toSet()
        val sorted = real.mapIndexed { i, x ->
            if (i in zeroed) clean(x, scale) else MathSupport.checked(x.round(MathSupport.MC))
        }.sortedDescending()
        return CubicSolution.Roots(
            sorted,
            complex?.let { (re, im) -> clean(re, im) to MathSupport.checked(im.round(MathSupport.MC)) },
        )
    }

    private fun value(a: BigDecimal, b: BigDecimal, c: BigDecimal, d: BigDecimal, x: BigDecimal): BigDecimal {
        val mc = BigMath.WORK
        return a.multiply(x, mc).add(b, mc).multiply(x, mc).add(c, mc).multiply(x, mc).add(d, mc)
    }

    /** Raffina una radice con due passi di Newton sul polinomio originale. */
    private fun newton(a: BigDecimal, b: BigDecimal, c: BigDecimal, d: BigDecimal, start: BigDecimal): BigDecimal {
        val mc = BigMath.WORK
        var x = start
        repeat(3) {
            val derivative = BigDecimal(3).multiply(a).multiply(x, mc).add(TWO.multiply(b), mc).multiply(x, mc).add(c, mc)
            if (derivative.signum() == 0) return x
            x = x.subtract(value(a, b, c, d, x).divide(derivative, mc), mc)
        }
        return x
    }

    /** Una radice reale per bisezione tra ±R (limite di Cauchy), poi Newton. */
    private fun realRoot(a: BigDecimal, b: BigDecimal, c: BigDecimal, d: BigDecimal): BigDecimal {
        if (d.signum() == 0) return BigDecimal.ZERO
        val mc = BigMath.WORK
        val bound = BigDecimal.ONE.add(b.abs().max(c.abs()).max(d.abs()).divide(a.abs(), mc), mc)
        // Il segno di f(+R) è quello di a, il segno di f(−R) è l'opposto.
        var low = bound.negate()
        var high = bound
        if (a.signum() < 0) {
            low = bound
            high = bound.negate()
        }
        val tolerance = BigDecimal("1E-45")
        repeat(600) {
            val middle = low.add(high, mc).divide(TWO, mc)
            val fm = value(a, b, c, d, middle)
            if (fm.signum() == 0) return middle
            if (fm.signum() < 0) low = middle else high = middle
            if (high.subtract(low).abs() <= low.abs().max(high.abs()).multiply(tolerance)) {
                return newton(a, b, c, d, middle)
            }
        }
        return newton(a, b, c, d, low.add(high, mc).divide(TWO, mc))
    }
}
