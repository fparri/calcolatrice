package io.github.calcolatrice.engine

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Valutazione veloce in `double` di un'espressione nella variabile X, per disegnare i grafici:
 * il motore a 34 cifre richiede decine di microsecondi per punto, troppo per ridisegnare
 * una curva a ogni movimento del dito. Segue esattamente la grammatica di [Parser]
 * (precedenze, percentuali, moltiplicazione implicita); fuori dal dominio restituisce NaN.
 * I valori che si leggono come numeri (tabella, zeri, derivate) usano invece il motore a 34 cifre.
 */
object FastEval {

    /** Funzione compilata: x → f(x), NaN dove non è definita. Null se l'espressione non è valida. */
    fun compile(
        expression: String,
        angleUnit: AngleUnit,
        variables: Map<Char, BigDecimal> = emptyMap(),
        ans: BigDecimal? = null,
        unknown: Char = 'X',
    ): ((Double) -> Double)? = try {
        val tokens = Tokenizer.tokenize(expression)
        val values = variables.mapValues { it.value.toDouble() }
        Compiler(tokens, angleUnit, ans?.toDouble(), values, unknown).compile()
    } catch (e: CalcException) {
        null
    }

    private class Compiler(
        private val tokens: List<Token>,
        private val angleUnit: AngleUnit,
        private val ans: Double?,
        private val variables: Map<Char, Double>,
        private val unknown: Char,
    ) {
        private var pos = 0
        private var postfixWasPercent = false
        private var termIsPercent = false

        fun compile(): (Double) -> Double {
            val f = expression()
            if (pos != tokens.size) throw CalcException(CalcError.SYNTAX)
            return f
        }

        private fun peek(): Token? = tokens.getOrNull(pos)
        private fun next(): Token? = tokens.getOrNull(pos++)

        private fun expression(): (Double) -> Double {
            var left = term()
            while (true) {
                val t = peek()
                if (t is Token.Operator && (t.symbol == '+' || t.symbol == '-')) {
                    pos++
                    val l = left
                    val raw = term()
                    val r: (Double) -> Double = if (termIsPercent) { x -> l(x) * raw(x) } else raw
                    left = if (t.symbol == '+') { x -> l(x) + r(x) } else { x -> l(x) - r(x) }
                } else {
                    return left
                }
            }
        }

        private fun term(): (Double) -> Double {
            var left = unary()
            var percent = postfixWasPercent
            while (true) {
                val t = peek()
                val l = left
                left = when {
                    t is Token.Operator && t.symbol == '*' -> {
                        pos++
                        percent = false
                        val r = unary();
                        { x -> l(x) * r(x) }
                    }
                    t is Token.Operator && t.symbol == '/' -> {
                        pos++
                        percent = false
                        val r = unary();
                        { x ->
                            val d = r(x)
                            if (d == 0.0) Double.NaN else l(x) / d
                        }
                    }
                    t != null && startsOperand(t) -> {
                        percent = false
                        val r = unary();
                        { x -> l(x) * r(x) }
                    }
                    else -> {
                        termIsPercent = percent
                        return left
                    }
                }
            }
        }

        private fun startsOperand(t: Token) = t is Token.Number || t is Token.Constant || t is Token.Variable ||
            t is Token.Function || t == Token.OpenParen || t == Token.Sqrt

        private fun unary(): (Double) -> Double {
            val t = peek()
            if (t is Token.Operator && t.symbol == '-') {
                pos++
                val f = unary()
                return { x -> -f(x) }
            }
            if (t is Token.Operator && t.symbol == '+') {
                pos++
                return unary()
            }
            return power()
        }

        private fun power(): (Double) -> Double {
            val base = postfix()
            val t = peek()
            if (t is Token.Operator && t.symbol == '^') {
                pos++
                val exponent = unary()
                postfixWasPercent = false
                return { x -> power(base(x), exponent(x)) }
            }
            return base
        }

        private fun postfix(): (Double) -> Double {
            var value = primary()
            var percent = false
            while (true) {
                val t = peek() as? Token.Postfix ?: break
                pos++
                percent = t.symbol == '%'
                val v = value
                value = if (t.symbol == '!') { x -> factorial(v(x)) } else { x -> v(x) / 100 }
            }
            postfixWasPercent = percent
            return value
        }

        private fun primary(): (Double) -> Double = when (val t = next()) {
            is Token.Number -> constant(t.value.toDouble())
            is Token.Constant -> when (t.symbol) {
                'π' -> constant(Math.PI)
                'e' -> constant(Math.E)
                else -> constant(ans ?: throw CalcException(CalcError.SYNTAX))
            }
            is Token.Variable -> if (t.name == unknown) { x -> x } else constant(variables[t.name] ?: 0.0)
            Token.OpenParen -> expression().also { expectCloseParen() }
            is Token.Function -> {
                val arguments = mutableListOf(expression())
                while (peek() == Token.Separator) {
                    pos++
                    arguments += expression()
                }
                expectCloseParen()
                val arity = Tokenizer.ARITY.getValue(t.name)
                if (arguments.size !in arity || (t.name == "normc" && arguments.size == 3)) {
                    throw CalcException(CalcError.SYNTAX)
                }
                function(t.name, arguments)
            }
            Token.Sqrt -> {
                val operand = sqrtOperand();
                { x -> val v = operand(x); if (v < 0) Double.NaN else kotlin.math.sqrt(v) }
            }
            else -> throw CalcException(CalcError.SYNTAX)
        }

        private fun sqrtOperand(): (Double) -> Double {
            val t = peek()
            if (t is Token.Operator && t.symbol == '-') {
                pos++
                val f = sqrtOperand()
                return { x -> -f(x) }
            }
            if (t is Token.Operator && t.symbol == '+') {
                pos++
                return sqrtOperand()
            }
            return primary()
        }

        private fun expectCloseParen() {
            when (peek()) {
                Token.CloseParen -> pos++
                null -> Unit
                else -> throw CalcException(CalcError.SYNTAX)
            }
        }

        private fun constant(v: Double): (Double) -> Double = { v }

        private fun function(name: String, args: List<(Double) -> Double>): (Double) -> Double {
            val a = args[0]
            return when (name) {
                "sin", "cos", "tan" -> { x -> trig(name, a(x)) }
                "asin" -> { x -> fromRadians(kotlin.math.asin(a(x))) }
                "acos" -> { x -> fromRadians(kotlin.math.acos(a(x))) }
                "atan" -> { x -> fromRadians(kotlin.math.atan(a(x))) }
                "ln" -> { x -> positive(a(x))?.let { kotlin.math.ln(it) } ?: Double.NaN }
                "log" -> if (args.size == 1) {
                    { x -> positive(a(x))?.let { kotlin.math.log10(it) } ?: Double.NaN }
                } else {
                    val b = args[1];
                    { x ->
                        val base = a(x)
                        val v = b(x)
                        if (base <= 0 || base == 1.0 || v <= 0) Double.NaN else kotlin.math.ln(v) / kotlin.math.ln(base)
                    }
                }
                "abs" -> { x -> abs(a(x)) }
                "root" -> { val b = args[1]; { x -> root(a(x), b(x)) } }
                "ncr" -> { val b = args[1]; { x -> combinations(a(x), b(x)) } }
                "npr" -> { val b = args[1]; { x -> permutations(a(x), b(x)) } }
                // Le altre funzioni sono rare in un grafico: si calcolano con il motore preciso.
                else -> { x ->
                    try {
                        val values = args.map { BigDecimal(it(x)) }
                        when (name) {
                            "gcd" -> MoreMath.gcd(values)
                            "lcm" -> MoreMath.lcm(values)
                            "binp" -> MoreMath.binomialPdf(values[0], values[1], values[2])
                            "binc" -> MoreMath.binomialCdf(values[0], values[1], values[2])
                            "normc" -> if (values.size == 2) {
                                MoreMath.normalCdf(values[0], values[1], BigDecimal.ZERO, BigDecimal.ONE)
                            } else {
                                MoreMath.normalCdf(values[0], values[1], values[2], values[3])
                            }
                            else -> throw CalcException(CalcError.SYNTAX)
                        }.toDouble()
                    } catch (e: CalcException) {
                        Double.NaN
                    } catch (e: ArithmeticException) {
                        Double.NaN
                    } catch (e: NumberFormatException) {
                        Double.NaN
                    }
                }
            }
        }

        private fun positive(v: Double): Double? = if (v > 0) v else null

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
}
