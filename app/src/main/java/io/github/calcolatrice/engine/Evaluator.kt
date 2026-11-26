package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.log10

/** Unità di misura degli angoli per le funzioni trigonometriche. */
enum class AngleUnit { DEGREES, RADIANS }

/** Errori che possono emergere dalla valutazione di un'espressione. */
enum class CalcError { DIVISION_BY_ZERO, UNDEFINED, OVERFLOW, SYNTAX, NO_SOLUTION }

/**
 * Variabili utilizzabili nelle espressioni, come sulle calcolatrici scientifiche.
 * Manca la E, che nel formato interno indica l'esponente (1.5E-7).
 */
const val VARIABLES = "ABCDXYM"

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
 * - costanti `π`, `e` e `@` (risultato precedente, "Ans"); variabili `A B C D X Y M` (valore 0 se non definite)
 * - `√` (radice quadrata, prefisso) e funzioni `sin( cos( tan( asin( acos( atan( ln( log(`
 * - funzioni con più argomenti separati da `;`: `log(b;x)` (logaritmo in base b), `root(n;x)`, `ncr(n;k)`,
 *   `npr(n;k)`, `gcd(a;b;…)`, `lcm(a;b;…)`, `binp(n;p;k)`, `binc(n;p;k)`, `normc(a;b)` e `normc(a;b;μ;σ)`;
 *   inoltre `abs(`
 *
 * `√` si applica solo all'operando che lo segue: `√9! = (√9)!`, `√4^2 = (√4)^2`.
 * Dopo `+` o `−` la percentuale si riferisce al valore precedente, come sulle calcolatrici
 * tascabili: `200+10% = 220`, `200−10% = 180`; altrove `%` divide per 100 (`50×10% = 5`).
 *
 * Le parentesi lasciate aperte vengono chiuse automaticamente.
 * La moltiplicazione implicita è supportata: `2π`, `3(4+1)`, `2sin(30)`.
 */
fun evaluate(
    expression: String,
    angleUnit: AngleUnit = AngleUnit.DEGREES,
    ans: BigDecimal? = null,
    variables: Map<Char, BigDecimal> = emptyMap(),
): EvalResult = try {
    EvalResult.Success(Parser(Tokenizer.tokenize(expression), angleUnit, ans, variables).parse())
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
    data class Variable(val name: Char) : Token
    data object OpenParen : Token
    data object CloseParen : Token
    data object Separator : Token
    data object Sqrt : Token
}

internal object Tokenizer {
    /** Nomi delle funzioni, dal più lungo al più corto (così "asin" ha la precedenza su "sin"). */
    val FUNCTIONS = listOf(
        "normc", "asin", "acos", "atan", "root", "binp", "binc",
        "sin", "cos", "tan", "abs", "ncr", "npr", "gcd", "lcm", "log", "ln",
    )

    /** Numero di argomenti ammessi da ogni funzione. */
    val ARITY: Map<String, IntRange> = FUNCTIONS.associateWith { name ->
        when (name) {
            "log" -> 1..2
            "root", "ncr", "npr" -> 2..2
            "gcd", "lcm" -> 2..Int.MAX_VALUE
            "binp", "binc" -> 3..3
            "normc" -> 2..4
            else -> 1..1
        }
    }

    /** Funzioni che accettano più di un argomento: dopo di esse il tasto `;` è attivo. */
    val MULTI_ARGUMENT: Set<String> = ARITY.filterValues { it.last > 1 }.keys

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
                c == ';' -> tokens += Token.Separator
                c == '√' -> tokens += Token.Sqrt
                c == 'π' || c == '@' -> tokens += Token.Constant(c)
                c in VARIABLES -> tokens += Token.Variable(c)
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
    /** Precisione dei risultati: 34 cifre significative (come IEEE decimal128). */
    val MC = MathContext(34, RoundingMode.HALF_EVEN)

    /** Oltre 10^MAX_EXPONENT il risultato è considerato troppo grande. */
    const val MAX_EXPONENT = 9999

    val PI = BigDecimal("3.141592653589793238462643383279503")
    val E = BigDecimal("2.718281828459045235360287471352662")
    private val DEG_360 = BigDecimal(360)

    /** Oltre questo valore (in radianti) la riduzione dell'angolo perderebbe tutte le cifre. */
    private val MAX_RADIANS = BigDecimal("1E18")

    /** Residui come sin(π) ≈ 3·10⁻³⁴: π stesso è arrotondato a 34 cifre. */
    private val TRIG_RESIDUE = BigDecimal("1E-32")
    private val TAN_POLE = BigDecimal("1E-30")

    /** Esponente decimale di x (posizione della cifra più significativa). */
    fun exponentOf(x: BigDecimal): Int = x.precision() - x.scale() - 1

    /** log10(|x|) approssimato, per qualunque BigDecimal diverso da zero (serve solo per i controlli). */
    fun log10Abs(x: BigDecimal): Double {
        val exp = exponentOf(x)
        val mantissa = x.abs().movePointLeft(exp).toDouble()
        return log10(mantissa) + exp
    }

    /** Controlla che il valore sia in un intervallo gestibile. */
    fun checked(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        val exp = exponentOf(x)
        if (exp > MAX_EXPONENT) throw CalcException(CalcError.OVERFLOW)
        if (exp < -MAX_EXPONENT) return BigDecimal.ZERO
        return x
    }

    fun isInteger(x: BigDecimal): Boolean =
        x.signum() == 0 || x.stripTrailingZeros().scale() <= 0

    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw CalcException(CalcError.UNDEFINED)
        return BigMath.sqrt(x).round(MC).stripTrailingZeros()
    }

    fun power(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        if (exponent.signum() == 0) return BigDecimal.ONE
        if (base.signum() == 0) {
            if (exponent.signum() < 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
            return BigDecimal.ZERO
        }
        val integerExponent = isInteger(exponent)
        if (!integerExponent && base.signum() < 0) throw CalcException(CalcError.UNDEFINED)

        // ±1 elevato a qualunque esponente: il controllo con i double sotto darebbe 0·∞ = NaN per esponenti enormi.
        if (base.abs().compareTo(BigDecimal.ONE) == 0) {
            val negative = base.signum() < 0 && exponent.toBigInteger().testBit(0)
            return if (negative) BigDecimal.ONE.negate() else BigDecimal.ONE
        }

        // Controllo dell'ordine di grandezza prima di calcolare.
        val magnitude = log10Abs(base) * exponent.toDouble()
        if (magnitude.isNaN()) throw CalcException(CalcError.UNDEFINED)
        if (magnitude > MAX_EXPONENT + 1) throw CalcException(CalcError.OVERFLOW)
        if (magnitude < -MAX_EXPONENT - 1) return BigDecimal.ZERO

        // e^x con la vera esponenziale: la costante e a 34 cifre, elevata a potenza, perderebbe precisione.
        if (base.compareTo(E) == 0) return checked(BigMath.exp(exponent).round(MC))
        if (integerExponent && exponent.abs() <= BigDecimal(100_000)) {
            return checked(base.pow(exponent.intValueExact(), MC))
        }
        // x^y = e^(y·ln|x|), col segno giusto per gli esponenti interi dispari.
        val negative = base.signum() < 0 && exponent.toBigInteger().testBit(0)
        val value = BigMath.exp(exponent.multiply(BigMath.ln(base.abs()), BigMath.WORK))
        return checked((if (negative) value.negate() else value).round(MC))
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
        val radians: BigDecimal
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
            radians = BigMath.toRadians(angle)
        } else {
            if (x.abs() >= MAX_RADIANS) throw CalcException(CalcError.UNDEFINED)
            radians = x
        }
        val (sin, cos) = BigMath.sinCos(radians)
        val value = when (name) {
            "sin" -> sin
            "cos" -> cos
            else -> {
                // Multipli dispari di π/2: il coseno è zero a meno dei residui di π.
                if (cos.abs() < TAN_POLE) throw CalcException(CalcError.UNDEFINED)
                sin.divide(cos, BigMath.WORK)
            }
        }
        if (radians.abs() >= BigDecimal.ONE && value.abs() < radians.abs().multiply(TRIG_RESIDUE)) {
            return BigDecimal.ZERO
        }
        return value.round(MC)
    }

    fun inverseTrig(name: String, x: BigDecimal, unit: AngleUnit): BigDecimal {
        if (name != "atan" && x.abs() > BigDecimal.ONE) throw CalcException(CalcError.UNDEFINED)
        val radians = when (name) {
            "asin" -> BigMath.asin(x)
            "acos" -> BigMath.acos(x)
            else -> BigMath.atan(x)
        }
        val value = if (unit == AngleUnit.DEGREES) BigMath.toDegrees(radians) else radians
        return value.round(MC)
    }

    fun logarithm(name: String, x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw CalcException(CalcError.UNDEFINED)
        val value = if (name == "log") BigMath.log10(x) else BigMath.ln(x)
        return value.round(MC)
    }
}

internal class Parser(
    private val tokens: List<Token>,
    private val angleUnit: AngleUnit,
    private val ans: BigDecimal?,
    private val variables: Map<Char, BigDecimal> = emptyMap(),
) {
    private var pos = 0
    private val mc = MathSupport.MC

    /** True se l'ultimo postfisso applicato da [parsePostfix] è `%`. */
    private var postfixWasPercent = false

    /** True se l'ultimo termine letto da [parseTerm] è una percentuale semplice (es. `10%`). */
    private var termIsPercent = false

    /**
     * Il più grande valore assoluto sommato o sottratto durante il calcolo. Dà la scala del rumore
     * di arrotondamento: un risultato più piccolo di circa 10⁻³² volte questo valore non si distingue da zero.
     */
    var magnitude: BigDecimal = BigDecimal.ZERO
        private set

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
                var right = parseTerm()
                // a ± b% = a ± a·b/100 (sconti e maggiorazioni).
                if (termIsPercent) right = left.multiply(right, mc)
                magnitude = magnitude.max(left.abs()).max(right.abs())
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
        var percent = postfixWasPercent
        while (true) {
            val t = peek()
            left = when {
                t is Token.Operator && t.symbol == '*' -> {
                    pos++
                    percent = false
                    MathSupport.checked(left.multiply(parseUnary(), mc))
                }
                t is Token.Operator && t.symbol == '/' -> {
                    pos++
                    percent = false
                    val divisor = parseUnary()
                    if (divisor.signum() == 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
                    MathSupport.checked(left.divide(divisor, mc))
                }
                t != null && startsOperand(t) -> {
                    percent = false
                    MathSupport.checked(left.multiply(parseUnary(), mc))
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
            val result = MathSupport.power(base, parseUnary())
            postfixWasPercent = false
            return result
        }
        return base
    }

    // postfisso := primario ('!' | '%')*      primario può essere '√' primario
    private fun parsePostfix(): BigDecimal {
        var value = parsePrimary()
        var percent = false
        while (true) {
            val t = peek() as? Token.Postfix ?: break
            pos++
            percent = t.symbol == '%'
            value = if (t.symbol == '!') {
                MathSupport.factorial(value)
            } else {
                value.divide(BigDecimal(100), mc)
            }
        }
        postfixWasPercent = percent
        return value
    }

    private fun parsePrimary(): BigDecimal = when (val t = next()) {
        is Token.Number -> t.value
        is Token.Constant -> when (t.symbol) {
            'π' -> MathSupport.PI
            'e' -> MathSupport.E
            else -> ans ?: throw CalcException(CalcError.SYNTAX)
        }
        is Token.Variable -> variables[t.name] ?: BigDecimal.ZERO
        Token.OpenParen -> parseExpression().also { expectCloseParen() }
        is Token.Function -> {
            val arguments = mutableListOf(parseExpression())
            while (peek() == Token.Separator) {
                pos++
                arguments += parseExpression()
            }
            expectCloseParen()
            val arity = Tokenizer.ARITY.getValue(t.name)
            if (arguments.size !in arity || (t.name == "normc" && arguments.size == 3)) {
                throw CalcException(CalcError.SYNTAX)
            }
            applyFunction(t.name, arguments)
        }
        Token.Sqrt -> MathSupport.sqrt(parseSqrtOperand())
        else -> throw CalcException(CalcError.SYNTAX)
    }

    /** Operando di √: solo il numero, la costante o il blocco tra parentesi che lo segue. */
    private fun parseSqrtOperand(): BigDecimal {
        val t = peek()
        if (t is Token.Operator && t.symbol == '-') {
            pos++
            return parseSqrtOperand().negate()
        }
        if (t is Token.Operator && t.symbol == '+') {
            pos++
            return parseSqrtOperand()
        }
        return parsePrimary()
    }

    /** Accetta ')' oppure la fine dell'espressione (parentesi chiusa automaticamente). */
    private fun expectCloseParen() {
        when (peek()) {
            Token.CloseParen -> pos++
            null -> Unit
            else -> throw CalcException(CalcError.SYNTAX)
        }
    }

    private fun applyFunction(name: String, args: List<BigDecimal>): BigDecimal {
        val x = args[0]
        return when (name) {
            "sin", "cos", "tan" -> MathSupport.trig(name, x, angleUnit)
            "asin", "acos", "atan" -> MathSupport.inverseTrig(name, x, angleUnit)
            "ln" -> MathSupport.logarithm(name, x)
            "log" -> if (args.size == 1) MathSupport.logarithm(name, x) else MoreMath.logBase(x, args[1])
            "abs" -> x.abs()
            "root" -> MoreMath.root(x, args[1])
            "ncr" -> MoreMath.combinations(x, args[1])
            "npr" -> MoreMath.permutations(x, args[1])
            "gcd" -> MoreMath.gcd(args)
            "lcm" -> MoreMath.lcm(args)
            "binp" -> MoreMath.binomialPdf(x, args[1], args[2])
            "binc" -> MoreMath.binomialCdf(x, args[1], args[2])
            "normc" -> if (args.size == 2) {
                MoreMath.normalCdf(x, args[1], BigDecimal.ZERO, BigDecimal.ONE)
            } else {
                MoreMath.normalCdf(x, args[1], args[2], args[3])
            }
            else -> throw CalcException(CalcError.SYNTAX)
        }
    }
}
