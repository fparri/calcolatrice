package io.github.calcolatrice.ui

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import io.github.calcolatrice.engine.CalcKey

/** Costruisce righe e griglie di tasti che inviano le loro azioni a [onAction]. */
class KeyPad(
    private val context: Context,
    private val palette: Palette,
    private val onAction: (PadAction) -> Unit,
) {
    fun button(key: PadKey, textSp: Float): KeyButton {
        val button = KeyButton(context)
        button.setTextSizeSp(textSp)
        button.setOnClickListener {
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            button.key?.let { onAction(it.action) }
        }
        button.setOnLongClickListener {
            val action = button.key?.longPressAction ?: return@setOnLongClickListener false
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onAction(action)
            true
        }
        button.bind(key, palette)
        return button
    }

    fun row(keys: List<PadKey>, spacing: Int, textSp: Float): Pair<LinearLayout, List<KeyButton>> {
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = keys.mapIndexed { index, key ->
            button(key, textSp).also { button ->
                line.addView(
                    button,
                    LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (index > 0) marginStart = spacing },
                )
            }
        }
        return line to buttons
    }

    fun grid(rows: List<List<PadKey>>, spacing: Int, textSp: Float): Pair<LinearLayout, List<List<KeyButton>>> {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val buttons = rows.mapIndexed { index, keys ->
            val (rowView, rowButtons) = row(keys, spacing, textSp)
            container.addView(
                rowView,
                LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { if (index > 0) topMargin = spacing },
            )
            rowButtons
        }
        return container to buttons
    }
}

/** Tasti della tastiera fisica comuni a tutte le schermate. */
object HardwareKeys {
    fun basic(c: Char): CalcKey? = when (c) {
        in '0'..'9' -> CalcKey.Digit(c)
        '.', ',' -> CalcKey.Decimal
        '+', '-', '*', '/', '^' -> CalcKey.Operator(c)
        'x', '×' -> CalcKey.Operator('*')
        ':', '÷' -> CalcKey.Operator('/')
        '%' -> CalcKey.Percent
        '!' -> CalcKey.Factorial
        '(' -> CalcKey.OpenParen
        ')' -> CalcKey.CloseParen
        '=' -> CalcKey.Equals
        'r', 'R', '√' -> CalcKey.SquareRoot
        'p', 'P', 'π' -> CalcKey.Constant('π')
        else -> null
    }
}
