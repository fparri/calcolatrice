package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

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
     * @param rows n righe, ognuna con gli n coefficienti seguiti dal termine noto.
     */
    fun solve(rows: List<List<BigDecimal>>): LinearSolution {
        val n = rows.size
        require(n >= 1 && rows.all { it.size == n + 1 }) { "Servono n righe di n + 1 valori" }
        val mc = MathSupport.MC
        val m = Array(n) { r -> Array(n + 1) { c -> rows[r][c] } }

        val coefficientScale = rows.maxOf { row -> row.dropLast(1).maxOf { it.abs() } }
        val constantScale = rows.maxOf { it.last().abs() }
        if (coefficientScale.signum() == 0) {
            return if (constantScale.signum() == 0) LinearSolution.Infinite else LinearSolution.None
        }

        // Riduzione a scala.
        var row = 0
        for (col in 0 until n) {
            val pivot = (row until n).maxBy { m[it][col].abs() }
            if (isNegligible(m[pivot][col], coefficientScale)) continue
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

        // Sostituzione all'indietro.
        val x = Array(n) { BigDecimal.ZERO }
        for (r in n - 1 downTo 0) {
            var sum = m[r][n]
            for (c in r + 1 until n) sum = sum.subtract(m[r][c].multiply(x[c], mc), mc)
            x[r] = sum.divide(m[r][r], mc)
        }
        val resultScale = x.maxOf { it.abs() }
        return LinearSolution.Unique(x.map { clean(it, resultScale) })
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
                val r1 = q.divide(a, mc)
                val r2 = c.divide(q, mc)
                val scale = r1.abs().max(r2.abs())
                val (x1, x2) = if (r1 >= r2) r1 to r2 else r2 to r1
                QuadraticSolution.TwoReal(clean(x1, scale), clean(x2, scale), vertex)
            }
            else -> {
                val im = MathSupport.sqrt(delta.negate()).divide(twoA.abs(), mc)
                QuadraticSolution.ComplexPair(vertex.x, MathSupport.checked(im), vertex)
            }
        }
    }
}

/** Frazione ridotta con denominatore positivo. */
data class Fraction(val numerator: BigInteger, val denominator: BigInteger)

object Fractions {
    /** Come sulle calcolatrici: oltre questi limiti il valore resta decimale. */
    private val MAX_DENOMINATOR = BigInteger.valueOf(1_000_000)
    private const val MAX_NUMERATOR_DIGITS = 12
    private val TOLERANCE = BigDecimal("1E-20")

    /**
     * Riconosce la frazione che corrisponde a [x] (calcolato a 34 cifre) con le frazioni continue.
     * Restituisce null se il valore non è razionale con numeri "ragionevoli", per esempio √2 o π.
     */
    fun toFraction(x: BigDecimal): Fraction? {
        if (x.signum() == 0) return Fraction(BigInteger.ZERO, BigInteger.ONE)
        val target = x.abs()
        val tolerance = target.max(BigDecimal.ONE).multiply(TOLERANCE)
        val mc = MathSupport.MC
        var h1 = BigInteger.ONE
        var h2 = BigInteger.ZERO
        var k1 = BigInteger.ZERO
        var k2 = BigInteger.ONE
        var y = target
        repeat(64) {
            val a = y.setScale(0, RoundingMode.FLOOR).toBigIntegerExact()
            val h = a * h1 + h2
            val k = a * k1 + k2
            if (k > MAX_DENOMINATOR) return null
            val approximation = BigDecimal(h).divide(BigDecimal(k), mc)
            if (target.subtract(approximation).abs() <= tolerance) {
                if (h.toString().length > MAX_NUMERATOR_DIGITS) return null
                return Fraction(if (x.signum() < 0) h.negate() else h, k)
            }
            val rest = y.subtract(BigDecimal(a))
            if (rest.signum() == 0) return null
            y = BigDecimal.ONE.divide(rest, mc)
            h2 = h1
            h1 = h
            k2 = k1
            k1 = k
        }
        return null
    }
}

/** Testo dei risultati di equazioni e sistemi, come decimale o come frazione. */
class SolutionFormatter(private val display: DisplayFormatter) {

    fun value(x: BigDecimal, asFraction: Boolean): String {
        if (asFraction) {
            val fraction = Fractions.toFraction(x)
            if (fraction != null && fraction.denominator != BigInteger.ONE) {
                return display.format(fraction.numerator.toString()) + "/" + display.format(fraction.denominator.toString())
            }
        }
        return display.format(ResultFormatter.toInternal(x))
    }

    /** `re + im·i` oppure `re − im·i`. */
    fun complex(re: BigDecimal, im: BigDecimal, plus: Boolean, asFraction: Boolean): String {
        val imText = value(im, asFraction)
        val imaginary = when {
            imText == "1" -> "i"
            '/' in imText -> "($imText)i"
            else -> imText + "i"
        }
        if (re.signum() == 0) return if (plus) imaginary else "−$imaginary"
        return value(re, asFraction) + (if (plus) " + " else " − ") + imaginary
    }
}
