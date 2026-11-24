package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Unità di misura degli angoli per le funzioni trigonometriche. */
enum class AngleUnit { DEGREES, RADIANS }

/** Errori che possono emergere dalla valutazione di un'espressione. */
enum class CalcError { DIVISION_BY_ZERO, UNDEFINED, OVERFLOW, SYNTAX }

sealed interface EvalResult {
    data class Success(val value: BigDecimal) : EvalResult
    data class Failure(val error: CalcError) : EvalResult
}

internal class CalcException(val error: CalcError) : RuntimeException(error.name)

/**
 * Valuta un'espressione nel formato interno della calcolatrice.
 *
 * Formato interno:
 * - numeri con il punto come separatore decimale, eventualmente in notazione `1.5E-7`
 * - operatori `+ - * / ^`, postfissi `!` (fattoriale) e `%` (diviso 100)
 * - costanti `π`, `e` e `@` (risultato precedente, "Ans")
 * - `√` (radice quadrata, prefisso) e funzioni `sin( cos( tan( asin( acos( atan( ln( log(`
 *
 * Le parentesi lasciate aperte vengono chiuse automaticamente.
 * La moltiplicazione implicita è supportata: `2π`, `3(4+1)`, `2sin(30)`.
 */
fun evaluate(
    expression: String,
    angleUnit: AngleUnit = AngleUnit.DEGREES,
    ans: BigDecimal? = null,
): EvalResult = try {
    EvalResult.Success(Parser(Tokenizer.tokenize(expression), angleUnit, ans).parse())
} catch (e: CalcException) {
    EvalResult.Failure(e.error)
} catch (e: ArithmeticException) {
    EvalResult.Failure(CalcError.OVERFLOW)
} catch (e: NumberFormatException) {
    EvalResult.Failure(CalcError.UNDEFINED)
}

internal sealed interface Token {
    data class Number(val value: BigDecimal) : Token
    data class Operator(val symbol: Char) : Token
    data class Postfix(val symbol: Char) : Token
    data class Function(val name: String) : Token
    data class Constant(val symbol: Char) : Token
    data object OpenParen : Token
    data object CloseParen : Token
    data object Sqrt : Token
}

internal object Tokenizer {
    /** Nomi delle funzioni, dal più lungo al più corto (così "asin" ha la precedenza su "sin"). */
    val FUNCTIONS = listOf("asin", "acos", "atan", "sin", "cos", "tan", "ln", "log")

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    if (i < input.length && input[i] == 'E') {
                        i++
                        if (i < input.length && input[i] == '-') i++
                        while (i < input.length && input[i].isDigit()) i++
                    }
                    val value = input.substring(start, i).toBigDecimalOrNull()
                        ?: throw CalcException(CalcError.SYNTAX)
                    tokens += Token.Number(value)
                    continue
                }
                c in "+-*/^" -> tokens += Token.Operator(c)
                c == '!' || c == '%' -> tokens += Token.Postfix(c)
                c == '(' -> tokens += Token.OpenParen
                c == ')' -> tokens += Token.CloseParen
                c == '√' -> tokens += Token.Sqrt
                c == 'π' || c == '@' -> tokens += Token.Constant(c)
                c.isLetter() -> {
                    val name = FUNCTIONS.firstOrNull { input.startsWith("$it(", i) }
                    if (name != null) {
                        tokens += Token.Function(name)
                        i += name.length + 1
                        continue
                    }
                    if (c == 'e') tokens += Token.Constant('e') else throw CalcException(CalcError.SYNTAX)
                }
                c.isWhitespace() -> Unit
                else -> throw CalcException(CalcError.SYNTAX)
            }
            i++
        }
        if (tokens.isEmpty()) throw CalcException(CalcError.SYNTAX)
        return tokens
    }
}

internal object MathSupport {
    /** Precisione interna: 34 cifre significative (come IEEE decimal128). */
    val MC = MathContext(34, RoundingMode.HALF_EVEN)

    /** Precisione dei risultati calcolati in virgola mobile (double). */
    val DOUBLE_MC = MathContext(15, RoundingMode.HALF_EVEN)

    /** Oltre 10^MAX_EXPONENT il risultato è considerato troppo grande. */
    const val MAX_EXPONENT = 9999

    val PI = BigDecimal("3.141592653589793238462643383279503")
    val E = BigDecimal("2.718281828459045235360287471352662")
    private val DEG_360 = BigDecimal(360)

    /** Esponente decimale di x (posizione della cifra più significativa). */
    fun exponentOf(x: BigDecimal): Int = x.precision() - x.scale() - 1

    /** log10(|x|) per qualunque BigDecimal diverso da zero, anche fuori dal range del double. */
    fun log10Abs(x: BigDecimal): Double {
        val exp = exponentOf(x)
        val mantissa = x.abs().movePointLeft(exp).toDouble()
        return log10(mantissa) + exp
    }

    fun fitsInDouble(x: BigDecimal): Boolean =
        x.signum() == 0 || abs(log10Abs(x)) < 300

    /** Controlla che il valore sia in un intervallo gestibile. */
    fun checked(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        val exp = exponentOf(x)
        if (exp > MAX_EXPONENT) throw CalcException(CalcError.OVERFLOW)
        if (exp < -MAX_EXPONENT) return BigDecimal.ZERO
        return x
    }

    fun fromDouble(d: Double): BigDecimal {
        if (d.isNaN()) throw CalcException(CalcError.UNDEFINED)
        if (d.isInfinite()) throw CalcException(CalcError.OVERFLOW)
        if (d == 0.0) return BigDecimal.ZERO
        return BigDecimal.valueOf(d).round(DOUBLE_MC)
    }

    fun isInteger(x: BigDecimal): Boolean =
        x.signum() == 0 || x.stripTrailingZeros().scale() <= 0

    /** 10^magnitude con segno, per risultati fuori dal range del double. */
    fun fromLog10(magnitude: Double, negative: Boolean): BigDecimal {
        val intPart = floor(magnitude)
        val mantissa = BigDecimal.valueOf(10.0.pow(magnitude - intPart)).round(DOUBLE_MC)
        val value = mantissa.scaleByPowerOfTen(intPart.toInt())
        return checked(if (negative) value.negate() else value)
    }

    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw CalcException(CalcError.UNDEFINED)
        if (x.signum() == 0) return BigDecimal.ZERO
        // Stima iniziale in double, poi raffinamento di Newton a 34 cifre.
        var y = if (fitsInDouble(x)) {
            BigDecimal.valueOf(sqrt(x.toDouble()))
        } else {
            fromLog10(log10Abs(x) / 2, negative = false)
        }
        val two = BigDecimal(2)
        repeat(4) { y = y.add(x.divide(y, MC), MC).divide(two, MC) }
        return y.round(MathContext(32, RoundingMode.HALF_EVEN)).stripTrailingZeros()
    }

    fun power(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        if (exponent.signum() == 0) return BigDecimal.ONE
        if (base.signum() == 0) {
            if (exponent.signum() < 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
            return BigDecimal.ZERO
        }
        val integerExponent = isInteger(exponent)
        if (!integerExponent && base.signum() < 0) throw CalcException(CalcError.UNDEFINED)

        val magnitude = log10Abs(base) * exponent.toDouble()
        if (magnitude.isNaN()) throw CalcException(CalcError.UNDEFINED)
        if (magnitude > MAX_EXPONENT) throw CalcException(CalcError.OVERFLOW)
        if (magnitude < -MAX_EXPONENT) return BigDecimal.ZERO

        if (integerExponent && exponent.abs() <= BigDecimal(100_000)) {
            return checked(base.pow(exponent.intValueExact(), MC))
        }
        val negative = base.signum() < 0 && exponent.toBigInteger().testBit(0)
        return if (abs(magnitude) < 300 && fitsInDouble(base)) {
            fromDouble(base.toDouble().pow(exponent.toDouble()))
        } else {
            fromLog10(magnitude, negative)
        }
    }

    fun factorial(x: BigDecimal): BigDecimal {
        if (x.signum() < 0 || !isInteger(x)) throw CalcException(CalcError.UNDEFINED)
        if (x > BigDecimal(3248)) throw CalcException(CalcError.OVERFLOW) // 3249! > 10^10000
        val n = x.intValueExact()
        var result = BigDecimal.ONE
        for (i in 2..n) result = result.multiply(BigDecimal(i), MC)
        return checked(result)
    }

    /** Riduce un angolo in gradi all'intervallo [0, 360). */
    private fun normalizeDegrees(x: BigDecimal): BigDecimal {
        val r = x.remainder(DEG_360)
        return if (r.signum() < 0) r.add(DEG_360) else r
    }

    fun trig(name: String, x: BigDecimal, unit: AngleUnit): BigDecimal {
        val radians: Double
        if (unit == AngleUnit.DEGREES) {
            val angle = normalizeDegrees(x)
            // Valori esatti per i multipli di 90°.
            if (isInteger(angle) && angle.toInt() % 90 == 0) {
                val quadrant = angle.toInt() / 90
                return when (name) {
                    "sin" -> intArrayOf(0, 1, 0, -1)[quadrant].toBigDecimal()
                    "cos" -> intArrayOf(1, 0, -1, 0)[quadrant].toBigDecimal()
                    else -> if (quadrant % 2 == 1) {
                        throw CalcException(CalcError.UNDEFINED)
                    } else {
                        BigDecimal.ZERO
                    }
                }
            }
            radians = Math.toRadians(angle.toDouble())
        } else {
            if (!fitsInDouble(x)) throw CalcException(CalcError.UNDEFINED)
            radians = x.toDouble()
        }
        val value = when (name) {
            "sin" -> sin(radians)
            "cos" -> cos(radians)
            else -> tan(radians)
        }
        // Elimina i residui numerici come sin(π) = 1.2E-16.
        if (abs(value) < 1e-15 && abs(radians) >= 1) return BigDecimal.ZERO
        return fromDouble(value)
    }

    fun inverseTrig(name: String, x: BigDecimal, unit: AngleUnit): BigDecimal {
        if (name != "atan" && x.abs() > BigDecimal.ONE) throw CalcException(CalcError.UNDEFINED)
        val d = if (fitsInDouble(x)) x.toDouble() else x.signum() * Double.MAX_VALUE
        val radians = when (name) {
            "asin" -> asin(d)
            "acos" -> acos(d)
            else -> atan(d)
        }
        return fromDouble(if (unit == AngleUnit.DEGREES) Math.toDegrees(radians) else radians)
    }

    fun logarithm(name: String, x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw CalcException(CalcError.UNDEFINED)
        val log10 = log10Abs(x)
        return when (name) {
            "log" -> fromDouble(log10)
            else -> fromDouble(if (fitsInDouble(x)) ln(x.toDouble()) else log10 * ln(10.0))
        }
    }
}

private class Parser(
    private val tokens: List<Token>,
    private val angleUnit: AngleUnit,
    private val ans: BigDecimal?,
) {
    private var pos = 0
    private val mc = MathSupport.MC

    fun parse(): BigDecimal {
        val value = parseExpression()
        if (pos != tokens.size) throw CalcException(CalcError.SYNTAX)
        return MathSupport.checked(value).let {
            if (it.signum() == 0) BigDecimal.ZERO else it.round(mc).stripTrailingZeros()
        }
    }

    private fun peek(): Token? = tokens.getOrNull(pos)
    private fun next(): Token? = tokens.getOrNull(pos++)

    // espressione := termine (('+' | '-') termine)*
    private fun parseExpression(): BigDecimal {
        var left = parseTerm()
        while (true) {
            val t = peek()
            if (t is Token.Operator && (t.symbol == '+' || t.symbol == '-')) {
                pos++
                val right = parseTerm()
                left = MathSupport.checked(
                    if (t.symbol == '+') left.add(right, mc) else left.subtract(right, mc),
                )
            } else {
                return left
            }
        }
    }

    // termine := unario (('*' | '/') unario | unario)*   (moltiplicazione implicita)
    private fun parseTerm(): BigDecimal {
        var left = parseUnary()
        while (true) {
            val t = peek()
            left = when {
                t is Token.Operator && t.symbol == '*' -> {
                    pos++
                    MathSupport.checked(left.multiply(parseUnary(), mc))
                }
                t is Token.Operator && t.symbol == '/' -> {
                    pos++
                    val divisor = parseUnary()
                    if (divisor.signum() == 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
                    MathSupport.checked(left.divide(divisor, mc))
                }
                t != null && startsOperand(t) -> MathSupport.checked(left.multiply(parseUnary(), mc))
                else -> return left
            }
        }
    }

    private fun startsOperand(t: Token) = t is Token.Number || t is Token.Constant ||
        t is Token.Function || t == Token.OpenParen || t == Token.Sqrt

    // unario := ('-' | '+') unario | potenza
    private fun parseUnary(): BigDecimal {
        val t = peek()
        if (t is Token.Operator && t.symbol == '-') {
            pos++
            return parseUnary().negate()
        }
        if (t is Token.Operator && t.symbol == '+') {
            pos++
            return parseUnary()
        }
        return parsePower()
    }

    // potenza := postfisso ('^' unario)?   (associativa a destra: 2^3^2 = 2^9)
    private fun parsePower(): BigDecimal {
        val base = parsePostfix()
        val t = peek()
        if (t is Token.Operator && t.symbol == '^') {
            pos++
            return MathSupport.power(base, parseUnary())
        }
        return base
    }

    // postfisso := primario ('!' | '%')*
    private fun parsePostfix(): BigDecimal {
        var value = parsePrimary()
        while (true) {
            val t = peek() as? Token.Postfix ?: return value
            pos++
            value = if (t.symbol == '!') {
                MathSupport.factorial(value)
            } else {
                value.divide(BigDecimal(100), mc)
            }
        }
    }

    private fun parsePrimary(): BigDecimal = when (val t = next()) {
        is Token.Number -> t.value
        is Token.Constant -> when (t.symbol) {
            'π' -> MathSupport.PI
            'e' -> MathSupport.E
            else -> ans ?: throw CalcException(CalcError.SYNTAX)
        }
        Token.OpenParen -> parseExpression().also { expectCloseParen() }
        is Token.Function -> {
            val argument = parseExpression()
            expectCloseParen()
            applyFunction(t.name, argument)
        }
        Token.Sqrt -> MathSupport.sqrt(parseUnary())
        else -> throw CalcException(CalcError.SYNTAX)
    }

    /** Accetta ')' oppure la fine dell'espressione (parentesi chiusa automaticamente). */
    private fun expectCloseParen() {
        when (peek()) {
            Token.CloseParen -> pos++
            null -> Unit
            else -> throw CalcException(CalcError.SYNTAX)
        }
    }

    private fun applyFunction(name: String, x: BigDecimal): BigDecimal = when (name) {
        "sin", "cos", "tan" -> MathSupport.trig(name, x, angleUnit)
        "asin", "acos", "atan" -> MathSupport.inverseTrig(name, x, angleUnit)
        "ln", "log" -> MathSupport.logarithm(name, x)
        else -> throw CalcException(CalcError.SYNTAX)
    }
}
