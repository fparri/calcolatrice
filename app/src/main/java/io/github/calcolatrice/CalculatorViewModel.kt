package io.github.calcolatrice

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.CalculatorEditor
import io.github.calcolatrice.engine.CalculatorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/** Un calcolo nella cronologia, nel formato interno (formattato per la lingua solo a schermo). */
data class HistoryEntry(val expression: String, val result: String)

data class CalculatorUiState(
    val calc: CalculatorState = CalculatorState(),
    val preview: BigDecimal? = null,
    val angleUnit: AngleUnit = AngleUnit.DEGREES,
    val inverse: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    /** Espressione dell'ultimo "=", mostrata sopra il risultato. */
    val lastExpression: String? = null,
)

/**
 * Mantiene lo stato della calcolatrice.
 * - L'espressione in corso sopravvive alla rotazione e alla chiusura del processo (SavedStateHandle).
 * - Cronologia e unità angolare restano salvate tra un avvio e l'altro (SharedPreferences).
 */
class CalculatorViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(restore())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    fun onKey(key: CalcKey) = update { state ->
        val transition = CalculatorEditor.onKey(state.calc, key, state.angleUnit)
        val history = transition.completed
            ?.let { listOf(HistoryEntry(it.expression, it.result)) + state.history }
            ?.take(MAX_HISTORY)
            ?: state.history
        val lastExpression = when {
            !transition.state.justEvaluated -> null
            transition.completed != null -> transition.completed.expression
            else -> state.lastExpression
        }
        state.copy(calc = transition.state, history = history, lastExpression = lastExpression)
    }

    fun toggleAngleUnit() = update { state ->
        state.copy(
            angleUnit = if (state.angleUnit == AngleUnit.DEGREES) AngleUnit.RADIANS else AngleUnit.DEGREES,
        )
    }

    fun toggleInverse() = update { it.copy(inverse = !it.inverse) }

    fun useHistoryValue(entry: HistoryEntry) = update { state ->
        state.copy(calc = CalculatorEditor.insertValue(state.calc, entry.result), lastExpression = null)
    }

    fun clearHistory() = update { it.copy(history = emptyList()) }

    private fun update(transform: (CalculatorUiState) -> CalculatorUiState) {
        val previous = _uiState.value
        _uiState.update { current ->
            val next = transform(current)
            next.copy(preview = CalculatorEditor.preview(next.calc, next.angleUnit))
        }
        val state = _uiState.value
        savedState[KEY_EXPRESSION] = state.calc.expression
        savedState[KEY_ANS] = state.calc.ans?.toString()
        savedState[KEY_JUST_EVALUATED] = state.calc.justEvaluated
        savedState[KEY_INVERSE] = state.inverse
        if (state.history != previous.history || state.angleUnit != previous.angleUnit) {
            prefs.edit {
                putString(PREF_ANGLE, state.angleUnit.name)
                putString(PREF_HISTORY, encodeHistory(state.history))
            }
        }
    }

    private fun restore(): CalculatorUiState {
        val calc = CalculatorState(
            expression = savedState[KEY_EXPRESSION] ?: "",
            ans = savedState.get<String>(KEY_ANS)?.toBigDecimalOrNull(),
            justEvaluated = savedState[KEY_JUST_EVALUATED] ?: false,
        )
        val angleUnit = prefs.getString(PREF_ANGLE, null)
            ?.let { name -> AngleUnit.entries.firstOrNull { it.name == name } }
            ?: AngleUnit.DEGREES
        return CalculatorUiState(
            calc = calc,
            preview = CalculatorEditor.preview(calc, angleUnit),
            angleUnit = angleUnit,
            inverse = savedState[KEY_INVERSE] ?: false,
            history = decodeHistory(prefs.getString(PREF_HISTORY, null)),
        )
    }

    private fun encodeHistory(history: List<HistoryEntry>): String = JSONArray().apply {
        history.forEach { put(JSONObject().put("e", it.expression).put("r", it.result)) }
    }.toString()

    private fun decodeHistory(json: String?): List<HistoryEntry> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        List(array.length()) { i ->
            val item = array.getJSONObject(i)
            HistoryEntry(item.getString("e"), item.getString("r"))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_HISTORY = 100
        const val PREFS_NAME = "calculator"
        const val PREF_ANGLE = "angle_unit"
        const val PREF_HISTORY = "history"
        const val KEY_EXPRESSION = "expression"
        const val KEY_ANS = "ans"
        const val KEY_JUST_EVALUATED = "just_evaluated"
        const val KEY_INVERSE = "inverse"
    }
}
