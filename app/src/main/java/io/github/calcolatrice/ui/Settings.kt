package io.github.calcolatrice.ui

import android.content.Context
import android.content.SharedPreferences
import io.github.calcolatrice.engine.NumberFormat
import io.github.calcolatrice.engine.VARIABLES
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Impostazioni e dati condivisi tra le schermate: formato dei numeri e variabili.
 * Stanno nelle SharedPreferences della calcolatrice, quindi restano tra un avvio e l'altro.
 */
class Settings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var numberFormat: NumberFormat
        get() = NumberFormat.parse(prefs.getString(KEY_FORMAT, null))
        set(value) = prefs.edit().putString(KEY_FORMAT, value.code).apply()

    /** Variabili A, B, C, D, X, Y e memoria M (assenti = 0). */
    var variables: Map<Char, BigDecimal>
        get() = runCatching {
            val json = JSONObject(prefs.getString(KEY_VARIABLES, null) ?: return emptyMap())
            VARIABLES.mapNotNull { name ->
                json.optString(name.toString(), "").toBigDecimalOrNull()?.let { name to it }
            }.toMap()
        }.getOrDefault(emptyMap())
        set(value) {
            val json = JSONObject()
            value.forEach { (name, number) -> json.put(name.toString(), number.toString()) }
            prefs.edit().putString(KEY_VARIABLES, json.toString()).apply()
        }

    fun storeVariable(name: Char, value: BigDecimal) {
        variables = variables + (name to value)
    }

    companion object {
        const val NAME = "calculator"
        private const val KEY_FORMAT = "number_format"
        private const val KEY_VARIABLES = "variables"
    }
}
