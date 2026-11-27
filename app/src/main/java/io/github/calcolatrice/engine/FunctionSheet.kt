package io.github.calcolatrice.engine

import java.math.BigDecimal

/** Le viste della schermata Funzioni. */
enum class FunctionTab { TABLE, GRAPH, ANALYSIS, STUDY }

/** Caselle modificabili: le due funzioni (g facoltativa) e i parametri di tabella e analisi. */
enum class FunctionCell(val default: String) {
    EXPRESSION(""),
    SECOND(""),
    TABLE_START("-5"),
    TABLE_STEP("1"),
    POINT("1"),
    FROM("0"),
    TO("1"),
    STUDY_FROM("-10"),
    STUDY_TO("10"),
}

/** Un risultato dell'analisi: un valore (eventualmente approssimato) o un errore. */
sealed interface AnalysisItem {
    data class Value(val value: BigDecimal, val approximate: Boolean = false) : AnalysisItem
    data class Values(val values: List<BigDecimal>) : AnalysisItem
    /** Punti (x; y), per le intersezioni dei due grafici. */
    data class Points(val points: List<Pair<BigDecimal, BigDecimal>>) : AnalysisItem
    data class Error(val error: CalcError) : AnalysisItem
}

/**
 * Risultati della vista Analisi: f(x₀), f′(x₀), ∫ₐᵇ f(x) dx e zeri in [a, b]; con g, anche i punti
 * d'intersezione dei due grafici in [a, b] (null se g è vuota).
 */
data class FunctionAnalysisResult(
    val value: AnalysisItem,
    val derivative: AnalysisItem,
    val integral: AnalysisItem,
    val zeros: AnalysisItem,
    val intersections: AnalysisItem? = null,
    /** Limite di f per x → x₀ (o ±∞) nel verso scelto; null se non si è potuto calcolare (x₀ non valido). */
    val limit: LimitResult? = null,
)

/** Una riga della tabella: x, f(x) e, se c'è la seconda funzione, g(x). */
data class TableRow(val x: BigDecimal, val f: EvalResult, val g: EvalResult? = null)

/**
 * Stato della schermata Funzioni, indipendente da Android: le espressioni f(X) e g(X) (facoltativa),
 * i parametri di ogni vista e la casella selezionata. Ogni casella si modifica con le regole della calcolatrice.
 */
class FunctionSheet(
    cells: Map<FunctionCell, String> = emptyMap(),
    tab: FunctionTab = FunctionTab.TABLE,
    var angleUnit: AngleUnit = AngleUnit.RADIANS,
    /** Verso del limite nella vista Analisi. */
    var limitSide: LimitSide = LimitSide.BOTH,
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
    val secondExpression: String get() = cells.getValue(FunctionCell.SECOND)

    /** Caselle visibili nella vista corrente, nell'ordine in cui le frecce le percorrono. */
    val visibleCells: List<FunctionCell>
        get() = when (tab) {
            FunctionTab.TABLE -> FUNCTIONS + listOf(FunctionCell.TABLE_START, FunctionCell.TABLE_STEP)
            FunctionTab.GRAPH -> FUNCTIONS
            FunctionTab.ANALYSIS -> FUNCTIONS + listOf(FunctionCell.POINT, FunctionCell.FROM, FunctionCell.TO)
            FunctionTab.STUDY -> FUNCTIONS + listOf(FunctionCell.STUDY_FROM, FunctionCell.STUDY_TO)
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

    /** Applica un tasto alla casella selezionata. La X ha senso solo nelle funzioni. */
    fun onKey(key: CalcKey) {
        if (key == CalcKey.Equals) return
        if (key is CalcKey.Variable && key.name == 'X' && selected !in FUNCTIONS) return
        val current = cells.getValue(selected)
        cells[selected] = CalculatorEditor.onKey(CalculatorState(current), key, angleUnit).state.expression
    }

    /** Tocco sul verso del limite: x → x₀, x₀⁻, x₀⁺, +∞, −∞. */
    fun cycleLimitSide() {
        limitSide = LimitSide.entries[(limitSide.ordinal + 1) % LimitSide.entries.size]
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

    /** La seconda funzione g, se è stata scritta: null se vuota, l'errore se non è valida. */
    fun secondFunction(variables: Map<Char, BigDecimal> = emptyMap()): Result<UserFunction>? {
        if (secondExpression.isEmpty()) return null
        return try {
            Result.success(UserFunction(secondExpression, angleUnit, variables))
        } catch (e: CalcException) {
            Result.failure(e)
        }
    }

    /** Valore di un parametro (inizio, passo, x₀, a, b). */
    fun parameter(c: FunctionCell): EvalResult {
        val text = cells.getValue(c).ifEmpty { "0" }
        return evaluate(text, angleUnit)
    }

    /** Righe della tabella: un errore globale (funzioni o parametri non validi) oppure x, f(x) e g(x). */
    fun table(rows: Int = TABLE_ROWS, variables: Map<Char, BigDecimal> = emptyMap()): Result<List<TableRow>> {
        val f = function(variables).getOrElse { return Result.failure(it) }
        val g = secondFunction(variables)?.getOrElse { return Result.failure(it) }
        val start = (parameter(FunctionCell.TABLE_START) as? EvalResult.Success)?.value
            ?: return Result.failure(CalcException(CalcError.SYNTAX))
        val step = (parameter(FunctionCell.TABLE_STEP) as? EvalResult.Success)?.value
            ?: return Result.failure(CalcException(CalcError.SYNTAX))
        if (step.signum() == 0) return Result.failure(CalcException(CalcError.UNDEFINED))
        val fRows = FunctionAnalysis.table(f, start, step, rows)
        val gRows = g?.let { FunctionAnalysis.table(it, start, step, rows) }
        return Result.success(fRows.mapIndexed { i, (x, y) -> TableRow(x, y, gRows?.get(i)?.second) })
    }

    /** Calcola tutta la vista Analisi (può richiedere qualche decimo di secondo: va fatto fuori dal thread principale). */
    fun analyze(variables: Map<Char, BigDecimal> = emptyMap()): Result<FunctionAnalysisResult> {
        val f = function(variables).getOrElse { return Result.failure(it) }
        val g = secondFunction(variables)?.getOrElse { return Result.failure(it) }
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
                limit = try {
                    val x0 = if (limitSide == LimitSide.PLUS_INFINITY || limitSide == LimitSide.MINUS_INFINITY) {
                        BigDecimal.ZERO
                    } else {
                        number(FunctionCell.POINT)
                    }
                    FunctionLimits.limit(f, x0, limitSide)
                } catch (e: CalcException) {
                    null
                } catch (e: ArithmeticException) {
                    null
                },
                intersections = g?.let {
                    item {
                        // Le intersezioni sono gli zeri di f − g.
                        val difference = UserFunction("($expression)-($secondExpression)", angleUnit, variables)
                        val xs = FunctionAnalysis.zeros(difference, number(FunctionCell.FROM), number(FunctionCell.TO))
                        AnalysisItem.Points(xs.mapNotNull { x -> (f.atOrNull(x) ?: it.atOrNull(x))?.let { y -> x to y } })
                    }
                },
            ),
        )
    }

    /** Studio di f nell'intervallo scelto (qualche secondo: fuori dal thread principale). */
    fun study(variables: Map<Char, BigDecimal> = emptyMap()): Result<FunctionStudyResult> {
        val f = function(variables).getOrElse { return Result.failure(it) }
        val from = (parameter(FunctionCell.STUDY_FROM) as? EvalResult.Success)?.value
            ?: return Result.failure(CalcException(CalcError.SYNTAX))
        val to = (parameter(FunctionCell.STUDY_TO) as? EvalResult.Success)?.value
            ?: return Result.failure(CalcException(CalcError.SYNTAX))
        return try {
            Result.success(FunctionStudy.study(f, from, to))
        } catch (e: CalcException) {
            Result.failure(e)
        } catch (e: ArithmeticException) {
            Result.failure(CalcException(CalcError.OVERFLOW))
        }
    }

    companion object {
        const val TABLE_ROWS = 41

        /** Le caselle delle funzioni, presenti in tutte le viste. */
        val FUNCTIONS = listOf(FunctionCell.EXPRESSION, FunctionCell.SECOND)
    }
}
