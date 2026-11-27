package io.github.calcolatrice

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.OneVariableStats
import io.github.calcolatrice.engine.RegressionStats
import io.github.calcolatrice.engine.StatMode
import io.github.calcolatrice.engine.StatOutcome
import io.github.calcolatrice.engine.StatisticsSheet
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.NamedValue
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.SheetCommand
import io.github.calcolatrice.ui.cellBackground
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
import io.github.calcolatrice.ui.showSaveToVariable
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Statistica come la modalità STAT delle calcolatrici: una colonna di dati (media, deviazione
 * standard, quartili…) oppure coppie (x, y) per la retta di regressione. Solo View di sistema.
 */
class StatisticsActivity : SheetActivity() {

    override val prefsName = PREFS_NAME
    override val clipboardLabel = R.string.statistics

    private lateinit var sheet: StatisticsSheet

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
        sheet = restoreSheet()
        if (prefs.getBoolean(PREF_SOLVED, false)) {
            sheet.solve()
            showResults = true
        }
        setContentView(buildContent())
        render()
    }

    override fun onPause() {
        super.onPause()
        saveSheet()
    }

    // ---------------------------------------------------------------- azioni

    override fun onAction(action: PadAction) {
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

    override fun resultsText(): CharSequence? = if (showResults) resultView.text else null

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
        resultView.contentDescription = spoken(resultView.text)
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
        val editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        editor.addView(topBar(titleView(getString(R.string.statistics))), LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
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
            setPadding(dp(20), dp(12), dp(20), dp(12))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            // Tocco: salva un risultato in una variabile; pressione prolungata: copia.
            setOnClickListener { showSaveToVariable(storable, settings, this@StatisticsActivity.display) }
            setOnLongClickListener {
                copyResults()
                true
            }
        }
        // Scheda dei risultati, come nelle equazioni.
        resultsScroll = ScrollView(this).apply {
            addView(resultView)
            background = roundedDrawable(palette.panel, dp(28).toFloat())
            clipToOutline = true
        }
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

        return scaffold(editor, keypad(listOf(keys.statisticsTools()), keys.equationMain(decimalSeparator)), EDITOR_WEIGHT)
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

        /** In verticale tabella e risultati hanno un po' più spazio della tastiera. */
        const val EDITOR_WEIGHT = 1.15f
        const val MAX_MODES = 5
    }
}
