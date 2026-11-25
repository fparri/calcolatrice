package io.github.calcolatrice

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.DisplayFormatter
import io.github.calcolatrice.engine.EquationMode
import io.github.calcolatrice.engine.EquationOutcome
import io.github.calcolatrice.engine.EquationSheet
import io.github.calcolatrice.engine.LinearSolution
import io.github.calcolatrice.engine.QuadraticSolution
import io.github.calcolatrice.engine.SolutionFormatter
import io.github.calcolatrice.engine.Vertex
import io.github.calcolatrice.ui.EquationCommand
import io.github.calcolatrice.ui.HardwareKeys
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.KeyFactory
import io.github.calcolatrice.ui.KeyPad
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.Palette
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
import io.github.calcolatrice.ui.padForSystemBars
import io.github.calcolatrice.ui.rippleColor
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.setupEdgeToEdge
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

/**
 * Equazioni e sistemi, come la modalità EQN delle calcolatrici scientifiche: si sceglie il tipo
 * di problema, si scrivono i coefficienti nella tabella e "=" risolve. Ogni coefficiente può essere
 * un'espressione (1/3, √2, −π…). Solo View di sistema, nessuna libreria.
 */
class EquationActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var sheet: EquationSheet
    private lateinit var palette: Palette
    private lateinit var keys: KeyFactory
    private lateinit var pad: KeyPad
    private lateinit var display: DisplayFormatter
    private lateinit var solutions: SolutionFormatter
    private var decimalSeparator = '.'
    private var asFraction = true
    private var landscape = false

    private lateinit var titleView: TextView
    private lateinit var fractionButton: KeyButton
    private var modeButtons: List<KeyButton> = emptyList()
    private lateinit var gridView: LinearLayout
    private var cellViews: List<TextView> = emptyList()
    private var gridMode: EquationMode? = null
    private lateinit var messageView: TextView
    private lateinit var resultView: TextView
    private lateinit var noteView: TextView

    /** Testo dei risultati: messaggio, righe principali e nota (il vertice della parabola). */
    private data class Description(val message: String?, val lines: List<String>, val note: String? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        palette = Palette(this)
        keys = KeyFactory(this)
        pad = KeyPad(this, palette, ::onAction)
        val symbols = DecimalFormatSymbols.getInstance(resources.configuration.locales[0])
        decimalSeparator = symbols.decimalSeparator
        display = DisplayFormatter(symbols.decimalSeparator, symbols.groupingSeparator)
        solutions = SolutionFormatter(display)
        asFraction = prefs.getBoolean(PREF_FRACTION, true)

        sheet = restoreSheet()
        if (prefs.getBoolean(PREF_SOLVED, false)) sheet.solve()
        savedInstanceState?.getInt(KEY_SELECTED, -1)
            ?.takeIf { it in 0 until sheet.mode.cellCount }
            ?.let { sheet.select(it) }

        val config = resources.configuration
        landscape = config.screenWidthDp > config.screenHeightDp
        setContentView(buildContent())
        render()
    }

    override fun onPause() {
        super.onPause()
        saveSheet()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED, sheet.selected)
    }

    // ---------------------------------------------------------------- azioni

    private fun onAction(action: PadAction) {
        when (action) {
            is PadAction.Input -> sheet.onKey(action.key)
            is PadAction.SetEquationMode -> sheet.setMode(action.mode)
            is PadAction.Equation -> when (action.command) {
                EquationCommand.NEXT_CELL -> sheet.next()
                EquationCommand.PREVIOUS_CELL -> sheet.previous()
                EquationCommand.CLEAR_CELLS -> sheet.clearAll()
                EquationCommand.TOGGLE_FRACTION -> {
                    asFraction = !asFraction
                    prefs.edit().putBoolean(PREF_FRACTION, asFraction).apply()
                }
            }
            PadAction.Back -> {
                finish()
                return
            }
            else -> Unit
        }
        render()
    }

    /** Tastiere fisiche: Tab passa al coefficiente successivo, Invio risolve. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isMetaPressed) {
            if (keyCode != KeyEvent.KEYCODE_C) return super.onKeyDown(keyCode, event)
            copyResults()
            return true
        }
        val action = when (keyCode) {
            KeyEvent.KEYCODE_TAB -> PadAction.Equation(
                if (event.isShiftPressed) EquationCommand.PREVIOUS_CELL else EquationCommand.NEXT_CELL,
            )
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> PadAction.Input(CalcKey.Equals)
            KeyEvent.KEYCODE_DEL -> PadAction.Input(CalcKey.Backspace)
            KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_ESCAPE -> PadAction.Input(CalcKey.Clear)
            else -> HardwareKeys.basic(event.unicodeChar.toChar())?.let { PadAction.Input(it) }
        } ?: return super.onKeyDown(keyCode, event)
        onAction(action)
        return true
    }

    private fun copyResults() {
        if (sheet.outcome == null) return
        val text = listOf(messageView, resultView, noteView)
            .filter { it.visibility == View.VISIBLE }
            .joinToString("\n") { it.text }
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.equations), text))
        // Da Android 13 il sistema mostra già una conferma.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- disegno

    private fun render() {
        val mode = sheet.mode
        if (gridMode != mode) buildGrid(mode)

        titleView.text = getString(formula(mode))
        modeButtons.zip(keys.equationModes(mode)).forEach { (button, key) ->
            if (button.key != key) button.bind(key, palette)
        }
        keys.fractionKey(asFraction).let { if (fractionButton.key != it) fractionButton.bind(it, palette) }

        val outcome = sheet.outcome
        val errorCell = (outcome as? EquationOutcome.Error)?.cell
        val expressions = sheet.expressions()
        cellViews.forEachIndexed { index, cell ->
            val expression = expressions[index]
            val text = if (expression.isEmpty()) "0" else display.format(expression)
            val selected = index == sheet.selected
            cell.text = text
            cell.setTextColor(if (expression.isEmpty()) palette.onBackgroundVariant else palette.onBackground)
            cell.background = cellBackground(selected, error = index == errorCell)
            cell.isSelected = selected
            cell.contentDescription = getString(
                R.string.cell_description,
                index / mode.coefficients + 1,
                LETTERS[index % mode.coefficients].toString(),
                text,
            )
        }

        val description = describe(outcome)
        messageView.text = description.message.orEmpty()
        messageView.visibility = if (description.message == null) View.GONE else View.VISIBLE
        messageView.setTextColor(if (outcome is EquationOutcome.Error) palette.error else palette.onBackgroundVariant)
        resultView.text = description.lines.joinToString("\n")
        resultView.visibility = if (description.lines.isEmpty()) View.GONE else View.VISIBLE
        noteView.text = description.note.orEmpty()
        noteView.visibility = if (description.note == null) View.GONE else View.VISIBLE
    }

    private fun describe(outcome: EquationOutcome?): Description = when (outcome) {
        null -> Description(getString(R.string.solve_hint), emptyList())
        is EquationOutcome.Error -> Description(getString(errorMessage(outcome.error)), emptyList())
        is EquationOutcome.System -> describeLinear(outcome.solution, listOf("x", "y", "z"))
        is EquationOutcome.Quadratic -> describeQuadratic(outcome.solution)
    }

    private fun describeLinear(solution: LinearSolution, names: List<String>): Description = when (solution) {
        is LinearSolution.Unique -> Description(null, solution.values.mapIndexed { i, v -> "${names[i]} = ${value(v)}" })
        LinearSolution.None -> Description(getString(R.string.no_solution), emptyList())
        LinearSolution.Infinite -> Description(getString(R.string.infinite_solutions), emptyList())
    }

    private fun describeQuadratic(solution: QuadraticSolution): Description = when (solution) {
        is QuadraticSolution.TwoReal -> Description(
            null,
            listOf("x₁ = ${value(solution.x1)}", "x₂ = ${value(solution.x2)}"),
            vertex(solution.vertex),
        )
        is QuadraticSolution.DoubleRoot -> Description(
            getString(R.string.double_root),
            listOf("x = ${value(solution.x)}"),
            vertex(solution.vertex),
        )
        is QuadraticSolution.ComplexPair -> Description(
            getString(R.string.no_real_solution),
            listOf(
                "x₁ = " + solutions.complex(solution.re, solution.im, plus = true, asFraction = asFraction),
                "x₂ = " + solutions.complex(solution.re, solution.im, plus = false, asFraction = asFraction),
            ),
            vertex(solution.vertex),
        )
        is QuadraticSolution.Linear -> {
            val linear = describeLinear(solution.solution, listOf("x"))
            Description(
                listOfNotNull(getString(R.string.linear_equation), linear.message).joinToString("\n"),
                linear.lines,
            )
        }
    }

    private fun value(x: BigDecimal) = solutions.value(x, asFraction)

    private fun vertex(v: Vertex) = getString(
        if (v.isMinimum) R.string.vertex_minimum else R.string.vertex_maximum,
        value(v.x),
        value(v.y),
    )

    private fun formula(mode: EquationMode): Int = when (mode) {
        EquationMode.SYSTEM_2 -> R.string.formula_system_2
        EquationMode.SYSTEM_3 -> R.string.formula_system_3
        EquationMode.QUADRATIC -> R.string.formula_quadratic
    }

    private fun cellBackground(selected: Boolean, error: Boolean): Drawable {
        val shape = GradientDrawable().apply {
            setColor(palette.panel)
            cornerRadius = dp(12).toFloat()
            when {
                error -> setStroke(dp(2), palette.error)
                selected -> setStroke(dp(2), palette.accent)
            }
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor(palette.onBackground)), shape, null)
    }

    // ---------------------------------------------------------------- layout

    private fun buildContent(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // Su tablet in verticale il contenuto resta centrato con una larghezza comoda.
        val width = if (!landscape && resources.configuration.screenWidthDp > 600) dp(600) else MATCH_PARENT
        root.addView(column, FrameLayout.LayoutParams(width, MATCH_PARENT, Gravity.CENTER_HORIZONTAL))

        val editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        editor.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        val (modeRow, buttons) = pad.row(keys.equationModes(sheet.mode), dp(8), 15f)
        modeButtons = buttons
        editor.addView(modeRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply { topMargin = dp(4) })
        gridView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        editor.addView(gridView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        editor.addView(buildResults(), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(4) })

        val keypad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tools = FrameLayout(this).apply {
            background = roundedDrawable(palette.panel, dp(28).toFloat())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val (toolRow, _) = pad.row(keys.equationTools(), dp(4), 18f)
        tools.addView(toolRow, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        keypad.addView(tools, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)))
        val (mainGrid, _) = pad.grid(
            keys.equationMain(decimalSeparator),
            dp(if (landscape) 6 else 8),
            if (landscape) 22f else 28f,
        )
        keypad.addView(mainGrid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(10) })

        if (landscape) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(editor, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            row.addView(keypad, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(16) })
            column.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            column.addView(editor, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            column.addView(keypad, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
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
        bar.addView(pad.button(keys.backKey(), 16f), LinearLayout.LayoutParams(dp(48), dp(48)))
        titleView = TextView(this).apply {
            textSize = 20f
            setTextColor(palette.onBackground)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(8), 0, dp(8), 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        bar.addView(titleView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        fractionButton = pad.button(keys.fractionKey(asFraction), 16f)
        bar.addView(fractionButton, LinearLayout.LayoutParams(dp(64), dp(40)))
        return bar
    }

    private fun buildResults(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(dp(8), dp(8), dp(8), dp(8))
            // Pressione prolungata: copia i risultati.
            setOnLongClickListener {
                copyResults()
                true
            }
        }
        messageView = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.END
        }
        resultView = TextView(this).apply {
            textSize = if (landscape) 20f else 24f
            gravity = Gravity.END
            setTextColor(palette.onBackground)
            setLineSpacing(dp(4).toFloat(), 1f)
            // TalkBack annuncia i risultati quando cambiano.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        noteView = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.END
            setTextColor(palette.onBackgroundVariant)
        }
        box.addView(messageView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        box.addView(resultView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })
        box.addView(noteView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        return ScrollView(this).apply { addView(box) }
    }

    /** Tabella dei coefficienti: intestazione con le lettere della formula e una riga per equazione. */
    private fun buildGrid(mode: EquationMode) {
        gridView.removeAllViews()
        val spacing = dp(6)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (c in 0 until mode.coefficients) {
            val label = TextView(this).apply {
                text = LETTERS[c].toString()
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(palette.onBackgroundVariant)
                typeface = Typeface.create("serif", Typeface.ITALIC)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            header.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (c > 0) marginStart = spacing })
        }
        gridView.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val cellHeight = dp(if (landscape) 38 else 44)
        val cells = ArrayList<TextView>(mode.cellCount)
        for (r in 0 until mode.equations) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until mode.coefficients) {
                val index = r * mode.coefficients + c
                val cell = TextView(this).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(dp(6), 0, dp(6), 0)
                    setAutoSizeTextTypeUniformWithConfiguration(10, 20, 1, TypedValue.COMPLEX_UNIT_SP)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        sheet.select(index)
                        render()
                    }
                }
                cells += cell
                row.addView(cell, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { if (c > 0) marginStart = spacing })
            }
            gridView.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, cellHeight).apply { topMargin = spacing })
        }
        cellViews = cells
        gridMode = mode
    }

    // ---------------------------------------------------------------- salvataggio

    /** I coefficienti restano salvati anche chiudendo la schermata, come sulle calcolatrici. */
    private fun restoreSheet(): EquationSheet {
        val json = prefs.getString(PREF_SHEET, null) ?: return EquationSheet()
        return runCatching {
            val saved = JSONObject(json)
            val savedCells = saved.getJSONObject("cells")
            val cells = EquationMode.entries.associateWith { mode ->
                val array = savedCells.optJSONArray(mode.name) ?: JSONArray()
                List(array.length()) { array.getString(it) }
            }
            EquationSheet(EquationMode.valueOf(saved.getString("mode")), cells)
        }.getOrElse { EquationSheet() }
    }

    private fun saveSheet() {
        val cells = JSONObject()
        EquationMode.entries.forEach { mode -> cells.put(mode.name, JSONArray(sheet.expressions(mode))) }
        val saved = JSONObject().put("mode", sheet.mode.name).put("cells", cells)
        prefs.edit()
            .putString(PREF_SHEET, saved.toString())
            .putBoolean(PREF_SOLVED, sheet.outcome != null)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "equations"
        const val PREF_SHEET = "sheet"
        const val PREF_SOLVED = "solved"
        const val PREF_FRACTION = "fractions"
        const val KEY_SELECTED = "selected"
        const val LETTERS = "abcd"
    }
}
