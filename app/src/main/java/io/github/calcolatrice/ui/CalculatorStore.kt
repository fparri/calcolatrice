package io.github.calcolatrice.ui

import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalculatorSession
import io.github.calcolatrice.engine.CalculatorState
import io.github.calcolatrice.engine.HistoryEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Salvataggio della calcolatrice nelle SharedPreferences: il calcolo in corso, la cronologia, l'unità degli
 * angoli, le variabili e la scelta "Frazioni". Resta tutto anche chiudendo l'app, come sulle calcolatrici.
 */
class CalculatorStore(private val settings: Settings) {
    private val prefs = settings.prefs

    var asFraction: Boolean
        get() = prefs.getBoolean(PREF_FRACTION, false)
        set(value) = prefs.edit().putBoolean(PREF_FRACTION, value).apply()

    fun restore(): CalculatorSession {
        val saved = runCatching { JSONObject(prefs.getString(PREF_STATE, null) ?: "{}") }.getOrDefault(JSONObject())
        val expression = saved.optString("expression", "")
        val calc = CalculatorState(
            expression = expression,
            ans = saved.optString("ans", "").toBigDecimalOrNull(),
            justEvaluated = saved.optBoolean("justEvaluated", false),
            cursor = saved.optInt("cursor", -1).takeIf { it in 0 until expression.length },
        )
        val angleUnit = prefs.getString(PREF_ANGLE, null)
            ?.let { name -> AngleUnit.entries.firstOrNull { it.name == name } }
            ?: AngleUnit.DEGREES
        return CalculatorSession(
            calc = calc,
            angleUnit = angleUnit,
            inverse = saved.optBoolean("inverse", false),
            history = decodeHistory(prefs.getString(PREF_HISTORY, null)),
            lastExpression = saved.optString("lastExpression", "").ifEmpty { null },
            variables = settings.variables,
        )
    }

    fun saveState(session: CalculatorSession) {
        val calc = session.calc
        val state = JSONObject()
            .put("expression", calc.expression)
            .put("ans", calc.ans?.toString().orEmpty())
            .put("justEvaluated", calc.justEvaluated)
            .put("cursor", calc.cursor ?: -1)
            .put("inverse", session.inverse)
            .put("lastExpression", session.lastExpression.orEmpty())
        prefs.edit().putString(PREF_STATE, state.toString()).apply()
        saveVariables(session)
    }

    fun saveVariables(session: CalculatorSession) {
        settings.variables = session.variables
    }

    fun saveHistory(session: CalculatorSession) {
        val array = JSONArray()
        session.history.forEach { array.put(JSONObject().put("e", it.expression).put("r", it.result)) }
        prefs.edit().putString(PREF_HISTORY, array.toString()).apply()
    }

    fun saveAngle(unit: AngleUnit) {
        prefs.edit().putString(PREF_ANGLE, unit.name).apply()
    }

    private fun decodeHistory(json: String?): List<HistoryEntry> {
        if (json == null) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                HistoryEntry(item.getString("e"), item.getString("r"))
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREF_ANGLE = "angle_unit"
        const val PREF_HISTORY = "history"
        const val PREF_STATE = "state"
        const val PREF_FRACTION = "fraction_main"
    }
}
