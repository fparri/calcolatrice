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
) {
    var calc: CalculatorState = calc
        private set
    var angleUnit: AngleUnit = angleUnit
        private set
    var history: List<HistoryEntry> = history
        private set

    /** Espressione dell'ultimo "=", mostrata sopra il risultato. */
    var lastExpression: String? = lastExpression
        private set

    /** Anteprima del risultato mentre si digita. */
    var preview: BigDecimal? = CalculatorEditor.preview(calc, angleUnit)
        private set

    /** Applica un tasto. Restituisce true se la cronologia è cambiata (va salvata). */
    fun onKey(key: CalcKey): Boolean {
        val transition = CalculatorEditor.onKey(calc, key, angleUnit)
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

    private fun updatePreview() {
        preview = CalculatorEditor.preview(calc, angleUnit)
    }

    companion object {
        const val MAX_HISTORY = 100
    }
}
