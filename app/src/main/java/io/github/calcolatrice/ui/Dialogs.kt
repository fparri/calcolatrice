package io.github.calcolatrice.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import io.github.calcolatrice.R
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

/** Scelta del formato dei numeri: Normale, Fix (decimali), Sci (cifre significative), Eng. */
fun Activity.showNumberFormatDialog(current: NumberFormat, onChosen: (NumberFormat) -> Unit) {
    val labels = arrayOf(
        getString(R.string.format_normal),
        getString(R.string.format_fix),
        getString(R.string.format_sci),
        getString(R.string.format_eng),
    )
    val selected = when (current) {
        NumberFormat.Normal -> 0
        is NumberFormat.Fix -> 1
        is NumberFormat.Sci -> 2
        NumberFormat.Eng -> 3
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
                2 -> chooseDigits(R.string.format_sci_digits, 1..10, (current as? NumberFormat.Sci)?.digits ?: 4) {
                    onChosen(NumberFormat.Sci(it))
                }
                else -> onChosen(NumberFormat.Eng)
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
