package io.github.calcolatrice

import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.calcolatrice.engine.AnalysisItem
import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalcError
import io.github.calcolatrice.engine.CalcException
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.EvalResult
import io.github.calcolatrice.engine.FunctionAnalysisResult
import io.github.calcolatrice.engine.FunctionCell
import io.github.calcolatrice.engine.FunctionSheet
import io.github.calcolatrice.engine.FunctionTab
import io.github.calcolatrice.engine.ResultFormatter
import io.github.calcolatrice.engine.UserFunction
import io.github.calcolatrice.ui.stackedFractions
import io.github.calcolatrice.engine.FunctionStudyResult
import io.github.calcolatrice.engine.LimitResult
import io.github.calcolatrice.engine.LimitSide
import io.github.calcolatrice.engine.Reliability
import io.github.calcolatrice.ui.Chip
import io.github.calcolatrice.ui.angleChip
import io.github.calcolatrice.ui.showAngle
import io.github.calcolatrice.ui.showFraction
import io.github.calcolatrice.ui.StudyText
import io.github.calcolatrice.ui.FitTextView
import io.github.calcolatrice.ui.GraphView
import io.github.calcolatrice.ui.HardwareKeys
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.MathEntry
import io.github.calcolatrice.ui.NamedValue
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.SheetCommand
import io.github.calcolatrice.ui.cellBackground
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
import io.github.calcolatrice.ui.mathEntries
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.showMathMenu
import io.github.calcolatrice.ui.showSaveToVariable
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Funzioni e grafici: si scrive f(X) (e, se serve, una seconda funzione g(X)) e si sceglie tra tabella
 * dei valori, grafico e analisi (valore e derivata in un punto, integrale definito, zeri e intersezioni
 * in un intervallo). Solo View di sistema.
 */
class FunctionActivity : SheetActivity() {

    override val prefsName = PREFS_NAME
    override val clipboardLabel = R.string.functions

    private lateinit var sheet: FunctionSheet

    private var tabButtons: List<KeyButton> = emptyList()
    private lateinit var angleChip: Chip
    private val functionViews = LinkedHashMap<FunctionCell, FitTextView>()
    private val parameterViews = HashMap<FunctionCell, TextView>()

    private lateinit var tableBox: View
    private lateinit var tableMessage: TextView
    private lateinit var tableScroll: ScrollView
    private var tableRows: List<Triple<TextView, TextView, TextView>> = emptyList()
    private lateinit var secondHeader: View

    private lateinit var graphBox: View
    private lateinit var graphView: GraphView
    private var graphKey: Triple<String, String, AngleUnit>? = null
    private var currentFunction: UserFunction? = null
    private var currentSecond: UserFunction? = null

    private lateinit var analysisBox: View
    private lateinit var limitChip: Chip
    private lateinit var analysisView: TextView
    private var analysis: Result<FunctionAnalysisResult>? = null
    private var analysisRunning = false

    private lateinit var studyBox: View
    private lateinit var studyView: TextView
    private var study: Result<FunctionStudyResult>? = null
    private var studyRunning = false

    /** Funzione e unità degli angoli dell'ultimo studio: i suoi punti si segnano sul grafico solo se non sono cambiate. */
    private var studyFor: Pair<String, AngleUnit>? = null

    /** Cresce a ogni modifica: un calcolo in background ormai superato non viene mostrato. */
    private var generation = 0
    private var storable: List<NamedValue> = emptyList()

    /** Risultati in forma esatta (frazioni, radici, π), come nelle equazioni. */
    private var asFraction = true
    private lateinit var fractionChip: Chip

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sheet = restoreSheet()
        asFraction = prefs.getBoolean(PREF_FRACTION, true)
        savedInstanceState?.getString(KEY_SELECTED)
            ?.let { name -> FunctionCell.entries.firstOrNull { it.name == name } }
            ?.let { sheet.select(it) }

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

    override fun onAction(action: PadAction) {
        when (action) {
            is PadAction.Input -> if (action.key == CalcKey.Equals) {
                when (sheet.tab) {
                    FunctionTab.ANALYSIS -> runAnalysis()
                    FunctionTab.STUDY -> runStudy()
                    else -> Unit
                }
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
            PadAction.ToggleFraction -> {
                asFraction = !asFraction
                prefs.edit().putBoolean(PREF_FRACTION, asFraction).apply()
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
        if (study != null) graphKey = null // i punti dello studio spariscono dal grafico
        study = null
        studyRunning = false
        studyFor = null
        generation++
    }

    /** Studio di funzione: qualche secondo di calcolo, in background come l'analisi. */
    private fun runStudy() {
        val current = ++generation
        studyRunning = true
        val snapshot = FunctionSheet(sheet.cells(), FunctionTab.STUDY, sheet.angleUnit)
        val key = sheet.expression to sheet.angleUnit
        val variables = settings.variables
        Thread {
            val result = try {
                snapshot.study(variables)
            } catch (e: Exception) {
                Result.failure(CalcException(CalcError.UNDEFINED))
            } catch (e: StackOverflowError) {
                Result.failure(CalcException(CalcError.OVERFLOW))
            }
            runOnUiThread {
                if (current == generation && !isFinishing) {
                    study = result
                    studyFor = key
                    studyRunning = false
                    graphKey = null // il grafico segna i punti trovati
                    render()
                }
            }
        }.start()
    }

    /** Derivate, integrali e zeri possono richiedere qualche decimo di secondo: si calcolano in background. */
    private fun runAnalysis() {
        val current = ++generation
        analysisRunning = true
        val snapshot = FunctionSheet(sheet.cells(), FunctionTab.ANALYSIS, sheet.angleUnit, sheet.limitSide)
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

    /** Qui la x è la variabile (per moltiplicare si usa *). */
    override fun characterKey(c: Char): CalcKey? = when (c) {
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

    override fun resultsText(): CharSequence? = when (sheet.tab) {
        FunctionTab.ANALYSIS -> analysisView.text
        FunctionTab.STUDY -> studyView.text
        FunctionTab.TABLE -> tableRows.filter { (x, _, _) -> x.visibility == View.VISIBLE }
            .joinToString("\n") { (x, y, g) -> listOfNotNull(x.text, y.text, g.text.takeIf { g.visibility == View.VISIBLE }).joinToString("\t") }
        FunctionTab.GRAPH -> null
    }

    // ---------------------------------------------------------------- disegno

    private fun render() {
        val tab = sheet.tab
        tabButtons.zip(keys.functionTabs(tab)).forEach { (button, key) -> if (button.key != key) button.bind(key, palette) }
        angleChip.showAngle(sheet.angleUnit)
        fractionChip.showFraction(asFraction)

        for ((cell, view) in functionViews) {
            val expression = sheet.cell(cell)
            val first = cell == FunctionCell.EXPRESSION
            val valid = expression.isEmpty() || (if (first) sheet.function() else sheet.secondFunction())?.isSuccess != false
            view.text = when {
                expression.isNotEmpty() -> display.format(expression)
                first -> getString(R.string.function_hint)
                else -> getString(R.string.second_function_hint)
            }
            view.setTextColor(if (expression.isEmpty()) palette.onBackgroundVariant else palette.onBackground)
            view.background = cellBackground(palette, selected = sheet.selected == cell, error = !valid)
            view.contentDescription = getString(
                if (first) R.string.function_cell_description else R.string.second_function_cell_description,
                spoken(view.text),
            )
        }

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
        studyBox.visibility = if (tab == FunctionTab.STUDY) View.VISIBLE else View.GONE
        when (tab) {
            FunctionTab.TABLE -> renderTable()
            FunctionTab.GRAPH -> renderGraph()
            FunctionTab.ANALYSIS -> renderAnalysis()
            FunctionTab.STUDY -> renderStudy()
        }
    }

    private fun parameterName(cell: FunctionCell): String = when (cell) {
        FunctionCell.TABLE_START -> getString(R.string.table_from)
        FunctionCell.TABLE_STEP -> getString(R.string.table_step)
        FunctionCell.POINT -> getString(R.string.analysis_point)
        FunctionCell.FROM -> getString(R.string.analysis_from)
        FunctionCell.TO -> getString(R.string.analysis_to)
        FunctionCell.EXPRESSION -> getString(R.string.function_label)
        FunctionCell.SECOND -> getString(R.string.second_function_label)
        FunctionCell.STUDY_FROM -> getString(R.string.analysis_from)
        FunctionCell.STUDY_TO -> getString(R.string.analysis_to)
    }

    private fun number(x: BigDecimal) = solutions.value(x, asFraction = false)

    /** Un risultato: in forma esatta se a/b è attivo e il valore ne ha una. */
    /** Frazioni su due livelli quando i risultati sono in forma esatta. */
    private fun stacked(text: String): CharSequence = if (asFraction) stackedFractions(text) else text

    private fun result(x: BigDecimal, approximate: Boolean = false) = solutions.value(x, asFraction && !approximate)

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
        val withSecond = sheet.secondExpression.isNotEmpty()
        secondHeader.visibility = if (withSecond) View.VISIBLE else View.GONE
        tableRows.forEachIndexed { i, (xView, yView, gView) ->
            val row = rows.getOrNull(i)
            xView.visibility = if (row == null) View.GONE else View.VISIBLE
            yView.visibility = xView.visibility
            gView.visibility = if (row != null && withSecond) View.VISIBLE else View.GONE
            if (row == null) return@forEachIndexed
            xView.text = number(row.x)
            showValue(yView, row.f)
            row.g?.let { showValue(gView, it) }
        }
    }

    private fun showValue(view: TextView, value: EvalResult) {
        when (value) {
            is EvalResult.Success -> {
                view.text = stacked(result(value.value))
                view.setTextColor(palette.onBackground)
            }
            is EvalResult.Failure -> {
                view.text = getString(R.string.undefined_short)
                view.setTextColor(palette.onBackgroundVariant)
            }
        }
        view.contentDescription = spoken(view.text)
    }

    private fun renderGraph() {
        // Si ricompila solo quando cambiano le funzioni o l'unità degli angoli.
        val key = Triple(sheet.expression, sheet.secondExpression, sheet.angleUnit)
        if (graphKey == key) return
        graphKey = key
        currentFunction = sheet.function(settings.variables).getOrNull()
        currentSecond = sheet.secondFunction(settings.variables)?.getOrNull()
        graphView.showFunctions(currentFunction?.fast(), currentSecond?.fast())
        graphView.marks = study?.getOrNull()?.takeIf { studyFor == (sheet.expression to sheet.angleUnit) }?.let { r ->
            GraphView.StudyMarks(
                extrema = (r.maxima + r.minima).map { it.x.toDouble() to it.y.toDouble() },
                inflections = r.inflections.map { it.x.toDouble() to it.y.toDouble() },
                zeros = r.zeros.map { it.toDouble() },
                verticals = r.vertical.map { it.toDouble() },
                lines = r.lines.map { Triple(it.m.toDouble(), it.q.toDouble(), it.side) },
            )
        }
    }

    /** Testo del punto toccato sul grafico, calcolato a 34 cifre e arrotondato per la lettura. */
    private fun traceText(x: Double): String? {
        val f = currentFunction ?: return null
        val xRounded = BigDecimal(x).round(MathContext(5, RoundingMode.HALF_EVEN))
        fun valueAt(function: UserFunction) = when (val r = function.result(xRounded)) {
            is EvalResult.Success -> display.format(ResultFormatter.toInternal(r.value.round(MathContext(8, RoundingMode.HALF_EVEN))))
            is EvalResult.Failure -> getString(R.string.undefined_short)
        }
        val xText = "x = ${display.format(ResultFormatter.toInternal(xRounded))}"
        val g = currentSecond ?: return "$xText   y = ${valueAt(f)}"
        return "$xText   f = ${valueAt(f)}   g = ${valueAt(g)}"
    }

    /** "x → 2⁻", "x → +∞"… per il chip e per il testo del limite. */
    private fun limitArrow(side: LimitSide): String {
        val point = sheet.cell(FunctionCell.POINT).ifEmpty { "0" }.let { display.format(it) }
        return when (side) {
            LimitSide.BOTH -> "x → $point"
            LimitSide.LEFT -> "x → $point⁻"
            LimitSide.RIGHT -> "x → $point⁺"
            LimitSide.PLUS_INFINITY -> "x → +∞"
            LimitSide.MINUS_INFINITY -> "x → −∞"
        }
    }

    /** Righe del limite, con "≈" e, se serve, "da verificare". */
    private fun limitLines(result: LimitResult?): List<String> {
        val name = "lim ${limitArrow(sheet.limitSide)} f(x)"
        fun mark(r: LimitResult) = if (r.reliability == Reliability.RISKY) "  (" + getString(R.string.study_risky) + ")" else ""
        fun text(r: LimitResult): String = when (r) {
            is LimitResult.Value -> "≈ " + result(r.value) + mark(r)
            is LimitResult.Infinite -> (if (r.sign > 0) "+∞" else "−∞") + mark(r)
            is LimitResult.Oscillating -> getString(R.string.limit_oscillates) + mark(r)
            is LimitResult.Undefined -> getString(R.string.limit_undefined)
            is LimitResult.Different -> getString(R.string.limit_different)
        }
        return when (result) {
            null -> listOf("$name: " + getString(R.string.error_syntax))
            is LimitResult.Different -> listOf(
                "lim ${limitArrow(LimitSide.LEFT)} f(x) = " + text(result.left),
                "lim ${limitArrow(LimitSide.RIGHT)} f(x) = " + text(result.right),
                getString(R.string.limit_different),
            )
            is LimitResult.Value, is LimitResult.Infinite -> listOf("$name = " + text(result))
            else -> listOf("$name: " + text(result))
        }
    }

    private fun renderAnalysis() {
        limitChip.update(limitArrow(sheet.limitSide), checked = false)
        limitChip.contentDescription = getString(R.string.limit_side_description, limitArrow(sheet.limitSide))
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

    /** "da verificare" nel colore degli errori, come nello studio di funzione. */
    private fun highlightRisky(text: CharSequence): CharSequence {
        val risky = getString(R.string.study_risky)
        var index = text.indexOf(risky)
        if (index < 0) return text
        val builder = android.text.SpannableStringBuilder(text)
        while (index >= 0) {
            builder.setSpan(
                android.text.style.ForegroundColorSpan(palette.error), index, index + risky.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            index = text.indexOf(risky, index + risky.length)
        }
        return builder
    }

    private fun renderStudy() {
        val result = study
        studyView.setTextColor(palette.onBackground)
        when {
            studyRunning -> {
                studyView.text = getString(R.string.study_running)
                studyView.setTextColor(palette.onBackgroundVariant)
            }
            result == null -> {
                studyView.text = getString(R.string.study_hint)
                studyView.setTextColor(palette.onBackgroundVariant)
            }
            result.isFailure -> {
                studyView.text = failureText(result.exceptionOrNull())
                studyView.setTextColor(palette.error)
            }
            else -> {
                val text = StudyText(this, palette) { result(it) }.build(result.getOrThrow())
                studyView.text = if (asFraction) stackedFractions(text) else text
                studyView.contentDescription = spoken(text)
            }
        }
    }

    private fun showAnalysis(r: FunctionAnalysisResult) {
        val lines = ArrayList<String>()
        val values = ArrayList<NamedValue>()
        fun add(name: String, item: AnalysisItem, undefinedText: String? = null) {
            when (item) {
                is AnalysisItem.Value -> {
                    val text = (if (item.approximate) "≈ " else "") + result(item.value, item.approximate)
                    lines += "$name = $text"
                    values += NamedValue("$name = $text", item.value)
                }
                is AnalysisItem.Error -> lines += "$name: " + (
                    if (item.error == CalcError.UNDEFINED && undefinedText != null) undefinedText else getString(errorMessage(item.error))
                    )
                is AnalysisItem.Values, is AnalysisItem.Points -> Unit
            }
        }
        add("f(x₀)", r.value)
        val derivativeUndefined = if (r.value is AnalysisItem.Value) getString(R.string.not_differentiable) else null
        add("f′(x₀)", r.derivative, derivativeUndefined)
        add("∫ₐᵇ f(x) dx", r.integral)
        when (val zeros = r.zeros) {
            is AnalysisItem.Values -> {
                lines += ""
                if (zeros.values.isEmpty()) {
                    lines += getString(R.string.analysis_zeros) + ": " + getString(R.string.analysis_no_zeros)
                } else {
                    lines += getString(R.string.analysis_zeros) + ":"
                    zeros.values.forEach { x ->
                        lines += "x = ${result(x)}"
                        values += NamedValue("x = ${result(x)}", x)
                    }
                }
            }
            is AnalysisItem.Error -> lines += getString(R.string.analysis_zeros) + ": " + getString(errorMessage(zeros.error))
            is AnalysisItem.Value, is AnalysisItem.Points -> Unit
        }
        when (val crossings = r.intersections) {
            is AnalysisItem.Points -> {
                lines += ""
                if (crossings.points.isEmpty()) {
                    lines += getString(R.string.analysis_intersections) + ": " + getString(R.string.analysis_no_intersections)
                } else {
                    lines += getString(R.string.analysis_intersections) + ":"
                    crossings.points.forEach { (x, y) ->
                        lines += "(${result(x)}; ${result(y)})"
                        values += NamedValue("x = ${result(x)}", x)
                    }
                }
            }
            is AnalysisItem.Error ->
                lines += getString(R.string.analysis_intersections) + ": " + getString(errorMessage(crossings.error))
            else -> Unit
        }
        lines += ""
        lines += limitLines(r.limit)
        if ((r.integral as? AnalysisItem.Value)?.approximate == true) {
            lines += ""
            lines += getString(R.string.analysis_approximate)
        }
        val text = lines.joinToString("\n")
        analysisView.text = highlightRisky(stacked(text))
        analysisView.contentDescription = spoken(text)
        storable = values
    }

    // ---------------------------------------------------------------- layout

    private fun buildContent(): View {
        val editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        angleChip = angleChip(this, palette) { onAction(PadAction.ToggleAngle) }
        fractionChip = fractionChip()
        editor.addView(
            topBar(titleView(getString(R.string.functions)), fractionChip, angleChip),
            LinearLayout.LayoutParams(MATCH_PARENT, dp(56)),
        )
        val (tabRow, buttons) = pad.row(keys.functionTabs(sheet.tab), dp(6), 15f)
        tabButtons = buttons
        editor.addView(tabRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply { topMargin = dp(4) })
        val rowHeight = dp(if (landscape) 40 else 44)
        editor.addView(
            buildExpressionRow(FunctionCell.EXPRESSION, getString(R.string.function_label)),
            LinearLayout.LayoutParams(MATCH_PARENT, rowHeight).apply { topMargin = dp(8) },
        )
        editor.addView(
            buildExpressionRow(FunctionCell.SECOND, getString(R.string.second_function_label)),
            LinearLayout.LayoutParams(MATCH_PARENT, rowHeight).apply { topMargin = dp(6) },
        )

        val content = FrameLayout(this)
        tableBox = buildTable()
        graphBox = buildGraph()
        analysisBox = buildAnalysis()
        studyBox = buildStudy()
        content.addView(tableBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(graphBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(analysisBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        content.addView(studyBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        editor.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })

        val keypad = keypad(listOf(keys.functionTools(), keys.functionTools2()), keys.functionMain(decimalSeparator), 24f)
        return scaffold(editor, keypad, EDITOR_WEIGHT)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(palette.onBackgroundVariant)
        // Il serif corsivo non ha i pedici (x₀): lì si usa il carattere di sistema.
        val family = if (text.any { it in '₀'..'₉' }) "sans-serif" else "serif"
        typeface = Typeface.create(family, Typeface.ITALIC)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun buildExpressionRow(cell: FunctionCell, name: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(name).apply { textSize = 18f }, LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT))
        val view = FitTextView(this).apply {
            setSizeRange(24f, 14f)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(12), 0, dp(12), 0)
            ellipsize = TextUtils.TruncateAt.START
            isClickable = true
            isFocusable = true
            setOnClickListener {
                sheet.select(cell)
                render()
            }
        }
        functionViews[cell] = view
        row.addView(view, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(8) })
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
        secondHeader = label("g(x)")
        header.addView(secondHeader, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f))
        box.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tableRows = List(FunctionSheet.TABLE_ROWS) { i ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                if (i % 2 == 0) background = roundedDrawable(palette.panel, dp(12).toFloat())
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
            val g = cellText()
            row.addView(x, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(y, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f))
            row.addView(g, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f))
            list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            Triple(x, y, g)
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
            hint = getString(R.string.graph_hint)
            traceLabel = ::traceText
            background = roundedDrawable(palette.panel, dp(28).toFloat())
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
        // Verso del limite: un tocco passa al successivo (x → x₀, x₀⁻, x₀⁺, +∞, −∞).
        val limitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        limitRow.addView(label(getString(R.string.limit_label)), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        limitChip = Chip(this, palette, checkable = false).apply {
            setOnClickListener {
                sheet.cycleLimitSide()
                invalidateAnalysis()
                render()
            }
        }
        limitRow.addView(limitChip, LinearLayout.LayoutParams(WRAP_CONTENT, dp(32)).apply { marginStart = dp(8) })
        box.addView(limitRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(6) })
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

    private fun buildStudy(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(
            parameterRow(
                listOf(
                    FunctionCell.STUDY_FROM to getString(R.string.analysis_from),
                    FunctionCell.STUDY_TO to getString(R.string.analysis_to),
                ),
            ),
            LinearLayout.LayoutParams(MATCH_PARENT, dp(40)),
        )
        studyView = TextView(this).apply {
            textSize = if (landscape) 15f else 16f
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnLongClickListener {
                copyResults()
                true
            }
        }
        val scroll = ScrollView(this).apply {
            addView(studyView)
            background = roundedDrawable(palette.panel, dp(28).toFloat())
            clipToOutline = true
        }
        box.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(6) })
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
            limitSide = LimitSide.entries.firstOrNull { it.name == saved.optString("limit") } ?: LimitSide.BOTH,
        )
    }.getOrElse { FunctionSheet() }

    private fun saveSheet() {
        val cells = JSONObject()
        sheet.cells().forEach { (cell, text) -> cells.put(cell.name, text) }
        val saved = JSONObject()
            .put("cells", cells)
            .put("tab", sheet.tab.name)
            .put("angle", sheet.angleUnit.name)
            .put("limit", sheet.limitSide.name)
        prefs.edit().putString(PREF_SHEET, saved.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "functions"
        const val PREF_SHEET = "sheet"
        const val KEY_SELECTED = "selected"
        const val PREF_FRACTION = "fractions"

        /** In verticale tabella e grafico hanno più spazio della tastiera, che qui ha due righe di strumenti. */
        const val EDITOR_WEIGHT = 1.45f
    }
}
