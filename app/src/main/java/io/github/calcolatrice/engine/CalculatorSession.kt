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
    /** Modalità "come Casio": il tasto ÷ scrive una frazione su due livelli. */
    var divisionMakesFraction: Boolean = false,
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

    /** Valori delle variabili A, B, C, D, X, Y (assenti = 0). */
    var variables: Map<Char, BigDecimal> = variables.filterKeys { it in VARIABLES }
        private set

    /** Espressione dell'ultimo "=", mostrata sopra il risultato. */
    var lastExpression: String? = lastExpression
        private set

    /** Anteprima del risultato mentre si digita. */
    var preview: BigDecimal? = CalculatorEditor.preview(calc, angleUnit, this.variables)
        private set

    /** Applica un tasto. Restituisce true se la cronologia è cambiata (va salvata). */
    fun onKey(key: CalcKey): Boolean {
        val transition = CalculatorEditor.onKey(calc, key, angleUnit, variables, divisionMakesFraction)
        val completed = transition.completed
        calc = transition.state
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

    private fun updatePreview() {
        preview = CalculatorEditor.preview(calc, angleUnit, variables)
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
