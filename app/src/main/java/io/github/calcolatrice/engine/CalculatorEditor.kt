package io.github.calcolatrice.engine

import java.math.BigDecimal

/** Tasti che modificano l'espressione. */
sealed interface CalcKey {
    data class Digit(val digit: Char) : CalcKey
    data object Decimal : CalcKey
    /** `+ - * / ^` */
    data class Operator(val symbol: Char) : CalcKey
    data object Percent : CalcKey
    data object Factorial : CalcKey
    data object Square : CalcKey
    data object Reciprocal : CalcKey
    data object SquareRoot : CalcKey
    /** `sin cos tan asin acos atan ln log` */
    data class Function(val name: String) : CalcKey
    /** `π` oppure `e` */
    data class Constant(val symbol: Char) : CalcKey
    /** Una delle [VARIABLES]. */
    data class Variable(val name: Char) : CalcKey
    data object ExpE : CalcKey
    data object Exp10 : CalcKey
    /** Parentesi "intelligente": apre o chiude in base al contesto. */
    data object Parentheses : CalcKey
    data object OpenParen : CalcKey
    data object CloseParen : CalcKey
    data object Backspace : CalcKey
    data object Clear : CalcKey
    data object Equals : CalcKey
}

/**
 * Stato dell'editor.
 * @property expression espressione nel formato interno; `@` indica il risultato precedente.
 * @property ans risultato dell'ultimo calcolo, a piena precisione.
 * @property justEvaluated true subito dopo aver premuto "=".
 * @property error errore dell'ultimo "=", se presente.
 * @property cursor posizione del cursore nell'espressione; null = in fondo (il caso normale).
 *   Quando il cursore non è in fondo l'espressione non contiene `@`.
 */
data class CalculatorState(
    val expression: String = "",
    val ans: BigDecimal? = null,
    val justEvaluated: Boolean = false,
    val error: CalcError? = null,
    val cursor: Int? = null,
)

/** Calcolo completato con "=", da salvare nella cronologia (formato interno). */
data class CompletedCalculation(val expression: String, val result: String)

data class Transition(val state: CalculatorState, val completed: CompletedCalculation? = null)

/** Funzioni di utilità sul testo dell'espressione. */
object ExpressionText {
    private val FUNCTION_TOKENS = Tokenizer.FUNCTIONS.map { "$it(" }

    /**
     * Sostituisce `@` con il valore di Ans. Un valore negativo va tra parentesi se non è in prima posizione
     * o se è seguito da `^ ! %`: altrimenti `−5^2` si leggerebbe −(5²) invece di (−5)².
     */
    fun expandAns(expression: String, ans: BigDecimal): String {
        if ('@' !in expression) return expression
        val text = ResultFormatter.toInternal(ans)
        val out = StringBuilder()
        expression.forEachIndexed { index, c ->
            if (c == '@') {
                val next = expression.getOrNull(index + 1)
                val wrap = text.startsWith('-') && (index > 0 || (next != null && next in "^!%"))
                if (wrap) out.append('(').append(text).append(')') else out.append(text)
            } else {
                out.append(c)
            }
        }
        return out.toString()
    }

    /** Il numero che si sta digitando alla fine dell'espressione (può essere vuoto). */
    fun currentNumber(s: String): String {
        var i = s.length
        while (i > 0) {
            val c = s[i - 1]
            if (c.isDigit() || c == '.' || c == 'E') {
                i--
            } else if (c == '-' && i >= 2 && s[i - 2] == 'E') {
                i--
            } else {
                break
            }
        }
        return s.substring(i)
    }

    /** True se l'espressione termina con un operando completo (numero, costante, variabile, parentesi chiusa...). */
    fun endsWithOperand(s: String): Boolean {
        val c = s.lastOrNull() ?: return false
        return c.isDigit() || c == '.' || c in ")πe!%@" || c in VARIABLES
    }

    fun endsWithFunctionToken(s: String): String? = FUNCTION_TOKENS.firstOrNull { s.endsWith(it) }

    /** True se l'espressione è un solo numero: non c'è nulla da calcolare. */
    fun isSingleNumber(s: String): Boolean = s.isNotEmpty() && currentNumber(s) == s

    /** Intervalli [inizio, fine) dei token di più caratteri (`sin(`, `asin(`…), dentro i quali il cursore non va. */
    fun tokenSpans(s: String): List<IntRange> {
        val spans = mutableListOf<IntRange>()
        var i = 0
        while (i < s.length) {
            val token = FUNCTION_TOKENS.firstOrNull { s.startsWith(it, i) }
            if (token != null) {
                spans += i until i + token.length
                i += token.length
            } else {
                i++
            }
        }
        return spans
    }

    /** Porta una posizione interna a un token al suo inizio (direction < 0) o alla sua fine. */
    fun snap(s: String, position: Int, direction: Int = 1): Int {
        val span = tokenSpans(s).firstOrNull { position > it.first && position <= it.last } ?: return position
        return if (direction < 0) span.first else span.last + 1
    }
}

/** Logica di inserimento dei tasti, indipendente da Android e quindi facilmente testabile. */
object CalculatorEditor {
    private const val OPERATORS = "+-*/^"
    private const val MAX_DIGITS = 20

    fun onKey(
        state: CalculatorState,
        key: CalcKey,
        angleUnit: AngleUnit,
        variables: Map<Char, BigDecimal> = emptyMap(),
    ): Transition {
        if (key == CalcKey.Equals) return equals(state, angleUnit, variables)
        if (key == CalcKey.Clear) return Transition(CalculatorState())

        // Le regole guardano il testo prima del cursore; quello dopo resta com'è.
        val full = state.expression
        val position = (state.cursor ?: full.length).coerceIn(0, full.length)
        val s = full.substring(0, position)
        val rest = full.substring(position)
        val fresh = state.justEvaluated
        val prefix = when (key) {
            is CalcKey.Digit -> if (fresh) key.digit.toString() else digit(s, key.digit)
            CalcKey.Decimal -> if (fresh) "0." else decimal(s)
            is CalcKey.Operator -> if (fresh) "@${key.symbol}" else operator(s, key.symbol)
            CalcKey.Percent -> postfix(s, "%", fresh)
            CalcKey.Factorial -> postfix(s, "!", fresh)
            CalcKey.Square -> postfix(s, "^2", fresh)
            CalcKey.Reciprocal -> postfix(s, "^-1", fresh)
            CalcKey.SquareRoot -> operandStart(s, "√", fresh)
            is CalcKey.Function -> operandStart(s, "${key.name}(", fresh)
            is CalcKey.Constant -> operandStart(s, key.symbol.toString(), fresh)
            is CalcKey.Variable -> operandStart(s, key.name.toString(), fresh)
            CalcKey.ExpE -> operandStart(s, "e^(", fresh)
            CalcKey.Exp10 -> operandStart(s, "10^(", fresh)
            CalcKey.Parentheses -> if (fresh) "(" else parentheses(s)
            CalcKey.OpenParen -> operandStart(s, "(", fresh)
            CalcKey.CloseParen -> if (fresh) s else closeParen(s)
            CalcKey.Backspace -> backspace(s, state.ans)
            CalcKey.Clear, CalcKey.Equals -> s
        }
        val keepRest = !fresh && rest.isNotEmpty()
        return Transition(
            state.copy(
                expression = if (keepRest) prefix + rest else prefix,
                justEvaluated = false,
                error = null,
                cursor = if (keepRest) prefix.length else null,
            ),
        )
    }

    /** Inserisce un valore (per esempio dalla cronologia) nella posizione del cursore. */
    fun insertValue(state: CalculatorState, value: String): CalculatorState {
        val s = state.expression
        if (state.justEvaluated || s.isEmpty()) {
            // Come dopo "=": il valore diventa Ans, quindi x², ! e % si applicano al numero intero
            // (anche negativo) e una cifra digitata subito dopo lo sostituisce.
            val number = value.toBigDecimalOrNull()
            if (number != null) return CalculatorState(expression = "@", ans = number, justEvaluated = true)
        }
        // A metà espressione il valore resta un blocco unico: negativi e notazione E vanno tra parentesi,
        // altrimenti x² agirebbe senza il segno o una cifra digitata dopo finirebbe nell'esponente.
        val text = if (value.startsWith('-') || 'E' in value) "($value)" else value
        val position = (state.cursor ?: s.length).coerceIn(0, s.length)
        val before = s.substring(0, position)
        val after = s.substring(position)
        val prefix = when {
            state.justEvaluated || before.isEmpty() -> text
            ExpressionText.endsWithOperand(before) -> "$before*$text"
            else -> before + text
        }
        val keepRest = !state.justEvaluated && after.isNotEmpty()
        return state.copy(
            expression = if (keepRest) prefix + after else prefix,
            justEvaluated = false,
            error = null,
            cursor = if (keepRest) prefix.length else null,
        )
    }

    /** Anteprima del risultato mentre si digita; null se non c'è nulla da mostrare. */
    fun preview(
        state: CalculatorState,
        angleUnit: AngleUnit,
        variables: Map<Char, BigDecimal> = emptyMap(),
    ): BigDecimal? {
        val s = state.expression
        if (state.justEvaluated || s.isEmpty() || ExpressionText.isSingleNumber(s)) return null
        return (evaluate(s, angleUnit, state.ans, variables) as? EvalResult.Success)?.value
    }

    /**
     * Sposta il cursore in [position], contata sull'espressione con Ans già sostituito
     * (come la mostra lo schermo). Dopo "=" il risultato diventa testo modificabile.
     */
    fun moveCursorTo(state: CalculatorState, position: Int): CalculatorState {
        val editable = editable(state)
        val text = editable.expression
        val p = ExpressionText.snap(text, position.coerceIn(0, text.length))
        return editable.copy(cursor = if (p >= text.length) null else p, error = null)
    }

    /** Sposta il cursore di una posizione a sinistra (delta < 0) o a destra, saltando i token interi. */
    fun moveCursor(state: CalculatorState, delta: Int): CalculatorState {
        val editable = editable(state)
        val text = editable.expression
        val current = (editable.cursor ?: text.length).coerceIn(0, text.length)
        val target = ExpressionText.snap(text, (current + delta).coerceIn(0, text.length), delta)
        return editable.copy(cursor = if (target >= text.length) null else target, error = null)
    }

    private fun editable(state: CalculatorState): CalculatorState {
        val ans = state.ans
        val text = if ('@' in state.expression && ans != null) {
            ExpressionText.expandAns(state.expression, ans)
        } else {
            state.expression
        }
        return state.copy(expression = text, justEvaluated = false)
    }

    private fun equals(state: CalculatorState, angleUnit: AngleUnit, variables: Map<Char, BigDecimal>): Transition {
        val s = state.expression
        if (s.isEmpty() || state.justEvaluated) return Transition(state)
        return when (val result = evaluate(s, angleUnit, state.ans, variables)) {
            is EvalResult.Success -> {
                val expanded = state.ans?.let { ExpressionText.expandAns(s, it) } ?: s
                Transition(
                    state = CalculatorState(expression = "@", ans = result.value, justEvaluated = true),
                    completed = if (ExpressionText.isSingleNumber(expanded)) {
                        null
                    } else {
                        CompletedCalculation(expanded, ResultFormatter.toInternal(result.value))
                    },
                )
            }
            is EvalResult.Failure -> Transition(state.copy(error = result.error))
        }
    }

    private fun digit(s: String, d: Char): String {
        val number = ExpressionText.currentNumber(s)
        return when {
            number == "0" -> s.dropLast(1) + d
            number.count { it.isDigit() } >= MAX_DIGITS -> s
            ExpressionText.endsWithOperand(s) && number.isEmpty() -> "$s*$d"
            else -> s + d
        }
    }

    private fun decimal(s: String): String {
        val number = ExpressionText.currentNumber(s)
        return when {
            '.' in number || 'E' in number -> s
            number.isNotEmpty() -> "$s."
            ExpressionText.endsWithOperand(s) -> "$s*0."
            else -> "${s}0."
        }
    }

    private fun operator(s: String, op: Char): String {
        val last = s.lastOrNull() ?: return if (op == '-') "-" else s
        if (last in OPERATORS) {
            // Permette numeri negativi dopo ×, ÷ e ^ (es. 3×−2), altrimenti sostituisce l'operatore.
            if (op == '-' && last in "*/^") return s + op
            val trimmed = s.trimEnd { it in OPERATORS }
            return when {
                trimmed.isEmpty() -> if (op == '-') "-" else ""
                trimmed.last() == '(' || trimmed.last() == '√' -> if (op == '-') "$trimmed-" else trimmed
                else -> trimmed + op
            }
        }
        if (last == '(') return if (op == '-') s + op else s
        if (last == '√' || last == 'E') return s
        return s + op
    }

    private fun postfix(s: String, suffix: String, fresh: Boolean): String = when {
        fresh -> "@$suffix"
        ExpressionText.endsWithOperand(s) -> s + suffix
        else -> s
    }

    private fun operandStart(s: String, text: String, fresh: Boolean): String = when {
        fresh -> text
        ExpressionText.endsWithOperand(s) -> "$s*$text"
        else -> s + text
    }

    private fun parentheses(s: String): String {
        val open = s.count { it == '(' }
        val close = s.count { it == ')' }
        return when {
            ExpressionText.endsWithOperand(s) && open > close -> "$s)"
            ExpressionText.endsWithOperand(s) -> "$s*("
            else -> "$s("
        }
    }

    private fun closeParen(s: String): String {
        val open = s.count { it == '(' }
        val close = s.count { it == ')' }
        return if (open > close && ExpressionText.endsWithOperand(s)) "$s)" else s
    }

    private fun backspace(prefix: String, ans: BigDecimal?): String {
        var s = prefix
        // Dopo "=" si può modificare il risultato: Ans diventa il suo testo.
        if (s.endsWith('@') && ans != null) s = ExpressionText.expandAns(s, ans)
        val function = ExpressionText.endsWithFunctionToken(s)
        s = if (function != null) s.dropLast(function.length) else s.dropLast(1)
        // Un esponente rimasto vuoto ("1.5E-" o "1.5E") sparisce insieme alla E, così la cifra
        // o l'operatore successivo non finiscono nell'esponente.
        return when {
            s.endsWith("E-") -> s.dropLast(2)
            s.endsWith('E') -> s.dropLast(1)
            else -> s
        }
    }
}
