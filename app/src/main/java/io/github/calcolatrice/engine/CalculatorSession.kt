package io.github.calcolatrice.engine

import java.math.BigDecimal

/** Un calcolo nella cronologia, nel formato interno (formattato per la lingua solo a schermo). */
data class HistoryEntry(val expression: String, val result: String)

/**
 * Stato completo della calcolatrice, indipendente da Android.
 * L'Activity lo mostra a schermo e ne salva le parti persistenti.
 */
class CalculatorSession(
    calc: CalculatorState = CalculatorState(),
    angleUnit: AngleUnit = AngleUnit.DEGREES,
    var inverse: Boolean = false,
    history: List<HistoryEntry> = emptyList(),
    lastExpression: String? = null,
    variables: Map<Char, BigDecimal> = emptyMap(),
) {
    var calc: CalculatorState = calc
        private set
    var angleUnit: AngleUnit = angleUnit
        private set
    var history: List<HistoryEntry> = history
        private set

    /** Valori delle variabili A, B, C, D, X, Y e della memoria M (assenti = 0). */
    var variables: Map<Char, BigDecimal> = variables.filterKeys { it in VARIABLES }
        private set

    /** Espressione dell'ultimo "=" (o dell'ultimo SOLVE), mostrata sopra il risultato. */
    var lastExpression: String? = lastExpression
        private set

    /** Variabile trovata dall'ultimo SOLVE, finché si mostra il suo risultato. */
    var solvedFor: Char? = null
        private set

    /** Anteprima del risultato mentre si digita. */
    var preview: BigDecimal? = CalculatorEditor.preview(calc, angleUnit, this.variables)
        private set

    /** Applica un tasto. Restituisce true se la cronologia è cambiata (va salvata). */
    fun onKey(key: CalcKey): Boolean {
        val transition = CalculatorEditor.onKey(calc, key, angleUnit, variables)
        val completed = transition.completed
        calc = transition.state
        solvedFor = null
        lastExpression = when {
            !calc.justEvaluated -> null
            completed != null -> completed.expression
            else -> lastExpression
        }
        if (completed != null) {
            history = (listOf(HistoryEntry(completed.expression, completed.result)) + history).take(MAX_HISTORY)
        }
        updatePreview()
        return completed != null
    }

    fun toggleAngleUnit() {
        angleUnit = if (angleUnit == AngleUnit.DEGREES) AngleUnit.RADIANS else AngleUnit.DEGREES
        updatePreview()
    }

    fun useHistoryValue(entry: HistoryEntry) {
        calc = CalculatorEditor.insertValue(calc, entry.result)
        lastExpression = null
        solvedFor = null
        updatePreview()
    }

    fun clearHistory() {
        history = emptyList()
    }

    // ------------------------------------------------------------ cursore

    /** Sposta il cursore di una posizione (delta −1 o +1). */
    fun moveCursor(delta: Int) = edit { CalculatorEditor.moveCursor(it, delta) }

    /** Sposta il cursore in un punto dell'espressione mostrata. */
    fun moveCursorTo(position: Int) = edit { CalculatorEditor.moveCursorTo(it, position) }

    private inline fun edit(change: (CalculatorState) -> CalculatorState) {
        if (calc.expression.isEmpty()) return
        calc = change(calc)
        lastExpression = null
        solvedFor = null
        updatePreview()
    }

    // ------------------------------------------------------------ variabili e memoria

    /** True se c'è un valore da salvare con STO o M+ (un risultato o un'espressione da calcolare). */
    val canStore: Boolean get() = calc.expression.isNotEmpty() || calc.ans != null

    fun setVariables(values: Map<Char, BigDecimal>) {
        variables = values.filterKeys { it in VARIABLES }
        updatePreview()
    }

    fun clearVariables() {
        variables = emptyMap()
        updatePreview()
    }

    /** STO: calcola l'espressione (se serve) e ne salva il risultato in [name]. True se la cronologia è cambiata. */
    fun store(name: Char): Boolean {
        require(name in VARIABLES)
        val (value, historyChanged) = commitValue()
        if (value != null) setVariable(name, value)
        return historyChanged
    }

    /** M+ e M−: calcola l'espressione (se serve) e la somma o sottrae alla memoria M. */
    fun addToMemory(subtract: Boolean): Boolean {
        val (value, historyChanged) = commitValue()
        if (value != null) {
            val memory = variables['M'] ?: BigDecimal.ZERO
            val sum = if (subtract) memory.subtract(value, MathSupport.MC) else memory.add(value, MathSupport.MC)
            try {
                setVariable('M', MathSupport.checked(sum))
            } catch (e: CalcException) {
                calc = calc.copy(error = e.error)
            }
        }
        return historyChanged
    }

    private fun setVariable(name: Char, value: BigDecimal) {
        variables = variables + (name to value)
        updatePreview()
    }

    /** Il risultato corrente, premendo "=" se l'espressione non è ancora stata calcolata. */
    private fun commitValue(): Pair<BigDecimal?, Boolean> {
        if (calc.justEvaluated || calc.expression.isEmpty()) return calc.ans to false
        val historyChanged = onKey(CalcKey.Equals)
        return (if (calc.justEvaluated) calc.ans else null) to historyChanged
    }

    // ------------------------------------------------------------ SOLVE

    /**
     * Cerca X tale che l'espressione valga 0, partendo dal valore attuale di X.
     * Se la trova, la salva in X e la mostra come risultato.
     */
    fun solve() {
        val expression = calc.expression
        if (expression.isEmpty() || calc.justEvaluated) return
        val start = variables['X'] ?: BigDecimal.ZERO
        when (val result = NumericSolver.solve(expression, angleUnit, calc.ans, variables, 'X', start)) {
            is EvalResult.Success -> {
                lastExpression = calc.ans?.let { ExpressionText.expandAns(expression, it) } ?: expression
                variables = variables + ('X' to result.value)
                calc = CalculatorState(expression = "@", ans = result.value, justEvaluated = true)
                solvedFor = 'X'
            }
            is EvalResult.Failure -> calc = calc.copy(error = result.error)
        }
        updatePreview()
    }

    private fun updatePreview() {
        preview = CalculatorEditor.preview(calc, angleUnit, variables)
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
