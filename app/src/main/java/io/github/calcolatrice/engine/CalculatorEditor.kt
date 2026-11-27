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
    /** ×10ⁿ: notazione scientifica (`6.02E23`); da solo vale 1×10ⁿ. */
    data object Exponent : CalcKey
    /** Frazione su due livelli (tasto ▫/▫): il cursore va nel numeratore. */
    data object Fraction : CalcKey
    /** Parentesi "intelligente": apre o chiude in base al contesto. */
    data object Parentheses : CalcKey
    /** `;`: separa gli argomenti delle funzioni come `log(2;8)` o `nCr(5;2)`. */
    data object Separator : CalcKey
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
     * Frazioni su due livelli nel formato interno: `{numeratore~denominatore}`. I tre segni si inseriscono
     * e si cancellano sempre insieme, quindi una frazione è sempre completa.
     */
    const val FRACTION_OPEN = '{'
    const val FRACTION_BAR = '~'
    const val FRACTION_CLOSE = '}'

    /**
     * Inizio della parte in cui si sta scrivendo: subito dopo `{` o `~` della frazione aperta più
     * all'interno, oppure 0 fuori dalle frazioni. Parentesi e `;` contano solo in questa parte.
     */
    fun slotStart(s: String): Int {
        val stack = ArrayDeque<Int>()
        s.forEachIndexed { i, c ->
            when (c) {
                FRACTION_OPEN -> stack.addLast(i + 1)
                FRACTION_BAR -> if (stack.isNotEmpty()) {
                    stack.removeLast()
                    stack.addLast(i + 1)
                }
                FRACTION_CLOSE -> stack.removeLastOrNull()
            }
        }
        return stack.lastOrNull() ?: 0
    }

    /** True se [s] contiene una frazione su due livelli. */
    fun hasFraction(s: CharSequence): Boolean = s.contains(FRACTION_OPEN)

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

    /**
     * La funzione la cui parentesi è ancora aperta più all'interno alla fine di [s]:
     * il nome (es. `"log"`), `""` per una parentesi semplice, null se nessuna parentesi è aperta.
     */
    fun openFunction(s: String): String? {
        val stack = ArrayDeque<String>()
        var i = slotStart(s)
        while (i < s.length) {
            val token = FUNCTION_TOKENS.firstOrNull { s.startsWith(it, i) }
            when {
                token != null -> {
                    stack.addLast(token.dropLast(1))
                    i += token.length
                    continue
                }
                s[i] == '(' -> stack.addLast("")
                s[i] == ')' -> stack.removeLastOrNull()
            }
            i++
        }
        return stack.lastOrNull()
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
        return c.isDigit() || c == '.' || c in ")πe!%@" || c == FRACTION_CLOSE || c in VARIABLES
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

    /**
     * Dove inizia l'operando che termina in fondo a [s] (numero, variabile, parentesi, funzione, frazione,
     * con eventuali √ davanti, !/% dopo e potenze), oppure null se [s] non termina con un operando.
     * È il numeratore che il tasto ÷ prende in modalità "come Casio".
     */
    fun operandStart(s: String): Int? {
        if (!endsWithOperand(s)) return null
        var end = s.length
        while (end > 0 && s[end - 1] in "!%") end--
        if (end == 0) return null
        var start = when (val c = s[end - 1]) {
            ')', FRACTION_CLOSE -> {
                val open = if (c == ')') '(' else FRACTION_OPEN
                var depth = 0
                var k = end - 1
                while (k >= 0) {
                    if (s[k] == c) depth++
                    if (s[k] == open && --depth == 0) break
                    k--
                }
                if (k < 0) return null
                // Il nome della funzione fa parte dell'operando: sin(30) intero.
                val function = FUNCTION_TOKENS.firstOrNull { s.startsWith(it, k - it.length + 1) && k - it.length + 1 >= 0 }
                if (c == ')' && function != null) k - function.length + 1 else k
            }
            else -> if (c.isDigit() || c == '.') end - currentNumber(s.substring(0, end)).length else end - 1
        }
        while (start > 0 && s[start - 1] == '√') start--
        // Una potenza resta intera: 2^3 ÷ … → (2³)/….
        if (start > 0 && s[start - 1] == '^') {
            val base = operandStart(s.substring(0, start - 1))
            if (base != null) start = base
        }
        return start
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
        /** Modalità "come Casio": il tasto ÷ scrive una frazione su due livelli. */
        divisionMakesFraction: Boolean = false,
    ): Transition {
        if (key == CalcKey.Equals) return equals(state, angleUnit, variables)
        if (key == CalcKey.Clear) return Transition(CalculatorState())

        // Le regole guardano il testo prima del cursore; quello dopo resta com'è.
        val full = state.expression
        val position = (state.cursor ?: full.length).coerceIn(0, full.length)
        val s = full.substring(0, position)
        val rest = full.substring(position)
        val fresh = state.justEvaluated
        fractionKey(state, s, rest, key, divisionMakesFraction)?.let { return Transition(it) }
        // Dentro un esponente appena aperto ("1E", "1E-") vanno bene solo cifre, il segno meno e la cancellazione.
        val inExponent = !fresh && (s.endsWith('E') || s.endsWith("E-"))
        val exponentKey = key is CalcKey.Digit || key == CalcKey.Backspace || key == CalcKey.Operator('-')
        val prefix = if (inExponent && !exponentKey) s else when (key) {
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
            CalcKey.Exponent -> if (fresh) exponentAfterResult(state.ans) else exponent(s)
            CalcKey.Fraction -> s // gestito da fractionKey
            CalcKey.Parentheses -> if (fresh) "(" else parentheses(s)
            CalcKey.Separator -> if (fresh) s else separator(s)
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

    /**
     * Tasti che riguardano le frazioni su due livelli (il cursore finisce dentro la frazione, quindi non
     * seguono la regola generale "testo prima del cursore + resto"); null per tutti gli altri casi.
     */
    private fun fractionKey(
        state: CalculatorState,
        s: String,
        rest: String,
        key: CalcKey,
        divisionMakesFraction: Boolean,
    ): CalculatorState? {
        val fresh = state.justEvaluated
        if (!fresh && (s.endsWith('E') || s.endsWith("E-"))) return null
        val open = ExpressionText.FRACTION_OPEN
        val bar = ExpressionText.FRACTION_BAR
        val close = ExpressionText.FRACTION_CLOSE
        fun place(before: String, after: String, cursor: Int) = state.copy(
            expression = before + after,
            justEvaluated = false,
            error = null,
            cursor = cursor.takeIf { it < (before + after).length },
        )
        val tail = if (fresh) "" else rest
        return when {
            key == CalcKey.Fraction -> {
                // Frazione vuota, cursore nel numeratore.
                val before = operandStart(s, open.toString(), fresh)
                place(before, "$bar$close$tail", before.length)
            }
            key == CalcKey.Operator('/') && divisionMakesFraction -> {
                if (fresh) return place("$open@$bar", "$close", 3)
                // L'operando prima del cursore diventa il numeratore; il cursore va nel denominatore.
                val start = ExpressionText.operandStart(s)
                if (start == null) {
                    val before = s + open
                    place(before, "$bar$close$tail", before.length)
                } else {
                    val before = s.substring(0, start) + open + s.substring(start) + bar
                    place(before, "$close$tail", before.length)
                }
            }
            key == CalcKey.Backspace && !fresh -> when (s.lastOrNull()) {
                // Dopo una frazione si entra nel denominatore; all'inizio del denominatore si torna al numeratore.
                close, bar -> state.copy(cursor = s.length - 1, error = null)
                open -> if (rest.startsWith("$bar$close")) {
                    // Frazione vuota: sparisce.
                    place(s.dropLast(1), rest.drop(2), s.length - 1)
                } else {
                    state.copy(cursor = s.length - 1, error = null)
                }
                else -> null
            }
            else -> null
        }
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
            if (trimmed.endsWith('E')) return if (op == '-') "$trimmed-" else trimmed
            return when {
                trimmed.isEmpty() -> if (op == '-') "-" else ""
                trimmed.last() in "(;√{~" -> if (op == '-') "$trimmed-" else trimmed
                else -> trimmed + op
            }
        }
        if (last == '(' || last == ';' || last == ExpressionText.FRACTION_OPEN || last == ExpressionText.FRACTION_BAR) {
            return if (op == '-') s + op else s
        }
        if (last == '√') return s
        // Dopo la E dell'esponente è ammesso solo il meno (1E-5).
        if (last == 'E') return if (op == '-') "$s-" else s
        return s + op
    }

    /** ×10ⁿ dopo un numero: ne diventa l'esponente; senza numero davanti vale 1×10ⁿ. */
    private fun exponent(s: String): String {
        val number = ExpressionText.currentNumber(s)
        return when {
            'E' in number -> s
            number.any { it.isDigit() } -> "${s}E"
            ExpressionText.endsWithOperand(s) -> "$s*1E"
            else -> "${s}1E"
        }
    }

    /** ×10ⁿ subito dopo "=": il risultato (se è un numero semplice) diventa la mantissa, altrimenti Ans×1×10ⁿ. */
    private fun exponentAfterResult(ans: BigDecimal?): String {
        val text = ans?.let { ResultFormatter.toInternal(it) } ?: return "1E"
        return if (text.all { it.isDigit() || it == '.' }) "${text}E" else "@*1E"
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
        // Dentro una frazione contano solo le parentesi della parte in cui si scrive.
        val slot = s.substring(ExpressionText.slotStart(s))
        val open = slot.count { it == '(' }
        val close = slot.count { it == ')' }
        return when {
            ExpressionText.endsWithOperand(s) && open > close -> "$s)"
            ExpressionText.endsWithOperand(s) -> "$s*("
            else -> "$s("
        }
    }

    /** `;` solo dopo un argomento completo, dentro una funzione che ne accetta più d'uno. */
    private fun separator(s: String): String {
        val function = ExpressionText.openFunction(s)
        return if (function in Tokenizer.MULTI_ARGUMENT && ExpressionText.endsWithOperand(s)) "$s;" else s
    }

    private fun closeParen(s: String): String {
        val slot = s.substring(ExpressionText.slotStart(s))
        val open = slot.count { it == '(' }
        val close = slot.count { it == ')' }
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
