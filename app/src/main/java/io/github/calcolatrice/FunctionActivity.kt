package io.github.calcolatrice

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
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
import io.github.calcolatrice.engine.AnalysisItem
import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalcError
import io.github.calcolatrice.engine.CalcException
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.DisplayFormatter
import io.github.calcolatrice.engine.EvalResult
import io.github.calcolatrice.engine.FunctionAnalysisResult
import io.github.calcolatrice.engine.FunctionCell
import io.github.calcolatrice.engine.FunctionSheet
import io.github.calcolatrice.engine.FunctionTab
import io.github.calcolatrice.engine.ResultFormatter
import io.github.calcolatrice.engine.SolutionFormatter
import io.github.calcolatrice.engine.UserFunction
import io.github.calcolatrice.ui.FitTextView
import io.github.calcolatrice.ui.GraphView
import io.github.calcolatrice.ui.HardwareKeys
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.KeyFactory
import io.github.calcolatrice.ui.KeyPad
import io.github.calcolatrice.ui.MathEntry
import io.github.calcolatrice.ui.NamedValue
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.Palette
import io.github.calcolatrice.ui.Settings
import io.github.calcolatrice.ui.SheetCommand
import io.github.calcolatrice.ui.cellBackground
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
import io.github.calcolatrice.ui.localFunctionNames
import io.github.calcolatrice.ui.mathEntries
import io.github.calcolatrice.ui.padForSystemBars
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.setupEdgeToEdge
import io.github.calcolatrice.ui.showMathMenu
import io.github.calcolatrice.ui.showSaveToVariable
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols

/**
 * Funzioni e grafici: si scrive f(X) e si sceglie tra tabella dei valori, grafico e analisi
 * (valore e derivata in un punto, integrale definito, zeri in un intervallo). Solo View di sistema.
 */
class FunctionActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var settings: Settings
    private lateinit var sheet: FunctionSheet
    private lateinit var palette: Palette
    private lateinit var keys: KeyFactory
    private lateinit var pad: KeyPad
    private lateinit var display: DisplayFormatter
    private lateinit var solutions: SolutionFormatter
    private var decimalSeparator = '.'
    private var landscape = false

    private var tabButtons: List<KeyButton> = emptyList()
    private lateinit var angleButton: KeyButton
    private lateinit var expressionView: FitTextView
    private val parameterViews = HashMap<FunctionCell, TextView>()

    private lateinit var tableBox: View
    private lateinit var tableMessage: TextView
    private lateinit var tableScroll: ScrollView
    private var tableRows: List<Pair<TextView, TextView>> = emptyList()

    private lateinit var graphBox: View
    private lateinit var graphView: GraphView
    private var graphExpression: String? = null
    private var graphAngle: AngleUnit? = null
    private var currentFunction: UserFunction? = null

    private lateinit var analysisBox: View
    private lateinit var analysisView: TextView
    private var analysis: Result<FunctionAnalysisResult>? = null
    private var analysisRunning = false

    /** Cresce a ogni modifica: un calcolo in background ormai superato non viene mostrato. */
    private var generation = 0
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
        savedInstanceState?.getString(KEY_SELECTED)
            ?.let { name -> FunctionCell.entries.firstOrNull { it.name == name } }
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

    override fun onDestroy() {
        super.onDestroy()
        generation++ // un calcolo ancora in corso non aggiornerà più lo schermo
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SELECTED, sheet.selected.name)
    }

    // ---------------------------------------------------------------- azioni

    private fun onAction(action: PadAction) {
        when (action) {
            is PadAction.Input -> if (action.key == CalcKey.Equals) {
                if (sheet.tab == FunctionTab.ANALYSIS) runAnalysis()
            } else {
                sheet.onKey(action.key)
                invalidateAnalysis()
            }
            is PadAction.SetFunctionTab -> sheet.setTab(action.tab)
            is PadAction.Sheet -> when (action.command) {
                SheetCommand.NEXT_CELL -> sheet.next()
                SheetCommand.PREVIOUS_CELL -> sheet.previous()
                SheetCommand.CLEAR_CELLS -> {
                    sheet.clearAll()
                    invalidateAnalysis()
                }
                SheetCommand.DELETE_ROW -> Unit
            }
            PadAction.ToggleAngle -> {
                sheet.angleUnit = if (sheet.angleUnit == AngleUnit.DEGREES) AngleUnit.RADIANS else AngleUnit.DEGREES
                invalidateAnalysis()
            }
            PadAction.ShowMath -> showMathMenu(mathMenu(), palette) { onAction(PadAction.Input(it)) }
            PadAction.Back -> {
                finish()
                return
            }
            else -> Unit
        }
        render()
    }

    /** Nella schermata funzioni il menu MATH contiene anche le funzioni che qui non hanno un tasto. */
    private fun mathMenu(): List<MathEntry> {
        fun describe(id: Int) = getString(id).replaceFirstChar { it.uppercase() }
        return listOf(
            MathEntry("log(x)", describe(R.string.key_log), CalcKey.Function("log")),
            MathEntry("eˣ", describe(R.string.key_exp_e), CalcKey.ExpE),
            MathEntry("e", describe(R.string.key_e), CalcKey.Constant('e')),
            MathEntry("sin⁻¹(x)", describe(R.string.key_asin), CalcKey.Function("asin")),
            MathEntry("cos⁻¹(x)", describe(R.string.key_acos), CalcKey.Function("acos")),
            MathEntry("tan⁻¹(x)", describe(R.string.key_atan), CalcKey.Function("atan")),
            MathEntry("x!", describe(R.string.key_factorial), CalcKey.Factorial),
        ) + mathEntries(display)
    }

    private fun invalidateAnalysis() {
        analysis = null
        analysisRunning = false
        generation++
    }

    /** Derivate, integrali e zeri possono richiedere qualche decimo di secondo: si calcolano in background. */
    private fun runAnalysis() {
        val current = ++generation
        analysisRunning = true
        val snapshot = FunctionSheet(sheet.cells(), FunctionTab.ANALYSIS, sheet.angleUnit)
        val variables = settings.variables
        Thread {
            // Qualunque errore imprevisto diventa un messaggio, mai una chiusura dell'app.
            val result = try {
                snapshot.analyze(variables)
            } catch (e: Exception) {
                Result.failure(CalcException(CalcError.UNDEFINED))
            } catch (e: StackOverflowError) {
                Result.failure(CalcException(CalcError.OVERFLOW))
            }
            runOnUiThread {
                if (current == generation && !isFinishing) {
                    analysis = result
                    analysisRunning = false
                    render()
                }
            }
        }.start()
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
            else -> hardwareKey(event.unicodeChar.toChar())?.let { PadAction.Input(it) }
        } ?: return super.onKeyDown(keyCode, event)
        onAction(action)
        return true
    }

    /** Qui la x è la variabile (per moltiplicare si usa *). */
    private fun hardwareKey(c: Char): CalcKey? = when (c) {
        'x', 'X' -> CalcKey.Variable('X')
        's' -> CalcKey.Function("sin")
        'c' -> CalcKey.Function("cos")
        't' -> CalcKey.Function("tan")
        'l' -> CalcKey.Function("ln")
        'g' -> CalcKey.Function("log")
        'e' -> CalcKey.Constant('e')
        ';' -> CalcKey.Separator
        else -> HardwareKeys.basic(c)
    }

    private fun copyResults() {
        val text = when (sheet.tab) {
            FunctionTab.ANALYSIS -> analysisView.text
            FunctionTab.TABLE -> tableRows.filter { (x, _) -> x.visibility == View.VISIBLE }
                .joinToString("\n") { (x, y) -> "${x.text}\t${y.text}" }
            FunctionTab.GRAPH -> return
        }
        if (text.isEmpty()) return
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.functions), text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- disegno

    private fun render() {
        val tab = sheet.tab
        tabButtons.zip(keys.functionTabs(tab)).forEach { (button, key) -> if (button.key != key) button.bind(key, palette) }
        keys.angleToggle(sheet.angleUnit).let { if (angleButton.key != it) angleButton.bind(it, palette) }

        val expression = sheet.expression
        val valid = expression.isNotEmpty() && sheet.function().isSuccess
        expressionView.text = if (expression.isEmpty()) getString(R.string.function_hint) else display.format(expression)
        expressionView.setTextColor(if (expression.isEmpty()) palette.onBackgroundVariant else palette.onBackground)
        expressionView.background = cellBackground(
            palette,
            selected = sheet.selected == FunctionCell.EXPRESSION,
            error = expression.isNotEmpty() && !valid,
        )
        expressionView.contentDescription = getString(R.string.function_cell_description, expressionView.text)

        for ((cell, view) in parameterViews) {
            val text = sheet.cell(cell)
            view.text = if (text.isEmpty()) "0" else display.format(text)
            val invalid = sheet.parameter(cell) is EvalResult.Failure
            view.background = cellBackground(palette, selected = sheet.selected == cell, error = invalid)
            view.contentDescription = getString(R.string.parameter_description, parameterName(cell), view.text)
        }

        tableBox.visibility = if (tab == FunctionTab.TABLE) View.VISIBLE else View.GONE
        graphBox.visibility = if (tab == FunctionTab.GRAPH) View.VISIBLE else View.GONE
        analysisBox.visibility = if (tab == FunctionTab.ANALYSIS) View.VISIBLE else View.GONE
        when (tab) {
            FunctionTab.TABLE -> renderTable()
            FunctionTab.GRAPH -> renderGraph()
            FunctionTab.ANALYSIS -> renderAnalysis()
        }
    }

    private fun parameterName(cell: FunctionCell): String = when (cell) {
        FunctionCell.TABLE_START -> getString(R.string.table_from)
        FunctionCell.TABLE_STEP -> getString(R.string.table_step)
        FunctionCell.POINT -> getString(R.string.analysis_point)
        FunctionCell.FROM -> getString(R.string.analysis_from)
        FunctionCell.TO -> getString(R.string.analysis_to)
        FunctionCell.EXPRESSION -> getString(R.string.function_label)
    }

    private fun number(x: BigDecimal) = solutions.value(x, asFraction = false)

    private fun failureText(error: Throwable?): String =
        if (sheet.expression.isEmpty()) {
            getString(R.string.function_hint)
        } else {
            getString(errorMessage((error as? CalcException)?.error ?: CalcError.SYNTAX))
        }

    private fun renderTable() {
        val result = sheet.table(variables = settings.variables)
        val rows = result.getOrNull()
        tableMessage.visibility = if (rows == null) View.VISIBLE else View.GONE
        tableScroll.visibility = if (rows == null) View.GONE else View.VISIBLE
        if (rows == null) {
            tableMessage.text = failureText(result.exceptionOrNull())
            return
        }
        tableRows.forEachIndexed { i, (xView, yView) ->
            val row = rows.getOrNull(i)
            xView.visibility = if (row == null) View.GONE else View.VISIBLE
            yView.visibility = xView.visibility
            if (row == null) return@forEachIndexed
            val (x, y) = row
            xView.text = number(x)
            when (y) {
                is EvalResult.Success -> {
                    yView.text = number(y.value)
                    yView.setTextColor(palette.onBackground)
                }
                is EvalResult.Failure -> {
                    yView.text = getString(R.string.undefined_short)
                    yView.setTextColor(palette.onBackgroundVariant)
                }
            }
        }
    }

    private fun renderGraph() {
        // Si ricompila solo quando cambiano la funzione o l'unità degli angoli.
        if (graphExpression == sheet.expression && graphAngle == sheet.angleUnit) return
        graphExpression = sheet.expression
        graphAngle = sheet.angleUnit
        currentFunction = sheet.function(settings.variables).getOrNull()
        graphView.showFunction(currentFunction?.fast())
    }

    /** Testo del punto toccato sul grafico, calcolato a 34 cifre e arrotondato per la lettura. */
    private fun traceText(x: Double): String? {
        val f = currentFunction ?: return null
        val xRounded = BigDecimal(x).round(MathContext(5, RoundingMode.HALF_EVEN))
        val y = when (val r = f.result(xRounded)) {
            is EvalResult.Success -> display.format(ResultFormatter.toInternal(r.value.round(MathContext(8, RoundingMode.HALF_EVEN))))
            is EvalResult.Failure -> getString(R.string.undefined_short)
        }
        return "x = ${display.format(ResultFormatter.toInternal(xRounded))}   y = $y"
    }

    private fun renderAnalysis() {
        val result = analysis
        storable = emptyList()
        analysisView.setTextColor(palette.onBackground)
        when {
            analysisRunning -> {
                analysisView.text = getString(R.string.analysis_running)
                analysisView.setTextColor(palette.onBackgroundVariant)
            }
            result == null -> {
                analysisView.text = getString(R.string.analysis_hint)
                analysisView.setTextColor(palette.onBackgroundVariant)
            }
            result.isFailure -> {
                analysisView.text = failureText(result.exceptionOrNull())
                analysisView.setTextColor(palette.error)
            }
            else -> showAnalysis(result.getOrThrow())
        }
    }

    private fun showAnalysis(r: FunctionAnalysisResult) {
        val lines = ArrayList<String>()
        val values = ArrayList<NamedValue>()
        fun add(name: String, item: AnalysisItem, undefinedText: String? = null) {
            when (item) {
                is AnalysisItem.Value -> {
                    val text = (if (item.approximate) "≈ " else "") + number(item.value)
                    lines += "$name = $text"
                    values += NamedValue("$name = $text", item.value)
                }
                is AnalysisItem.Error -> lines += "$name: " + (
                    if (item.error == CalcError.UNDEFINED && undefinedText != null) undefinedText else getString(errorMessage(item.error))
                    )
                is AnalysisItem.Values -> Unit
            }
        }
        add("f(x₀)", r.value)
        val derivativeUndefined = if (r.value is AnalysisItem.Value) getString(R.string.not_differentiable) else null
        add("f′(x₀)", r.derivative, derivativeUndefined)
        add("∫ₐᵇ f(x) dx", r.integral)
        when (val zeros = r.zeros) {
            is AnalysisItem.Values -> {
                lines += ""
                lines += getString(R.string.analysis_zeros) + ":"
                if (zeros.values.isEmpty()) {
                    lines += getString(R.string.analysis_no_zeros)
                } else {
                    zeros.values.forEach { x ->
                        lines += "x = ${number(x)}"
                        values += NamedValue("x = ${number(x)}", x)
                    }
                }
            }
            is AnalysisItem.Error -> lines += getString(R.string.analysis_zeros) + ": " + getString(errorMessage(zeros.error))
            is AnalysisItem.Value -> Unit
        }
        if ((r.integral as? AnalysisItem.Value)?.approximate == true) {
            lines += ""
            lines += getString(R.string.analysis_approximate)
        }
        analysisView.text = lines.joinToString("\n")
        storable = values
    }

    // ---------------------------------------------------------------- layout

    private fun buildContent(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val width = if (!landscape && resources.configuration.screenWidthDp > 600) dp(600) else MATCH_PARENT
        root.addView(column, FrameLayout.LayoutParams(width, MATCH_PARENT, Gravity.CENTER_HORIZONTAL))

        val editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        editor.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        val (tabRow, buttons) = pad.row(keys.functionTabs(sheet.tab), dp(6), 15f)
        tabButtons = buttons
        editor.addView(tabRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply { topMargin = dp(4) })
        editor.addView(buildExpressionRow(), LinearLayout.LayoutParams(MATCH_PARENT, dp(if (landscape) 48 else 56)).apply { topMargin = dp(8) })

        val content = FrameLayout(this)
        tableBox = buildTable()
        graphBox = buildGraph()
        analysisBox = buildAnalysis()
        content.addView(tableBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(graphBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(analysisBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        editor.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        val keypad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        keypad.addView(toolPanel(keys.functionTools()), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        keypad.addView(toolPanel(keys.functionTools2()), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })
        val (mainGrid, _) = pad.grid(
            keys.functionMain(decimalSeparator),
            dp(if (landscape) 6 else 8),
            if (landscape) 22f else 26f,
        )
        keypad.addView(mainGrid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        if (landscape) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(editor, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            row.addView(keypad, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(16) })
            column.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            column.addView(editor, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1.1f))
            column.addView(keypad, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        }
        root.padForSystemBars(dp(16), dp(8))
        return root
    }

    private fun toolPanel(keyList: List<io.github.calcolatrice.ui.PadKey>): View {
        val panel = FrameLayout(this).apply {
            background = roundedDrawable(palette.panel, dp(24).toFloat())
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val (row, _) = pad.row(keyList, dp(4), 17f)
        panel.addView(row, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return panel
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(pad.button(keys.backKey(), 16f), LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(
            TextView(this).apply {
                setText(R.string.functions)
                textSize = 20f
                setTextColor(palette.onBackground)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(8), 0, dp(8), 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        angleButton = pad.button(keys.angleToggle(sheet.angleUnit), 15f)
        bar.addView(angleButton, LinearLayout.LayoutParams(dp(64), dp(40)))
        return bar
    }

    private fun label(text: String, italic: Boolean = true) = TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(palette.onBackgroundVariant)
        typeface = Typeface.create("serif", if (italic) Typeface.ITALIC else Typeface.NORMAL)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun buildExpressionRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(getString(R.string.function_label)).apply { textSize = 18f }, LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT))
        expressionView = FitTextView(this).apply {
            setSizeRange(26f, 14f)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(12), 0, dp(12), 0)
            ellipsize = TextUtils.TruncateAt.START
            isClickable = true
            isFocusable = true
            setOnClickListener {
                sheet.select(FunctionCell.EXPRESSION)
                render()
            }
        }
        row.addView(expressionView, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(8) })
        return row
    }

    private fun parameterCell(cell: FunctionCell): TextView = TextView(this).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        textSize = 17f
        setTextColor(palette.onBackground)
        ellipsize = TextUtils.TruncateAt.END
        setPadding(dp(4), 0, dp(4), 0)
        isClickable = true
        isFocusable = true
        setOnClickListener {
            sheet.select(cell)
            render()
        }
        parameterViews[cell] = this
    }

    /** Riga di parametri: etichetta e casella per ognuno. */
    private fun parameterRow(cells: List<Pair<FunctionCell, String>>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cells.forEachIndexed { index, (cell, name) ->
            row.addView(label(name), LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply { if (index > 0) marginStart = dp(10) })
            row.addView(parameterCell(cell), LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(6) })
        }
        return row
    }

    private fun buildTable(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(
            parameterRow(
                listOf(
                    FunctionCell.TABLE_START to getString(R.string.table_from),
                    FunctionCell.TABLE_STEP to getString(R.string.table_step),
                ),
            ),
            LinearLayout.LayoutParams(MATCH_PARENT, dp(40)),
        )
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(label("x"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        header.addView(label("f(x)"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f))
        box.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tableRows = List(FunctionSheet.TABLE_ROWS) { i ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                if (i % 2 == 0) background = roundedDrawable(palette.panel, dp(8).toFloat())
                setPadding(0, dp(4), 0, dp(4))
            }
            fun cellText() = TextView(this).apply {
                textSize = if (landscape) 16f else 17f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(palette.onBackground)
            }
            val x = cellText()
            val y = cellText()
            row.addView(x, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(y, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f))
            list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            x to y
        }
        tableScroll = ScrollView(this).apply {
            addView(list)
            setOnLongClickListener {
                copyResults()
                true
            }
        }
        box.addView(tableScroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(4) })
        tableMessage = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(palette.onBackgroundVariant)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        box.addView(tableMessage, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return box
    }

    private fun buildGraph(): View {
        val box = FrameLayout(this)
        graphView = GraphView(this, palette).apply {
            decimalSeparator = this@FunctionActivity.decimalSeparator
            emptyMessage = getString(R.string.graph_empty)
            traceLabel = ::traceText
            background = roundedDrawable(palette.panel, dp(16).toFloat())
            clipToOutline = true
            onViewportChanged = { low, high ->
                contentDescription = getString(
                    R.string.graph_description,
                    this@FunctionActivity.display.format(ResultFormatter.toInternal(BigDecimal(low).round(MathContext(3)))),
                    this@FunctionActivity.display.format(ResultFormatter.toInternal(BigDecimal(high).round(MathContext(3)))),
                )
            }
            contentDescription = getString(R.string.graph_description, "−10", "10")
        }
        box.addView(graphView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        box.addView(
            TextView(this).apply {
                setText(R.string.graph_hint)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(palette.onBackgroundVariant)
                setPadding(dp(8), dp(4), dp(8), dp(6))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM),
        )
        return box
    }

    private fun buildAnalysis(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(
            parameterRow(
                listOf(
                    FunctionCell.POINT to getString(R.string.analysis_point),
                    FunctionCell.FROM to getString(R.string.analysis_from),
                    FunctionCell.TO to getString(R.string.analysis_to),
                ),
            ),
            LinearLayout.LayoutParams(MATCH_PARENT, dp(40)),
        )
        analysisView = TextView(this).apply {
            textSize = if (landscape) 17f else 19f
            gravity = Gravity.END
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            // Tocco: salva un risultato in una variabile; pressione prolungata: copia.
            setOnClickListener { showSaveToVariable(storable, settings, this@FunctionActivity.display) }
            setOnLongClickListener {
                copyResults()
                true
            }
        }
        box.addView(ScrollView(this).apply { addView(analysisView) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(4) })
        return box
    }

    // ---------------------------------------------------------------- salvataggio

    private fun restoreSheet(): FunctionSheet = runCatching {
        val saved = JSONObject(prefs.getString(PREF_SHEET, null) ?: return FunctionSheet())
        val cells = saved.optJSONObject("cells") ?: JSONObject()
        FunctionSheet(
            cells = FunctionCell.entries.filter { cells.has(it.name) }.associateWith { cells.getString(it.name) },
            tab = FunctionTab.entries.firstOrNull { it.name == saved.optString("tab") } ?: FunctionTab.TABLE,
            angleUnit = AngleUnit.entries.firstOrNull { it.name == saved.optString("angle") } ?: AngleUnit.RADIANS,
        )
    }.getOrElse { FunctionSheet() }

    private fun saveSheet() {
        val cells = JSONObject()
        sheet.cells().forEach { (cell, text) -> cells.put(cell.name, text) }
        val saved = JSONObject()
            .put("cells", cells)
            .put("tab", sheet.tab.name)
            .put("angle", sheet.angleUnit.name)
        prefs.edit().putString(PREF_SHEET, saved.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "functions"
        const val PREF_SHEET = "sheet"
        const val KEY_SELECTED = "selected"
    }
}
