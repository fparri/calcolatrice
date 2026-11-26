package io.github.calcolatrice

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
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
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.DisplayFormatter
import io.github.calcolatrice.engine.OneVariableStats
import io.github.calcolatrice.engine.RegressionStats
import io.github.calcolatrice.engine.SolutionFormatter
import io.github.calcolatrice.engine.StatMode
import io.github.calcolatrice.engine.StatOutcome
import io.github.calcolatrice.engine.StatisticsSheet
import io.github.calcolatrice.ui.HardwareKeys
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.KeyFactory
import io.github.calcolatrice.ui.KeyPad
import io.github.calcolatrice.ui.NamedValue
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.Palette
import io.github.calcolatrice.ui.Settings
import io.github.calcolatrice.ui.SheetCommand
import io.github.calcolatrice.ui.cellBackground
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
import io.github.calcolatrice.ui.localFunctionNames
import io.github.calcolatrice.ui.padForSystemBars
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.setupEdgeToEdge
import io.github.calcolatrice.ui.showSaveToVariable
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

/**
 * Statistica come la modalità STAT delle calcolatrici: una colonna di dati (media, deviazione
 * standard, quartili…) oppure coppie (x, y) per la retta di regressione. Solo View di sistema.
 */
class StatisticsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var settings: Settings
    private lateinit var sheet: StatisticsSheet
    private lateinit var palette: Palette
    private lateinit var keys: KeyFactory
    private lateinit var pad: KeyPad
    private lateinit var display: DisplayFormatter
    private lateinit var solutions: SolutionFormatter
    private var decimalSeparator = '.'
    private var landscape = false

    /** True dopo "=": al posto della tabella si vedono i risultati, fino al tasto successivo. */
    private var showResults = false

    private var modeButtons: List<KeyButton> = emptyList()
    private lateinit var tableScroll: ScrollView
    private lateinit var tableView: LinearLayout
    private lateinit var resultsScroll: ScrollView
    private lateinit var resultView: TextView
    private lateinit var messageView: TextView
    private var rowViews: List<LinearLayout> = emptyList()
    private var cellViews: List<List<TextView>> = emptyList()
    private var tableShape: Pair<StatMode, Int>? = null
    private var storable: List<NamedValue> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        settings = Settings(this)
        palette = Palette(this)
        keys = KeyFactory(this)
        pad = KeyPad(this, palette, ::onAction)
        val symbols = DecimalFormatSymbols.getInstance(resources.configuration.locales[0])
        decimalSeparator = symbols.decimalSeparator
        display = DisplayFormatter(symbols.decimalSeparator, symbols.groupingSeparator, localFunctionNames())
        solutions = SolutionFormatter(display, settings.numberFormat)

        sheet = restoreSheet()
        if (prefs.getBoolean(PREF_SOLVED, false)) {
            sheet.solve()
            showResults = true
        }
        val config = resources.configuration
        landscape = config.screenWidthDp > config.screenHeightDp
        setContentView(buildContent())
        render()
    }

    override fun onPause() {
        super.onPause()
        saveSheet()
    }

    // ---------------------------------------------------------------- azioni

    private fun onAction(action: PadAction) {
        when (action) {
            is PadAction.Input -> sheet.onKey(action.key)
            is PadAction.SetStatMode -> sheet.setMode(action.mode)
            is PadAction.Sheet -> when (action.command) {
                // Con i risultati a schermo le frecce riportano alla tabella senza spostarsi.
                SheetCommand.NEXT_CELL -> if (!showResults) sheet.next()
                SheetCommand.PREVIOUS_CELL -> if (!showResults) sheet.previous()
                SheetCommand.DELETE_ROW -> if (!showResults) sheet.deleteRow()
                SheetCommand.CLEAR_CELLS -> sheet.clearAll()
            }
            PadAction.Back -> {
                finish()
                return
            }
            else -> Unit
        }
        val outcome = sheet.outcome
        showResults = action == PadAction.Input(CalcKey.Equals) &&
            (outcome is StatOutcome.OneVariable || outcome is StatOutcome.Regression)
        render()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isMetaPressed) {
            if (keyCode != KeyEvent.KEYCODE_C) return super.onKeyDown(keyCode, event)
            copyResults()
            return true
        }
        val action = when (keyCode) {
            KeyEvent.KEYCODE_TAB -> PadAction.Sheet(
                if (event.isShiftPressed) SheetCommand.PREVIOUS_CELL else SheetCommand.NEXT_CELL,
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
        if (!showResults) return
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.statistics), resultView.text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- disegno

    private fun render() {
        val mode = sheet.mode
        modeButtons.zip(keys.statModes(mode)).forEach { (button, key) ->
            if (button.key != key) button.bind(key, palette)
        }
        if (tableShape != mode to sheet.rowCount) buildTable()

        val outcome = sheet.outcome
        val error = outcome as? StatOutcome.Error
        cellViews.forEachIndexed { r, cells ->
            cells.forEachIndexed { c, cell ->
                val expression = sheet.cell(r, c)
                val selected = r == sheet.row && c == sheet.column
                cell.text = if (expression.isEmpty()) "" else display.format(expression)
                cell.background = cellBackground(palette, selected, error = error?.row == r && error.column == c)
                cell.isSelected = selected
                cell.contentDescription = getString(
                    R.string.stat_cell_description, r + 1, columnName(c), cell.text.ifEmpty { "—" },
                )
            }
        }
        rowViews.getOrNull(sheet.row)?.let { row ->
            tableScroll.post {
                val top = row.top - dp(8)
                val bottom = row.bottom + dp(8)
                if (top < tableScroll.scrollY || bottom > tableScroll.scrollY + tableScroll.height) {
                    tableScroll.smoothScrollTo(0, if (top < tableScroll.scrollY) top else bottom - tableScroll.height)
                }
            }
        }

        val results = if (showResults) describe(outcome) else null
        tableScroll.visibility = if (results == null) View.VISIBLE else View.GONE
        resultsScroll.visibility = if (results == null) View.GONE else View.VISIBLE
        resultView.text = results?.joinToString("\n") { it.label }.orEmpty()
        storable = results.orEmpty()

        messageView.text = when {
            error != null -> getString(errorMessage(error.error))
            outcome == StatOutcome.NotEnoughData -> getString(
                if (mode == StatMode.REGRESSION) R.string.stat_not_enough_regression else R.string.stat_not_enough,
            )
            showResults && outcome is StatOutcome.Regression -> regressionLine(outcome.stats)
            showResults -> getString(R.string.stat_results_hint)
            else -> getString(R.string.stat_hint)
        }
        messageView.setTextColor(if (error != null || outcome == StatOutcome.NotEnoughData) palette.error else palette.onBackgroundVariant)
    }

    private fun columnName(c: Int) = when {
        c == 0 -> "x"
        sheet.mode == StatMode.FREQUENCY -> "f"
        else -> "y"
    }

    private fun value(x: BigDecimal) = solutions.value(x, asFraction = false)

    private fun describe(outcome: StatOutcome?): List<NamedValue>? = when (outcome) {
        is StatOutcome.OneVariable -> describeOne(outcome.stats)
        is StatOutcome.Regression -> describeRegression(outcome.stats)
        else -> null
    }

    private fun line(name: String, x: BigDecimal?) = NamedValue("$name = ${x?.let(::value) ?: "—"}", x ?: BigDecimal.ZERO)

    private fun describeOne(s: OneVariableStats): List<NamedValue> = listOfNotNull(
        line("n", BigDecimal(s.n)),
        line("x̄", s.mean),
        line("Σx", s.sum),
        line("Σx²", s.sumOfSquares),
        line("σx", s.populationSd),
        s.sampleSd?.let { line("sx", it) },
        line("min", s.min),
        line("Q₁", s.q1),
        line("Med", s.median),
        line("Q₃", s.q3),
        line("max", s.max),
        mode(s.modes),
    )

    /** Moda: uno o più valori ("Mo = 3; 5"), oppure "—" se tutti i valori sono ugualmente frequenti. */
    private fun mode(modes: List<BigDecimal>): NamedValue {
        val text = if (modes.isEmpty()) "—" else modes.take(MAX_MODES).joinToString("; ") { value(it) } +
            if (modes.size > MAX_MODES) "; …" else ""
        return NamedValue("Mo = $text", modes.firstOrNull() ?: BigDecimal.ZERO)
    }

    private fun describeRegression(s: RegressionStats): List<NamedValue> = listOfNotNull(
        line("a", s.a),
        line("b", s.b),
        s.r?.let { line("r", it) },
        line("n", BigDecimal(s.n)),
        line("x̄", s.meanX),
        line("ȳ", s.meanY),
        line("σx", s.sdX),
        line("σy", s.sdY),
    )

    /** La retta con i suoi valori, es. "y = 2,2 + 0,6x". */
    private fun regressionLine(s: RegressionStats): String {
        val slope = value(s.b.abs())
        val sign = if (s.b.signum() < 0) "−" else "+"
        return "y = ${value(s.a)} $sign ${slope}x"
    }

    // ---------------------------------------------------------------- layout

    private fun buildContent(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val width = if (!landscape && resources.configuration.screenWidthDp > 600) dp(600) else MATCH_PARENT
        root.addView(column, FrameLayout.LayoutParams(width, MATCH_PARENT, Gravity.CENTER_HORIZONTAL))

        val editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        editor.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        val (modeRow, buttons) = pad.row(keys.statModes(sheet.mode), dp(8), 15f)
        modeButtons = buttons
        editor.addView(modeRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply { topMargin = dp(4) })

        val content = FrameLayout(this)
        tableView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tableScroll = ScrollView(this).apply { addView(tableView) }
        content.addView(tableScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        resultView = TextView(this).apply {
            textSize = if (landscape) 18f else 22f
            gravity = Gravity.END
            setTextColor(palette.onBackground)
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            // Tocco: salva un risultato in una variabile; pressione prolungata: copia.
            setOnClickListener { showSaveToVariable(storable, settings, this@StatisticsActivity.display) }
            setOnLongClickListener {
                copyResults()
                true
            }
        }
        resultsScroll = ScrollView(this).apply { addView(resultView) }
        content.addView(resultsScroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        editor.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        messageView = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.END
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), dp(4), dp(8), 0)
        }
        editor.addView(messageView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val keypad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tools = FrameLayout(this).apply {
            background = roundedDrawable(palette.panel, dp(28).toFloat())
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val (toolRow, _) = pad.row(keys.statisticsTools(), dp(4), 18f)
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
        root.padForSystemBars(dp(16), dp(8))
        return root
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(pad.button(keys.backKey(), 16f), LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = TextView(this).apply {
            setText(R.string.statistics)
            textSize = 20f
            setTextColor(palette.onBackground)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(8), 0, dp(8), 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        bar.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        return bar
    }

    /** Tabella: intestazione, poi una riga per dato più la riga vuota per aggiungerne. */
    private fun buildTable() {
        tableView.removeAllViews()
        val mode = sheet.mode
        val spacing = dp(6)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(View(this), LinearLayout.LayoutParams(dp(INDEX_WIDTH), 1))
        for (c in 0 until mode.columns) {
            header.addView(
                TextView(this).apply {
                    text = columnName(c)
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(palette.onBackgroundVariant)
                    typeface = Typeface.create("serif", Typeface.ITALIC)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = spacing },
            )
        }
        tableView.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val cellHeight = dp(if (landscape) 38 else 44)
        val rows = ArrayList<LinearLayout>()
        val cells = ArrayList<List<TextView>>()
        for (r in 0 until sheet.rowCount) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                TextView(this).apply {
                    text = (r + 1).toString()
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(palette.onBackgroundVariant)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(INDEX_WIDTH), WRAP_CONTENT),
            )
            val rowCells = (0 until mode.columns).map { c ->
                TextView(this).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setPadding(dp(6), 0, dp(6), 0)
                    setTextColor(palette.onBackground)
                    setAutoSizeTextTypeUniformWithConfiguration(10, 20, 1, TypedValue.COMPLEX_UNIT_SP)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        sheet.select(r, c)
                        showResults = false
                        render()
                    }
                }.also { row.addView(it, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = spacing }) }
            }
            tableView.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, cellHeight).apply { topMargin = spacing })
            rows += row
            cells += rowCells
        }
        rowViews = rows
        cellViews = cells
        tableShape = mode to sheet.rowCount
    }

    // ---------------------------------------------------------------- salvataggio

    private fun restoreSheet(): StatisticsSheet {
        val json = prefs.getString(PREF_SHEET, null) ?: return StatisticsSheet()
        return runCatching {
            val saved = JSONObject(json)
            val savedData = saved.getJSONObject("data")
            val data = StatMode.entries.associateWith { mode ->
                val rows = savedData.optJSONArray(mode.name) ?: JSONArray()
                List(rows.length()) { r ->
                    val row = rows.getJSONArray(r)
                    List(row.length()) { row.getString(it) }
                }
            }
            StatisticsSheet(StatMode.valueOf(saved.getString("mode")), data)
        }.getOrElse { StatisticsSheet() }
    }

    private fun saveSheet() {
        val data = JSONObject()
        StatMode.entries.forEach { mode ->
            data.put(mode.name, JSONArray(sheet.rows(mode).map { JSONArray(it) }))
        }
        val saved = JSONObject().put("mode", sheet.mode.name).put("data", data)
        prefs.edit()
            .putString(PREF_SHEET, saved.toString())
            .putBoolean(PREF_SOLVED, showResults)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "statistics"
        const val PREF_SHEET = "sheet"
        const val PREF_SOLVED = "solved"
        const val INDEX_WIDTH = 32
        const val MAX_MODES = 5
    }
}
