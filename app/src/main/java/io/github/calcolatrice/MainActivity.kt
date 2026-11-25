package io.github.calcolatrice

import android.animation.LayoutTransition
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.CalculatorSession
import io.github.calcolatrice.engine.CalculatorState
import io.github.calcolatrice.engine.DisplayFormatter
import io.github.calcolatrice.engine.HistoryEntry
import io.github.calcolatrice.engine.ResultFormatter
import io.github.calcolatrice.ui.HardwareKeys
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.KeyFactory
import io.github.calcolatrice.ui.KeyPad
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.PadKey
import io.github.calcolatrice.ui.Palette
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
import io.github.calcolatrice.ui.padForSystemBars
import io.github.calcolatrice.ui.rippleDrawable
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.setupEdgeToEdge
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormatSymbols

/**
 * Unica schermata dell'app, costruita solo con le View del sistema Android (nessuna libreria).
 * In verticale: display, pannello scientifico a scomparsa e tastierino.
 * In orizzontale (e su tablet ruotati): pannello scientifico e tastierino affiancati.
 */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var session: CalculatorSession
    private lateinit var palette: Palette
    private lateinit var keys: KeyFactory
    private lateinit var pad: KeyPad
    private lateinit var formatter: DisplayFormatter
    private lateinit var plainFormatter: DisplayFormatter
    private var decimalSeparator = '.'
    private var scientificExpanded = false
    private var landscape = false

    private lateinit var previousView: TextView
    private lateinit var mainView: TextView
    private lateinit var secondaryView: TextView
    private lateinit var angleView: TextView
    private lateinit var mainGridView: View
    private var scientificButtons: List<List<KeyButton>> = emptyList()
    private var expandButton: KeyButton? = null
    private var scientificExtraRows: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        palette = Palette(this)
        keys = KeyFactory(this)
        pad = KeyPad(this, palette, ::onAction)
        val symbols = DecimalFormatSymbols.getInstance(resources.configuration.locales[0])
        decimalSeparator = symbols.decimalSeparator
        formatter = DisplayFormatter(symbols.decimalSeparator, symbols.groupingSeparator)
        plainFormatter = DisplayFormatter(symbols.decimalSeparator, null)

        session = restoreSession(savedInstanceState)
        scientificExpanded = savedInstanceState?.getBoolean(KEY_SCIENTIFIC_EXPANDED) ?: false
        val config = resources.configuration
        landscape = config.screenWidthDp > config.screenHeightDp

        setContentView(buildContent())
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val calc = session.calc
        outState.putString(KEY_EXPRESSION, calc.expression)
        outState.putString(KEY_ANS, calc.ans?.toString())
        outState.putBoolean(KEY_JUST_EVALUATED, calc.justEvaluated)
        outState.putBoolean(KEY_INVERSE, session.inverse)
        outState.putString(KEY_LAST_EXPRESSION, session.lastExpression)
        outState.putBoolean(KEY_SCIENTIFIC_EXPANDED, scientificExpanded)
    }

    // ---------------------------------------------------------------- azioni

    private fun onAction(action: PadAction) {
        when (action) {
            is PadAction.Input -> if (session.onKey(action.key)) saveHistory()
            PadAction.ToggleInverse -> session.inverse = !session.inverse
            PadAction.ToggleAngle -> {
                session.toggleAngleUnit()
                prefs.edit().putString(PREF_ANGLE, session.angleUnit.name).apply()
            }
            PadAction.ToggleScientific -> scientificExpanded = !scientificExpanded
            PadAction.ShowHistory -> showHistory()
            PadAction.OpenEquations -> {
                startActivity(Intent(this, EquationActivity::class.java))
                return
            }
            // Azioni della schermata equazioni.
            PadAction.Back, is PadAction.Equation, is PadAction.SetEquationMode -> Unit
        }
        render()
    }

    /** Tastiere fisiche: tablet, Chromebook, tastiere Bluetooth. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isMetaPressed) {
            if (keyCode != KeyEvent.KEYCODE_C) return super.onKeyDown(keyCode, event)
            copyResult()
            return true
        }
        val key = hardwareKey(keyCode, event) ?: return super.onKeyDown(keyCode, event)
        onAction(PadAction.Input(key))
        return true
    }

    private fun hardwareKey(keyCode: Int, event: KeyEvent): CalcKey? = when (keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> CalcKey.Equals
        KeyEvent.KEYCODE_DEL -> CalcKey.Backspace
        KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_ESCAPE -> CalcKey.Clear
        else -> {
            val c = event.unicodeChar.toChar()
            HardwareKeys.basic(c) ?: scientificKey(c)
        }
    }

    private fun scientificKey(c: Char): CalcKey? = when (c) {
        // Scorciatoie per le funzioni scientifiche (rispettano il tasto INV).
        's', 'S' -> CalcKey.Function(if (session.inverse) "asin" else "sin")
        'c', 'C' -> CalcKey.Function(if (session.inverse) "acos" else "cos")
        't', 'T' -> CalcKey.Function(if (session.inverse) "atan" else "tan")
        'l', 'L' -> if (session.inverse) CalcKey.ExpE else CalcKey.Function("ln")
        'g', 'G' -> if (session.inverse) CalcKey.Exp10 else CalcKey.Function("log")
        'e' -> CalcKey.Constant('e')
        else -> null
    }

    private fun copyResult() {
        val calc = session.calc
        val ans = calc.ans
        val preview = session.preview
        val value = when {
            calc.justEvaluated && ans != null -> ResultFormatter.toInternal(ans)
            preview != null -> ResultFormatter.toInternal(preview)
            else -> calc.expression.ifEmpty { "0" }
        }
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), plainFormatter.format(value, ans)))
        // Da Android 13 il sistema mostra già una conferma.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- disegno

    private fun render() {
        val calc = session.calc
        val main = if (calc.expression.isEmpty()) "0" else formatter.format(calc.expression, calc.ans)
        mainView.text = main

        val previous = session.lastExpression?.let { formatter.format(it) + " =" }
        previousView.text = previous.orEmpty()
        previousView.visibility = if (previous == null) View.GONE else View.VISIBLE

        val error = calc.error?.let { getString(errorMessage(it)) }
        val preview = session.preview
            ?.let { formatter.format(ResultFormatter.toInternal(it)) }
            ?.takeIf { it != main }
        secondaryView.text = error ?: preview ?: ""
        secondaryView.setTextColor(if (error != null) palette.error else palette.onBackgroundVariant)

        val degrees = session.angleUnit == AngleUnit.DEGREES
        val angleName = getString(if (degrees) R.string.angle_degrees else R.string.angle_radians)
        angleView.text = angleName
        angleView.contentDescription = getString(R.string.angle_toggle, angleName)

        val specs = if (landscape) {
            keys.scientificLandscape(session.inverse, session.angleUnit)
        } else {
            keys.scientificPortrait(session.inverse, session.angleUnit)
        }
        scientificButtons.zip(specs).forEach { (buttons, row) ->
            buttons.zip(row).forEach { (button, key) -> if (button.key != key) button.bind(key, palette) }
        }

        if (!landscape) {
            val expandKey = keys.expandKey(scientificExpanded)
            expandButton?.let { if (it.key != expandKey) it.bind(expandKey, palette) }
            scientificExtraRows?.visibility = if (scientificExpanded) View.VISIBLE else View.GONE
            val params = mainGridView.layoutParams as LinearLayout.LayoutParams
            val weight = if (scientificExpanded) 1.6f else 2f
            if (params.weight != weight) {
                params.weight = weight
                mainGridView.layoutParams = params
            }
        }
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // Su tablet in verticale il contenuto resta centrato con una larghezza comoda.
        val width = if (!landscape && resources.configuration.screenWidthDp > 600) dp(600) else MATCH_PARENT
        root.addView(column, FrameLayout.LayoutParams(width, MATCH_PARENT, Gravity.CENTER_HORIZONTAL))

        column.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        column.addView(buildDisplay(), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        column.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))

        if (landscape) {
            val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val panel = FrameLayout(this).apply {
                background = roundedDrawable(palette.panel, dp(28).toFloat())
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            val (scientificGrid, buttons) = pad.grid(
                keys.scientificLandscape(session.inverse, session.angleUnit), dp(4), 18f,
            )
            scientificButtons = buttons
            panel.addView(scientificGrid, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            bottom.addView(panel, LinearLayout.LayoutParams(0, MATCH_PARENT, 3f))
            bottom.addView(View(this), LinearLayout.LayoutParams(dp(12), MATCH_PARENT))
            val (mainGrid, _) = pad.grid(keys.main(decimalSeparator), dp(6), 22f)
            mainGridView = mainGrid
            bottom.addView(mainGrid, LinearLayout.LayoutParams(0, MATCH_PARENT, 4f))
            column.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, 0, 2.2f))
        } else {
            column.addView(buildScientificPanel(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            column.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(12)))
            val (mainGrid, _) = pad.grid(keys.main(decimalSeparator), dp(8), 30f)
            mainGridView = mainGrid
            column.addView(mainGrid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 2f))
        }

        // Edge-to-edge: il contenuto evita barre di sistema e notch.
        root.padForSystemBars(dp(16), dp(8))
        return root
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        angleView = TextView(this).apply {
            setTextColor(palette.accent)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = rippleDrawable(palette.onBackground, dp(20).toFloat())
            isClickable = true
            isFocusable = true
            setOnClickListener { onAction(PadAction.ToggleAngle) }
        }
        bar.addView(angleView, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(pad.button(keys.equationsKey(), 16f), LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(pad.button(keys.historyKey(), 16f), LinearLayout.LayoutParams(dp(48), dp(48)))
        return bar
    }

    private fun buildDisplay(): View {
        val display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(8), 0, dp(8), 0)
            setOnLongClickListener {
                copyResult()
                true
            }
        }
        previousView = TextView(this).apply {
            textSize = 22f
            setTextColor(palette.onBackgroundVariant)
            gravity = Gravity.END
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        mainView = TextView(this).apply {
            setTextColor(palette.onBackground)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.END or Gravity.BOTTOM
            maxLines = 3
            includeFontPadding = false
            setAutoSizeTextTypeUniformWithConfiguration(
                24, if (landscape) 48 else 72, 2, TypedValue.COMPLEX_UNIT_SP,
            )
        }
        secondaryView = TextView(this).apply {
            textSize = 26f
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            // TalkBack annuncia il risultato quando cambia.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        display.addView(previousView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        display.addView(mainView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        display.addView(secondaryView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return display
    }

    /** Pannello scientifico verticale: prima riga sempre visibile, le altre a scomparsa. */
    private fun buildScientificPanel(): View {
        val rows = keys.scientificPortrait(session.inverse, session.angleUnit)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(palette.panel, dp(28).toFloat())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutTransition = LayoutTransition()
        }

        val firstLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (firstRow, firstButtons) = pad.row(rows.first(), dp(4), 18f)
        firstLine.addView(firstRow, LinearLayout.LayoutParams(0, dp(44), 1f))
        val expand = pad.button(keys.expandKey(scientificExpanded), 18f)
        expandButton = expand
        firstLine.addView(expand, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(4) })
        panel.addView(firstLine, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val extra = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val extraButtons = rows.drop(1).map { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val (rowView, rowButtons) = pad.row(row, dp(4), 18f)
            line.addView(rowView, LinearLayout.LayoutParams(0, dp(44), 1f))
            line.addView(View(this), LinearLayout.LayoutParams(dp(48), 1))
            extra.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })
            rowButtons
        }
        panel.addView(extra, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        scientificExtraRows = extra
        scientificButtons = listOf(firstButtons) + extraButtons
        return panel
    }

    // ---------------------------------------------------------------- cronologia

    private fun showHistory() {
        val history = session.history
        var dialog: AlertDialog? = null
        val content: View = if (history.isEmpty()) {
            TextView(this).apply {
                setText(R.string.history_empty)
                textSize = 16f
                setTextColor(palette.onBackgroundVariant)
                setPadding(dp(24), dp(16), dp(24), dp(16))
            }
        } else {
            val list = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            history.forEach { entry -> list.addView(historyItem(entry) { dialog?.dismiss() }) }
            ScrollView(this).apply { addView(list) }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.history)
            .setView(content)
            .setPositiveButton(R.string.close, null)
        if (history.isNotEmpty()) {
            builder.setNeutralButton(R.string.clear_history) { _, _ ->
                session.clearHistory()
                saveHistory()
            }
        }
        dialog = builder.show()
    }

    private fun historyItem(entry: HistoryEntry, onSelected: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = rippleDrawable(palette.onBackground, 0f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                session.useHistoryValue(entry)
                render()
                onSelected()
            }
            addView(
                TextView(context).apply {
                    text = formatter.format(entry.expression)
                    textSize = 15f
                    setTextColor(palette.onBackgroundVariant)
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
            )
            addView(
                TextView(context).apply {
                    text = "= " + formatter.format(entry.result)
                    textSize = 24f
                    setTextColor(palette.onBackground)
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
            )
        }

    // ---------------------------------------------------------------- salvataggio

    private fun restoreSession(bundle: Bundle?): CalculatorSession {
        val calc = CalculatorState(
            expression = bundle?.getString(KEY_EXPRESSION) ?: "",
            ans = bundle?.getString(KEY_ANS)?.toBigDecimalOrNull(),
            justEvaluated = bundle?.getBoolean(KEY_JUST_EVALUATED) ?: false,
        )
        val angleUnit = prefs.getString(PREF_ANGLE, null)
            ?.let { name -> AngleUnit.entries.firstOrNull { it.name == name } }
            ?: AngleUnit.DEGREES
        return CalculatorSession(
            calc = calc,
            angleUnit = angleUnit,
            inverse = bundle?.getBoolean(KEY_INVERSE) ?: false,
            history = decodeHistory(prefs.getString(PREF_HISTORY, null)),
            lastExpression = bundle?.getString(KEY_LAST_EXPRESSION),
        )
    }

    private fun saveHistory() {
        val array = JSONArray()
        session.history.forEach { array.put(JSONObject().put("e", it.expression).put("r", it.result)) }
        prefs.edit().putString(PREF_HISTORY, array.toString()).apply()
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

    // ---------------------------------------------------------------- utilità

    private companion object {
        const val PREFS_NAME = "calculator"
        const val PREF_ANGLE = "angle_unit"
        const val PREF_HISTORY = "history"
        const val KEY_EXPRESSION = "expression"
        const val KEY_ANS = "ans"
        const val KEY_JUST_EVALUATED = "just_evaluated"
        const val KEY_INVERSE = "inverse"
        const val KEY_LAST_EXPRESSION = "last_expression"
        const val KEY_SCIENTIFIC_EXPANDED = "scientific_expanded"
    }
}
