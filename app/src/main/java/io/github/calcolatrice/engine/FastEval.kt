package io.github.calcolatrice.engine

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Valutazione veloce in `double` di un'espressione nella variabile X, per disegnare i grafici:
 * il calcolo a 34 cifre richiede decine di microsecondi per punto, troppo per ridisegnare una curva
 * a ogni movimento del dito. Usa lo stesso albero di [ExpressionParser], quindi le stesse regole;
 * fuori dal dominio restituisce NaN. I valori che si leggono come numeri (tabella, zeri, derivate)
 * usano invece il calcolo a 34 cifre.
 */
object FastEval {

    /** Funzione x → f(x), NaN dove non è definita. Null se l'espressione non è valida. */
    fun compile(
        expression: String,
        angleUnit: AngleUnit,
        variables: Map<Char, BigDecimal> = emptyMap(),
        ans: BigDecimal? = null,
        unknown: Char = 'X',
    ): ((Double) -> Double)? {
        val root = try {
            ExpressionParser.parse(expression)
        } catch (e: CalcException) {
            return null
        }
        return compile(root, angleUnit, variables, ans, unknown)
    }

    internal fun compile(
        root: Expr,
        angleUnit: AngleUnit,
        variables: Map<Char, BigDecimal> = emptyMap(),
        ans: BigDecimal? = null,
        unknown: Char = 'X',
    ): (Double) -> Double {
        val evaluation = DoubleEvaluation(angleUnit, variables.mapValues { it.value.toDouble() }, ans?.toDouble(), unknown)
        return { x -> evaluation.value(root, x) }
    }
}

/** Valutazione in double di un [Expr], con le stesse convenzioni di [Evaluation] (NaN al posto degli errori). */
private class DoubleEvaluation(
    private val angleUnit: AngleUnit,
    private val variables: Map<Char, Double>,
    private val ans: Double?,
    private val unknown: Char,
) {
    fun value(e: Expr, x: Double): Double = when (e) {
        is Expr.Num -> e.value.toDouble()
        is Expr.Const -> when (e.symbol) {
            'π' -> Math.PI
            'e' -> Math.E
            else -> ans ?: Double.NaN
        }
        is Expr.Var -> if (e.name == unknown) x else variables[e.name] ?: 0.0
        is Expr.Neg -> -value(e.operand, x)
        is Expr.Sum -> {
            val left = value(e.left, x)
            val right = value(e.right, x).let { if (e.percentOfLeft) left * it else it }
            if (e.subtract) left - right else left + right
        }
        is Expr.Product -> value(e.left, x) * value(e.right, x)
        is Expr.Quotient -> {
            val left = value(e.left, x)
            val divisor = value(e.right, x)
            if (divisor == 0.0) Double.NaN else left / divisor
        }
        is Expr.Power -> power(value(e.base, x), value(e.exponent, x))
        is Expr.Factorial -> factorial(value(e.operand, x))
        is Expr.Percent -> value(e.operand, x) / 100
        is Expr.Sqrt -> value(e.operand, x).let { if (it < 0) Double.NaN else kotlin.math.sqrt(it) }
        is Expr.Call -> call(e.name, e.args.map { value(it, x) })
    }

    private fun call(name: String, args: List<Double>): Double {
        val a = args[0]
        return when (name) {
            "sin", "cos", "tan" -> trig(name, a)
            "asin" -> fromRadians(kotlin.math.asin(a))
            "acos" -> fromRadians(kotlin.math.acos(a))
            "atan" -> fromRadians(kotlin.math.atan(a))
            "ln" -> if (a > 0) kotlin.math.ln(a) else Double.NaN
            "log" -> if (args.size == 1) {
                if (a > 0) kotlin.math.log10(a) else Double.NaN
            } else {
                val v = args[1]
                if (a <= 0 || a == 1.0 || v <= 0) Double.NaN else kotlin.math.ln(v) / kotlin.math.ln(a)
            }
            "abs" -> abs(a)
            "root" -> root(a, args[1])
            "ncr" -> combinations(a, args[1])
            "npr" -> permutations(a, args[1])
            // Le altre funzioni sono rare in un grafico: si calcolano a 34 cifre.
            else -> precise(name, args)
        }
    }

    private fun precise(name: String, args: List<Double>): Double = try {
        if (args.any { !it.isFinite() }) {
            Double.NaN
        } else {
            Evaluation(angleUnit, null).value(Expr.Call(name, args.map { Expr.Num(BigDecimal(it)) })).toDouble()
        }
    } catch (e: CalcException) {
        Double.NaN
    } catch (e: ArithmeticException) {
        Double.NaN
    }

    /** Come [MathSupport.trig]: in gradi valori esatti per i multipli di 90°. */
    private fun trig(name: String, x: Double): Double {
        if (!x.isFinite()) return Double.NaN
        val radians = if (angleUnit == AngleUnit.DEGREES) {
            var angle = x % 360.0
            if (angle < 0) angle += 360.0
            if (angle == floor(angle) && angle.toInt() % 90 == 0) {
                val quadrant = angle.toInt() / 90
                return when (name) {
                    "sin" -> doubleArrayOf(0.0, 1.0, 0.0, -1.0)[quadrant]
                    "cos" -> doubleArrayOf(1.0, 0.0, -1.0, 0.0)[quadrant]
                    else -> if (quadrant % 2 == 1) Double.NaN else 0.0
                }
            }
            Math.toRadians(angle)
        } else {
            if (abs(x) >= 1e18) return Double.NaN
            x
        }
        return when (name) {
            "sin" -> kotlin.math.sin(radians)
            "cos" -> kotlin.math.cos(radians)
            else -> kotlin.math.tan(radians)
        }
    }

    private fun fromRadians(r: Double): Double = if (angleUnit == AngleUnit.DEGREES) Math.toDegrees(r) else r

    private fun power(base: Double, exponent: Double): Double {
        if (exponent == 0.0) return 1.0
        if (base == 0.0) return if (exponent < 0) Double.NaN else 0.0
        if (base == Math.E) return kotlin.math.exp(exponent)
        val v = base.pow(exponent)
        return if (v.isInfinite()) Double.NaN else v
    }

    private fun factorial(v: Double): Double {
        if (v < 0 || v != floor(v) || v > 170) return Double.NaN
        var result = 1.0
        for (i in 2..v.toInt()) result *= i
        return result
    }

    private fun root(index: Double, x: Double): Double {
        if (index == 0.0 || !index.isFinite()) return Double.NaN
        val integer = index == floor(index)
        val odd = integer && abs(index % 2.0) == 1.0
        if (x < 0 && !odd) return Double.NaN
        if (x == 0.0) return if (index < 0) Double.NaN else 0.0
        val v = abs(x).pow(1 / index)
        return if (x < 0) -v else v
    }

    private fun combinations(n: Double, k: Double): Double {
        if (n < 0 || k < 0 || n != floor(n) || k != floor(k)) return Double.NaN
        if (k > n) return 0.0
        val small = minOf(k, n - k)
        if (small > 10_000) return Double.NaN
        var result = 1.0
        for (i in 1..small.toInt()) result = result * (n - small + i) / i
        return result
    }

    private fun permutations(n: Double, k: Double): Double {
        if (n < 0 || k < 0 || n != floor(n) || k != floor(k)) return Double.NaN
        if (k > n) return 0.0
        if (k > 10_000) return Double.NaN
        var result = 1.0
        for (i in 1..k.toInt()) result *= n - k + i
        return result
    }
}
