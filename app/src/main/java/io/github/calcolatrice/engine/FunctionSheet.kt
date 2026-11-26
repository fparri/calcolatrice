package io.github.calcolatrice.engine

import java.math.BigDecimal

/** Le tre viste della schermata Funzioni. */
enum class FunctionTab { TABLE, GRAPH, ANALYSIS }

/** Caselle modificabili: la funzione e i parametri di tabella e analisi. */
enum class FunctionCell(val default: String) {
    EXPRESSION(""),
    TABLE_START("-5"),
    TABLE_STEP("1"),
    POINT("1"),
    FROM("0"),
    TO("1"),
}

/** Un risultato dell'analisi: un valore (eventualmente approssimato) o un errore. */
sealed interface AnalysisItem {
    data class Value(val value: BigDecimal, val approximate: Boolean = false) : AnalysisItem
    data class Values(val values: List<BigDecimal>) : AnalysisItem
    data class Error(val error: CalcError) : AnalysisItem
}

/** Risultati della vista Analisi: f(x₀), f′(x₀), ∫ₐᵇ f(x) dx e zeri in [a, b]. */
data class FunctionAnalysisResult(
    val value: AnalysisItem,
    val derivative: AnalysisItem,
    val integral: AnalysisItem,
    val zeros: AnalysisItem,
)

/**
 * Stato della schermata Funzioni, indipendente da Android: l'espressione f(X), i parametri
 * di ogni vista e la casella selezionata. Ogni casella si modifica con le regole della calcolatrice.
 */
class FunctionSheet(
    cells: Map<FunctionCell, String> = emptyMap(),
    tab: FunctionTab = FunctionTab.TABLE,
    var angleUnit: AngleUnit = AngleUnit.RADIANS,
) {
    private val cells: MutableMap<FunctionCell, String> =
        FunctionCell.entries.associateWith { cells[it] ?: it.default }.toMutableMap()

    var tab: FunctionTab = tab
        private set
    var selected: FunctionCell = FunctionCell.EXPRESSION
        private set

    fun cell(c: FunctionCell): String = cells.getValue(c)

    fun cells(): Map<FunctionCell, String> = cells.toMap()

    val expression: String get() = cells.getValue(FunctionCell.EXPRESSION)

    /** Caselle visibili nella vista corrente, nell'ordine in cui le frecce le percorrono. */
    val visibleCells: List<FunctionCell>
        get() = when (tab) {
            FunctionTab.TABLE -> listOf(FunctionCell.EXPRESSION, FunctionCell.TABLE_START, FunctionCell.TABLE_STEP)
            FunctionTab.GRAPH -> listOf(FunctionCell.EXPRESSION)
            FunctionTab.ANALYSIS -> listOf(FunctionCell.EXPRESSION, FunctionCell.POINT, FunctionCell.FROM, FunctionCell.TO)
        }

    fun setTab(t: FunctionTab) {
        tab = t
        if (selected !in visibleCells) selected = FunctionCell.EXPRESSION
    }

    fun select(c: FunctionCell) {
        if (c in visibleCells) selected = c
    }

    fun next() {
        val list = visibleCells
        selected = list[(list.indexOf(selected) + 1) % list.size]
    }

    fun previous() {
        val list = visibleCells
        selected = list[(list.indexOf(selected) - 1 + list.size) % list.size]
    }

    /** Applica un tasto alla casella selezionata. La X ha senso solo nella funzione. */
    fun onKey(key: CalcKey) {
        if (key == CalcKey.Equals) return
        if (key is CalcKey.Variable && key.name == 'X' && selected != FunctionCell.EXPRESSION) return
        val current = cells.getValue(selected)
        cells[selected] = CalculatorEditor.onKey(CalculatorState(current), key, angleUnit).state.expression
    }

    fun clearAll() {
        FunctionCell.entries.forEach { cells[it] = it.default }
        selected = FunctionCell.EXPRESSION
    }

    /** La funzione da studiare, oppure l'errore se l'espressione è vuota o non valida. */
    fun function(variables: Map<Char, BigDecimal> = emptyMap()): Result<UserFunction> {
        if (expression.isEmpty()) return Result.failure(CalcException(CalcError.SYNTAX))
        return try {
            Result.success(UserFunction(expression, angleUnit, variables))
        } catch (e: CalcException) {
            Result.failure(e)
        }
    }

    /** Valore di un parametro (inizio, passo, x₀, a, b). */
    fun parameter(c: FunctionCell): EvalResult {
        val text = cells.getValue(c).ifEmpty { "0" }
        return evaluate(text, angleUnit)
    }

    /** Righe della tabella: un errore globale (parametri non validi) oppure le coppie (x, f(x)). */
    fun table(rows: Int = TABLE_ROWS, variables: Map<Char, BigDecimal> = emptyMap()): Result<List<Pair<BigDecimal, EvalResult>>> {
        val f = function(variables).getOrElse { return Result.failure(it) }
        val start = (parameter(FunctionCell.TABLE_START) as? EvalResult.Success)?.value
            ?: return Result.failure(CalcException(CalcError.SYNTAX))
        val step = (parameter(FunctionCell.TABLE_STEP) as? EvalResult.Success)?.value
            ?: return Result.failure(CalcException(CalcError.SYNTAX))
        if (step.signum() == 0) return Result.failure(CalcException(CalcError.UNDEFINED))
        return Result.success(FunctionAnalysis.table(f, start, step, rows))
    }

    /** Calcola tutta la vista Analisi (può richiedere qualche decimo di secondo: va fatto fuori dal thread principale). */
    fun analyze(variables: Map<Char, BigDecimal> = emptyMap()): Result<FunctionAnalysisResult> {
        val f = function(variables).getOrElse { return Result.failure(it) }
        fun item(block: () -> AnalysisItem): AnalysisItem = try {
            block()
        } catch (e: CalcException) {
            AnalysisItem.Error(e.error)
        } catch (e: ArithmeticException) {
            AnalysisItem.Error(CalcError.OVERFLOW)
        }
        fun number(c: FunctionCell): BigDecimal = when (val r = parameter(c)) {
            is EvalResult.Success -> r.value
            is EvalResult.Failure -> throw CalcException(r.error)
        }
        return Result.success(
            FunctionAnalysisResult(
                value = item { AnalysisItem.Value(f.at(number(FunctionCell.POINT))) },
                derivative = item { AnalysisItem.Value(FunctionAnalysis.derivative(f, number(FunctionCell.POINT))) },
                integral = item {
                    val r = FunctionAnalysis.integral(f, number(FunctionCell.FROM), number(FunctionCell.TO))
                    AnalysisItem.Value(r.value, r.approximate)
                },
                zeros = item { AnalysisItem.Values(FunctionAnalysis.zeros(f, number(FunctionCell.FROM), number(FunctionCell.TO))) },
            ),
        )
    }

    companion object {
        const val TABLE_ROWS = 41
    }
}
