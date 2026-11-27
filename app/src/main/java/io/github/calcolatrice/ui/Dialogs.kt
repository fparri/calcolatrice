package io.github.calcolatrice.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.calcolatrice.R
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.DisplayFormatter
import io.github.calcolatrice.engine.NumberFormat
import io.github.calcolatrice.engine.ResultFormatter
import io.github.calcolatrice.engine.VARIABLES
import java.math.BigDecimal

/** Un risultato che si può salvare in una variabile, con l'etichetta da mostrare (es. "x = 2"). */
data class NamedValue(val label: String, val value: BigDecimal)

/**
 * Salva uno dei risultati in una variabile, da usare poi nella calcolatrice:
 * prima si sceglie il valore (se sono più d'uno), poi la variabile.
 */
fun Activity.showSaveToVariable(values: List<NamedValue>, settings: Settings, display: DisplayFormatter) {
    if (values.isEmpty()) return
    if (values.size == 1) {
        chooseVariable(values[0].value, settings, display)
        return
    }
    AlertDialog.Builder(this)
        .setTitle(R.string.choose_value)
        .setItems(values.map { it.label }.toTypedArray()) { _, which -> chooseVariable(values[which].value, settings, display) }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun Activity.chooseVariable(value: BigDecimal, settings: Settings, display: DisplayFormatter) {
    val current = settings.variables
    val labels = VARIABLES.map { name ->
        "$name   (${display.format(ResultFormatter.toInternal(current[name] ?: BigDecimal.ZERO))})"
    }
    AlertDialog.Builder(this)
        .setTitle(R.string.save_to_variable)
        .setItems(labels.toTypedArray()) { _, which ->
            val name = VARIABLES[which]
            settings.storeVariable(name, value)
            Toast.makeText(this, getString(R.string.saved_in, name.toString()), Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/** Scelta del formato dei numeri: Normale, Fix (decimali), Sci (cifre significative). */
fun Activity.showNumberFormatDialog(current: NumberFormat, onChosen: (NumberFormat) -> Unit) {
    val labels = arrayOf(
        getString(R.string.format_normal),
        getString(R.string.format_fix),
        getString(R.string.format_sci),
    )
    val selected = when (current) {
        NumberFormat.Normal -> 0
        is NumberFormat.Fix -> 1
        is NumberFormat.Sci -> 2
    }
    AlertDialog.Builder(this)
        .setTitle(R.string.number_format)
        .setSingleChoiceItems(labels, selected) { dialog, which ->
            dialog.dismiss()
            when (which) {
                0 -> onChosen(NumberFormat.Normal)
                1 -> chooseDigits(R.string.format_fix_digits, 0..9, (current as? NumberFormat.Fix)?.decimals ?: 2) {
                    onChosen(NumberFormat.Fix(it))
                }
                else -> chooseDigits(R.string.format_sci_digits, 1..10, (current as? NumberFormat.Sci)?.digits ?: 4) {
                    onChosen(NumberFormat.Sci(it))
                }
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun Activity.chooseDigits(title: Int, range: IntRange, current: Int, onChosen: (Int) -> Unit) {
    val items = range.map { it.toString() }.toTypedArray()
    AlertDialog.Builder(this)
        .setTitle(title)
        .setSingleChoiceItems(items, current - range.first) { dialog, which ->
            dialog.dismiss()
            onChosen(range.first + which)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/** Una voce del menu MATH: come si scrive, cosa fa e il tasto che la inserisce. */
data class MathEntry(val label: String, val description: String, val key: CalcKey)

/** Le funzioni del menu MATH, con i nomi nella lingua dell'utente (MCD, mcm…). */
fun Activity.mathEntries(display: DisplayFormatter): List<MathEntry> {
    fun entry(name: String, arguments: String, description: Int) =
        MathEntry(display.format("$name(") + arguments + ")", getString(description), CalcKey.Function(name))
    return listOf(
        entry("abs", "x", R.string.math_abs),
        entry("root", "n; x", R.string.math_root),
        entry("log", "b; x", R.string.math_log_base),
        entry("ncr", "n; k", R.string.math_ncr),
        entry("npr", "n; k", R.string.math_npr),
        entry("gcd", "a; b; …", R.string.math_gcd),
        entry("lcm", "a; b; …", R.string.math_lcm),
        entry("binp", "n; p; k", R.string.math_binp),
        entry("binc", "n; p; k", R.string.math_binc),
        entry("normc", "a; b; μ; σ", R.string.math_normc),
    )
}

/** Menu MATH: tocca una funzione per inserirla; gli argomenti si separano con il tasto ";". */
fun Activity.showMathMenu(entries: List<MathEntry>, palette: Palette, onChosen: (CalcKey) -> Unit) {
    var dialog: AlertDialog? = null
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, dp(8))
    }
    list.addView(
        TextView(this).apply {
            setText(R.string.math_hint)
            textSize = 14f
            setTextColor(palette.onBackgroundVariant)
            setPadding(dp(24), 0, dp(24), dp(8))
        },
        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
    )
    for (entry in entries) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(10))
            background = rippleDrawable(palette.onBackground, 0f)
            isClickable = true
            isFocusable = true
            contentDescription = entry.description
            setOnClickListener {
                dialog?.dismiss()
                onChosen(entry.key)
            }
        }
        row.addView(
            TextView(this).apply {
                text = entry.label
                textSize = 18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(palette.accent)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
        )
        row.addView(
            TextView(this).apply {
                text = entry.description
                textSize = 14f
                gravity = Gravity.START
                setTextColor(palette.onBackground)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
        )
        list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
    dialog = AlertDialog.Builder(this)
        .setTitle(R.string.math_menu_title)
        .setView(ScrollView(this).apply { addView(list) })
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
