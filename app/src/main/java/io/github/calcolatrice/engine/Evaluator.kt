package io.github.calcolatrice.engine

import java.math.BigDecimal

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
    EvalResult.Success(Evaluation(angleUnit, ans, variables).result(ExpressionParser.parse(expression)))
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

/**
 * Albero di un'espressione: si costruisce una volta ([ExpressionParser]) e si valuta tante volte,
 * a 34 cifre ([Evaluation]) o in double per i grafici ([FastEval]). Le regole di precedenza,
 * delle percentuali e della moltiplicazione implicita stanno solo nel parser.
 */
internal sealed interface Expr {
    data class Num(val value: BigDecimal) : Expr

    /** `π`, `e` oppure `@` (risultato precedente). */
    data class Const(val symbol: Char) : Expr
    data class Var(val name: Char) : Expr
    data class Neg(val operand: Expr) : Expr

    /** a ± b; con [percentOfLeft] b è una percentuale di a (`200 + 10%` = 200 + 200·10/100). */
    data class Sum(val left: Expr, val right: Expr, val subtract: Boolean, val percentOfLeft: Boolean) : Expr
    data class Product(val left: Expr, val right: Expr) : Expr
    data class Quotient(val left: Expr, val right: Expr) : Expr
    data class Power(val base: Expr, val exponent: Expr) : Expr
    data class Factorial(val operand: Expr) : Expr
    data class Percent(val operand: Expr) : Expr
    data class Sqrt(val operand: Expr) : Expr
    data class Call(val name: String, val args: List<Expr>) : Expr
}

/** True se l'espressione contiene la variabile [name] (SOLVE ha bisogno della X). */
internal fun Expr.uses(name: Char): Boolean = when (this) {
    is Expr.Var -> this.name == name
    is Expr.Num, is Expr.Const -> false
    is Expr.Neg -> operand.uses(name)
    is Expr.Sum -> left.uses(name) || right.uses(name)
    is Expr.Product -> left.uses(name) || right.uses(name)
    is Expr.Quotient -> left.uses(name) || right.uses(name)
    is Expr.Power -> base.uses(name) || exponent.uses(name)
    is Expr.Factorial -> operand.uses(name)
    is Expr.Percent -> operand.uses(name)
    is Expr.Sqrt -> operand.uses(name)
    is Expr.Call -> args.any { it.uses(name) }
}

/**
 * Analisi sintattica (discesa ricorsiva):
 *
 *     espressione := termine (('+' | '-') termine)*
 *     termine     := unario (('*' | '/') unario | unario)*      moltiplicazione implicita
 *     unario      := ('-' | '+') unario | potenza
 *     potenza     := postfisso ('^' unario)?                    associativa a destra: 2^3^2 = 2^9
 *     postfisso   := primario ('!' | '%')*
 *     primario    := numero | costante | variabile | '(' espressione ')' | funzione | '√' operando
 *
 * Le parentesi lasciate aperte vengono chiuse alla fine.
 */
internal class ExpressionParser private constructor(private val tokens: List<Token>) {
    private var pos = 0

    /** True se l'ultimo postfisso letto è `%`. */
    private var postfixWasPercent = false

    /** True se l'ultimo termine letto è una percentuale semplice (es. `10%`). */
    private var termIsPercent = false

    companion object {
        fun parse(text: String): Expr = parse(Tokenizer.tokenize(text))

        fun parse(tokens: List<Token>): Expr {
            val parser = ExpressionParser(tokens)
            val root = parser.expression()
            if (parser.pos != tokens.size) throw CalcException(CalcError.SYNTAX)
            return root
        }
    }

    private fun peek(): Token? = tokens.getOrNull(pos)
    private fun next(): Token? = tokens.getOrNull(pos++)
    private fun isOperator(t: Token?, symbol: Char) = t is Token.Operator && t.symbol == symbol

    private fun expression(): Expr {
        var left = term()
        while (true) {
            val t = peek()
            if (!isOperator(t, '+') && !isOperator(t, '-')) return left
            pos++
            val right = term()
            left = Expr.Sum(left, right, subtract = isOperator(t, '-'), percentOfLeft = termIsPercent)
        }
    }

    private fun term(): Expr {
        var left = unary()
        var percent = postfixWasPercent
        while (true) {
            val t = peek()
            left = when {
                isOperator(t, '*') -> {
                    pos++
                    Expr.Product(left, unary())
                }
                isOperator(t, '/') -> {
                    pos++
                    Expr.Quotient(left, unary())
                }
                t != null && startsOperand(t) -> Expr.Product(left, unary())
                else -> {
                    termIsPercent = percent
                    return left
                }
            }
            percent = false
        }
    }

    private fun startsOperand(t: Token) = t is Token.Number || t is Token.Constant || t is Token.Variable ||
        t is Token.Function || t == Token.OpenParen || t == Token.Sqrt

    private fun unary(): Expr {
        val t = peek()
        if (isOperator(t, '-')) {
            pos++
            return Expr.Neg(unary())
        }
        if (isOperator(t, '+')) {
            pos++
            return unary()
        }
        return power()
    }

    private fun power(): Expr {
        val base = postfix()
        if (!isOperator(peek(), '^')) return base
        pos++
        val exponent = unary()
        postfixWasPercent = false
        return Expr.Power(base, exponent)
    }

    private fun postfix(): Expr {
        var value = primary()
        var percent = false
        while (true) {
            val t = peek() as? Token.Postfix ?: break
            pos++
            percent = t.symbol == '%'
            value = if (percent) Expr.Percent(value) else Expr.Factorial(value)
        }
        postfixWasPercent = percent
        return value
    }

    private fun primary(): Expr = when (val t = next()) {
        is Token.Number -> Expr.Num(t.value)
        is Token.Constant -> Expr.Const(t.symbol)
        is Token.Variable -> Expr.Var(t.name)
        Token.OpenParen -> expression().also { expectCloseParen() }
        is Token.Function -> {
            val args = mutableListOf(expression())
            while (peek() == Token.Separator) {
                pos++
                args += expression()
            }
            expectCloseParen()
            val arity = Tokenizer.ARITY.getValue(t.name)
            if (args.size !in arity || (t.name == "normc" && args.size == 3)) throw CalcException(CalcError.SYNTAX)
            Expr.Call(t.name, args)
        }
        Token.Sqrt -> Expr.Sqrt(sqrtOperand())
        else -> throw CalcException(CalcError.SYNTAX)
    }

    /** Operando di √: solo il numero, la costante o il blocco tra parentesi che lo segue. */
    private fun sqrtOperand(): Expr {
        val t = peek()
        if (isOperator(t, '-')) {
            pos++
            return Expr.Neg(sqrtOperand())
        }
        if (isOperator(t, '+')) {
            pos++
            return sqrtOperand()
        }
        return primary()
    }

    /** Accetta ')' oppure la fine dell'espressione (parentesi chiusa automaticamente). */
    private fun expectCloseParen() {
        when (peek()) {
            Token.CloseParen -> pos++
            null -> Unit
            else -> throw CalcException(CalcError.SYNTAX)
        }
    }
}

/**
 * Valutazione a 34 cifre di un [Expr]. Gli errori (divisione per zero, dominio, numeri troppo grandi)
 * escono come [CalcException].
 */
internal class Evaluation(
    private val angleUnit: AngleUnit,
    private val ans: BigDecimal?,
    private val variables: Map<Char, BigDecimal> = emptyMap(),
) {
    private val mc = MathSupport.MC

    /**
     * Il più grande valore assoluto sommato o sottratto durante il calcolo. Dà la scala del rumore
     * di arrotondamento: un risultato più piccolo di circa 10⁻³² volte questo valore non si distingue da zero.
     */
    var magnitude: BigDecimal = BigDecimal.ZERO
        private set

    /** Il risultato finale: controllato e arrotondato a 34 cifre. */
    fun result(root: Expr): BigDecimal = MathSupport.checked(value(root)).let {
        if (it.signum() == 0) BigDecimal.ZERO else it.round(mc).stripTrailingZeros()
    }

    fun value(e: Expr): BigDecimal = when (e) {
        is Expr.Num -> e.value
        is Expr.Const -> when (e.symbol) {
            'π' -> MathSupport.PI
            'e' -> MathSupport.E
            else -> ans ?: throw CalcException(CalcError.SYNTAX)
        }
        is Expr.Var -> variables[e.name] ?: BigDecimal.ZERO
        is Expr.Neg -> value(e.operand).negate()
        is Expr.Sum -> {
            val left = value(e.left)
            var right = value(e.right)
            if (e.percentOfLeft) right = left.multiply(right, mc)
            magnitude = magnitude.max(left.abs()).max(right.abs())
            MathSupport.checked(if (e.subtract) left.subtract(right, mc) else left.add(right, mc))
        }
        is Expr.Product -> {
            val left = value(e.left)
            MathSupport.checked(left.multiply(value(e.right), mc))
        }
        is Expr.Quotient -> {
            val left = value(e.left)
            val divisor = value(e.right)
            if (divisor.signum() == 0) throw CalcException(CalcError.DIVISION_BY_ZERO)
            MathSupport.checked(left.divide(divisor, mc))
        }
        is Expr.Power -> {
            val base = value(e.base)
            MathSupport.power(base, value(e.exponent))
        }
        is Expr.Factorial -> MathSupport.factorial(value(e.operand))
        is Expr.Percent -> value(e.operand).divide(BigDecimal(100), mc)
        is Expr.Sqrt -> MathSupport.sqrt(value(e.operand))
        is Expr.Call -> call(e.name, e.args.map(::value))
    }

    private fun call(name: String, args: List<BigDecimal>): BigDecimal {
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
