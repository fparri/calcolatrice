package io.github.calcolatrice

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.calcolatrice.engine.CubicSolution
import io.github.calcolatrice.engine.EquationMode
import io.github.calcolatrice.engine.EquationOutcome
import io.github.calcolatrice.engine.EquationSheet
import io.github.calcolatrice.engine.InequalityText
import io.github.calcolatrice.engine.LinearSolution
import io.github.calcolatrice.engine.QuadraticSolution
import io.github.calcolatrice.engine.Relation
import io.github.calcolatrice.engine.SolutionSet
import io.github.calcolatrice.engine.Vertex
import io.github.calcolatrice.ui.stackedFractions
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.NamedValue
import io.github.calcolatrice.ui.SheetCommand
import io.github.calcolatrice.ui.cellBackground
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.showSaveToVariable
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Equazioni e sistemi, come la modalità EQN delle calcolatrici scientifiche: si sceglie il tipo
 * di problema, si scrivono i coefficienti nella tabella e "=" risolve. Ogni coefficiente può essere
 * un'espressione (1/3, √2, −π…). Solo View di sistema, nessuna libreria.
 */
class EquationActivity : SheetActivity() {

    override val prefsName = PREFS_NAME
    override val clipboardLabel = R.string.equations

    private lateinit var sheet: EquationSheet
    private var asFraction = true

    private lateinit var titleView: TextView
    private lateinit var fractionButton: KeyButton
    private var modeButtons: List<KeyButton> = emptyList()
    private lateinit var gridView: LinearLayout
    private var cellViews: List<TextView> = emptyList()
    /** Segni delle disequazioni (> ≥ < ≤), uno per riga, solo nella modalità disequazioni. */
    private var relationViews: List<TextView> = emptyList()
    private var gridMode: EquationMode? = null
    private lateinit var messageView: TextView
    private lateinit var resultView: TextView
    private lateinit var noteView: TextView

    /**
     * Testo dei risultati: messaggio, righe principali, nota (il vertice della parabola)
     * e i valori reali che si possono salvare in una variabile.
     */
    private data class Description(
        val message: String?,
        val lines: List<String>,
        val note: String? = null,
        val values: List<NamedValue> = emptyList(),
    )

    /** Valori dell'ultimo risultato mostrato, per "salva in una variabile". */
    private var storable: List<NamedValue> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        asFraction = prefs.getBoolean(PREF_FRACTION, true)

        sheet = restoreSheet()
        if (prefs.getBoolean(PREF_SOLVED, false)) sheet.solve()
        savedInstanceState?.getInt(KEY_SELECTED, -1)
            ?.takeIf { it in 0 until sheet.mode.cellCount }
            ?.let { sheet.select(it) }

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

    override fun onAction(action: PadAction) {
        when (action) {
            is PadAction.Input -> sheet.onKey(action.key)
            is PadAction.SetEquationMode -> sheet.setMode(action.mode)
            is PadAction.Sheet -> when (action.command) {
                SheetCommand.NEXT_CELL -> sheet.next()
                SheetCommand.PREVIOUS_CELL -> sheet.previous()
                SheetCommand.CLEAR_CELLS -> sheet.clearAll()
                SheetCommand.DELETE_ROW -> Unit
            }
            PadAction.ToggleFraction -> {
                asFraction = !asFraction
                prefs.edit().putBoolean(PREF_FRACTION, asFraction).apply()
            }
            PadAction.Back -> {
                finish()
                return
            }
            else -> Unit
        }
        render()
    }

    override fun resultsText(): CharSequence? {
        if (sheet.outcome == null) return null
        return listOf(messageView, resultView, noteView)
            .filter { it.visibility == View.VISIBLE }
            .joinToString("\n") { it.text }
    }

    // ---------------------------------------------------------------- disegno

    private fun render() {
        val mode = sheet.mode
        if (gridMode != mode) buildGrid(mode)

        titleView.text = formula(mode)
        modeButtons.zip(keys.equationModes(mode)).forEach { (button, key) ->
            if (button.key != key) button.bind(key, palette)
        }
        keys.fractionKey(asFraction).let { if (fractionButton.key != it) fractionButton.bind(it, palette) }

        val outcome = sheet.outcome
        val errorCell = (outcome as? EquationOutcome.Error)?.cell
        val expressions = sheet.expressions()
        // Nelle disequazioni la seconda riga è facoltativa: vuota, non conta e resta in disparte.
        val unusedRow = if (mode == EquationMode.INEQUALITY && expressions.subList(3, 6).all { it.isEmpty() }) 1 else -1
        cellViews.forEachIndexed { index, cell ->
            val expression = expressions[index]
            val unused = index / mode.coefficients == unusedRow
            val text = when {
                unused -> ""
                expression.isEmpty() -> "0"
                else -> display.format(expression)
            }
            val selected = index == sheet.selected
            cell.text = text
            cell.setTextColor(if (expression.isEmpty()) palette.onBackgroundVariant else palette.onBackground)
            cell.background = cellBackground(palette, selected, error = index == errorCell)
            cell.isSelected = selected
            cell.contentDescription = getString(
                R.string.cell_description,
                index / mode.coefficients + 1,
                LETTERS[index % mode.coefficients].toString(),
                text,
            )
        }

        relationViews.forEachIndexed { row, view ->
            val relation = sheet.relation(row)
            view.text = relation.symbol
            view.alpha = if (row == unusedRow) UNUSED_ALPHA else 1f
            view.contentDescription = getString(R.string.relation_description, row + 1, relationName(relation))
        }

        val description = describe(outcome)
        messageView.text = description.message.orEmpty()
        messageView.visibility = if (description.message == null) View.GONE else View.VISIBLE
        messageView.setTextColor(if (outcome is EquationOutcome.Error) palette.error else palette.onBackgroundVariant)
        val results = description.lines.joinToString("\n")
        resultView.text = if (asFraction) stackedFractions(results) else results
        resultView.visibility = if (description.lines.isEmpty()) View.GONE else View.VISIBLE
        noteView.text = description.note.orEmpty()
        noteView.visibility = if (description.note == null) View.GONE else View.VISIBLE
        storable = description.values
    }

    private fun relationName(relation: Relation): String = getString(
        when (relation) {
            Relation.GREATER -> R.string.relation_greater
            Relation.GREATER_EQUAL -> R.string.relation_greater_equal
            Relation.LESS -> R.string.relation_less
            Relation.LESS_EQUAL -> R.string.relation_less_equal
        },
    )

    private fun describe(outcome: EquationOutcome?): Description = when (outcome) {
        null -> Description(
            getString(if (sheet.mode == EquationMode.INEQUALITY) R.string.inequality_hint else R.string.solve_hint),
            emptyList(),
        )
        is EquationOutcome.Inequality -> describeInequality(outcome.solution, outcome.system)
        is EquationOutcome.Error -> Description(getString(errorMessage(outcome.error)), emptyList())
        is EquationOutcome.System -> describeLinear(outcome.solution, listOf("x", "y", "z"))
        is EquationOutcome.Quadratic -> describeQuadratic(outcome.solution)
        is EquationOutcome.Cubic -> describeCubic(outcome.solution)
    }

    /** Soluzioni in forma algebrica e, sotto, come intervalli; gli estremi si possono salvare. */
    private fun describeInequality(solution: SolutionSet, system: Boolean): Description {
        val italian = resources.configuration.locales[0].language == "it"
        val text = InequalityText(::value, italianIntervals = italian)
        val title = if (system) getString(R.string.inequality_system) else null
        return when {
            solution.isEmpty -> Description(listOfNotNull(title, getString(R.string.no_solution)).joinToString("\n"), emptyList())
            solution.isAll -> Description(
                listOfNotNull(title, getString(R.string.all_real_solutions)).joinToString("\n"),
                listOf("∀x ∈ ℝ"),
            )
            else -> {
                val bounds = solution.intervals
                    .flatMap { listOfNotNull(it.low?.value, it.high?.value) }
                    .distinctBy { it.stripTrailingZeros() }
                Description(
                    title,
                    listOf(text.algebraic(solution).orEmpty()),
                    text.intervals(solution),
                    bounds.map { NamedValue("x = ${value(it)}", it) },
                )
            }
        }
    }

    private fun describeLinear(solution: LinearSolution, names: List<String>): Description = when (solution) {
        is LinearSolution.Unique -> named(solution.values.mapIndexed { i, v -> names[i] to v }, null)
        LinearSolution.None -> Description(getString(R.string.no_solution), emptyList())
        LinearSolution.Infinite -> Description(getString(R.string.infinite_solutions), emptyList())
    }

    private fun describeQuadratic(solution: QuadraticSolution): Description = when (solution) {
        is QuadraticSolution.TwoReal ->
            named(listOf("x₁" to solution.x1, "x₂" to solution.x2), null, vertex(solution.vertex))
        is QuadraticSolution.DoubleRoot ->
            named(listOf("x" to solution.x), getString(R.string.double_root), vertex(solution.vertex))
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
            linear.copy(message = listOfNotNull(getString(R.string.linear_equation), linear.message).joinToString("\n"))
        }
    }

    private fun describeCubic(solution: CubicSolution): Description = when (solution) {
        is CubicSolution.Quadratic -> {
            val quadratic = describeQuadratic(solution.solution)
            quadratic.copy(
                message = listOfNotNull(getString(R.string.quadratic_equation), quadratic.message).joinToString("\n"),
            )
        }
        is CubicSolution.Roots -> {
            val complex = solution.complex
            if (complex == null) {
                named(solution.real.mapIndexed { i, x -> "x${SUBSCRIPTS[i]}" to x }, null)
            } else {
                val (re, im) = complex
                val real = named(listOf("x₁" to solution.real[0]), null)
                real.copy(
                    lines = real.lines + listOf(
                        "x₂ = " + solutions.complex(re, im, plus = true, asFraction = asFraction),
                        "x₃ = " + solutions.complex(re, im, plus = false, asFraction = asFraction),
                    ),
                )
            }
        }
    }

    /** Righe "nome = valore", con i valori salvabili in una variabile. */
    private fun named(values: List<Pair<String, BigDecimal>>, message: String?, note: String? = null) = Description(
        message,
        values.map { (name, x) -> "$name = ${value(x)}" },
        note,
        values.map { (name, x) -> NamedValue("$name = ${value(x)}", x) },
    )

    private fun value(x: BigDecimal) = solutions.value(x, asFraction)

    private fun vertex(v: Vertex) = getString(
        if (v.isMinimum) R.string.vertex_minimum else R.string.vertex_maximum,
        value(v.x),
        value(v.y),
    )

    private fun formula(mode: EquationMode): String = when (mode) {
        EquationMode.SYSTEM_2 -> getString(R.string.formula_system_2)
        EquationMode.SYSTEM_3 -> getString(R.string.formula_system_3)
        EquationMode.QUADRATIC -> getString(R.string.formula_quadratic)
        EquationMode.CUBIC -> getString(R.string.formula_cubic)
        EquationMode.INEQUALITY -> getString(R.string.formula_inequality, sheet.relation(0).symbol)
    }

    // ---------------------------------------------------------------- layout

    private fun buildContent(): View {
        val editor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleView = titleView()
        fractionButton = pad.button(keys.fractionKey(asFraction), 16f)
        editor.addView(topBar(titleView, fractionButton), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        val (modeRow, buttons) = pad.row(keys.equationModes(sheet.mode), dp(6), 14f)
        modeButtons = buttons
        editor.addView(modeRow, LinearLayout.LayoutParams(MATCH_PARENT, dp(40)).apply { topMargin = dp(4) })
        gridView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        editor.addView(gridView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
        editor.addView(buildResults(), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(4) })
        return scaffold(editor, keypad(listOf(keys.equationTools()), keys.equationMain(decimalSeparator)), EDITOR_WEIGHT)
    }

    private fun buildResults(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(dp(8), dp(8), dp(8), dp(8))
            // Tocco: salva un risultato in una variabile; pressione prolungata: copia i risultati.
            setOnClickListener { showSaveToVariable(storable, settings, this@EquationActivity.display) }
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
        val inequality = mode == EquationMode.INEQUALITY
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
        if (inequality) header.addView(View(this), LinearLayout.LayoutParams(0, 1, RELATION_WEIGHT).apply { marginStart = spacing })
        gridView.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val cellHeight = dp(if (landscape) 38 else 44)
        val cells = ArrayList<TextView>(mode.cellCount)
        val relations = ArrayList<TextView>()
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
            if (inequality) {
                // Il segno della disequazione: un tocco passa al successivo (> ≥ < ≤).
                val relation = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 22f
                    setTextColor(palette.accent)
                    background = roundedDrawable(palette.panel, dp(12).toFloat())
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        sheet.cycleRelation(r)
                        render()
                    }
                }
                relations += relation
                row.addView(relation, LinearLayout.LayoutParams(0, MATCH_PARENT, RELATION_WEIGHT).apply { marginStart = spacing })
            }
            gridView.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, cellHeight).apply { topMargin = spacing })
        }
        cellViews = cells
        relationViews = relations
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
            val savedRelations = saved.optJSONArray("relations") ?: JSONArray()
            val relations = List(savedRelations.length()) { i ->
                Relation.entries.firstOrNull { it.name == savedRelations.optString(i) } ?: Relation.GREATER
            }
            val mode = EquationMode.entries.firstOrNull { it.name == saved.getString("mode") } ?: EquationMode.SYSTEM_2
            EquationSheet(mode, cells, relations)
        }.getOrElse { EquationSheet() }
    }

    private fun saveSheet() {
        val cells = JSONObject()
        EquationMode.entries.forEach { mode -> cells.put(mode.name, JSONArray(sheet.expressions(mode))) }
        val saved = JSONObject()
            .put("mode", sheet.mode.name)
            .put("cells", cells)
            .put("relations", JSONArray(sheet.relations().map { it.name }))
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
        const val SUBSCRIPTS = "₁₂₃"
        const val RELATION_WEIGHT = 0.6f
        const val UNUSED_ALPHA = 0.4f

        /** In verticale la parte con tabella e risultati è un po' più alta della tastiera. */
        const val EDITOR_WEIGHT = 1.15f
    }
}
