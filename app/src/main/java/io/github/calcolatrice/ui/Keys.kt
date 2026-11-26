package io.github.calcolatrice.ui

import android.content.Context
import io.github.calcolatrice.R
import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.EquationMode
import io.github.calcolatrice.engine.FunctionTab
import io.github.calcolatrice.engine.StatMode

/** Azioni dei tasti: input per l'editor oppure comandi dell'interfaccia. */
sealed interface PadAction {
    data class Input(val key: CalcKey) : PadAction
    data object ToggleInverse : PadAction
    data object ToggleAngle : PadAction
    data object ToggleScientific : PadAction
    data object ShowHistory : PadAction
    data object ShowMenu : PadAction
    data object ShowVariables : PadAction
    data object ShowMath : PadAction
    data object Solve : PadAction
    data object MemoryAdd : PadAction
    data object MemorySubtract : PadAction
    data object ToggleFraction : PadAction
    data object Back : PadAction
    data class SetEquationMode(val mode: EquationMode) : PadAction
    data class SetStatMode(val mode: StatMode) : PadAction
    data class SetFunctionTab(val tab: FunctionTab) : PadAction
    data class Sheet(val command: SheetCommand) : PadAction
}

/** Comandi delle tabelle (equazioni e statistica). */
enum class SheetCommand { NEXT_CELL, PREVIOUS_CELL, CLEAR_CELLS, DELETE_ROW }

enum class KeyStyle { Number, Operator, Clear, Equals, Function, Toggle }

data class PadKey(
    val label: String,
    val action: PadAction,
    val style: KeyStyle,
    /** Testo letto da TalkBack; se null viene letta l'etichetta. */
    val description: String? = null,
    val icon: Int? = null,
    val active: Boolean = false,
    val longPressAction: PadAction? = null,
)

/** Definizione di tutti i tasti, con le descrizioni per l'accessibilità nella lingua del telefono. */
class KeyFactory(private val context: Context) {

    private fun s(id: Int) = context.getString(id)
    private fun input(key: CalcKey) = PadAction.Input(key)
    private fun digit(d: Char) = PadKey(d.toString(), input(CalcKey.Digit(d)), KeyStyle.Number)
    private fun op(label: String, symbol: Char, description: Int) =
        PadKey(label, input(CalcKey.Operator(symbol)), KeyStyle.Operator, s(description))
    private fun fn(label: String, key: CalcKey, description: Int) =
        PadKey(label, input(key), KeyStyle.Function, s(description))

    /** Tastierino principale: 4 colonne × 5 righe. */
    fun main(decimalSeparator: Char): List<List<PadKey>> = listOf(
        listOf(
            PadKey("AC", input(CalcKey.Clear), KeyStyle.Clear, s(R.string.key_clear)),
            PadKey("( )", input(CalcKey.Parentheses), KeyStyle.Operator, s(R.string.key_parentheses)),
            PadKey("%", input(CalcKey.Percent), KeyStyle.Operator, s(R.string.key_percent)),
            op("÷", '/', R.string.key_divide),
        ),
        listOf(digit('7'), digit('8'), digit('9'), op("×", '*', R.string.key_multiply)),
        listOf(digit('4'), digit('5'), digit('6'), op("−", '-', R.string.key_minus)),
        listOf(digit('1'), digit('2'), digit('3'), op("+", '+', R.string.key_plus)),
        listOf(
            digit('0'),
            PadKey(decimalSeparator.toString(), input(CalcKey.Decimal), KeyStyle.Number, s(R.string.key_decimal)),
            PadKey(
                "", input(CalcKey.Backspace), KeyStyle.Number, s(R.string.key_backspace),
                icon = R.drawable.ic_backspace, longPressAction = input(CalcKey.Clear),
            ),
            PadKey("=", input(CalcKey.Equals), KeyStyle.Equals, s(R.string.key_equals)),
        ),
    )

    fun expandKey(expanded: Boolean) = PadKey(
        "", PadAction.ToggleScientific, KeyStyle.Function,
        s(if (expanded) R.string.hide_scientific else R.string.show_scientific),
        icon = if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
    )

    fun historyKey() = PadKey(
        "", PadAction.ShowHistory, KeyStyle.Function, s(R.string.history), icon = R.drawable.ic_history,
    )

    fun menuKey() = PadKey("", PadAction.ShowMenu, KeyStyle.Function, s(R.string.more_options), icon = R.drawable.ic_more_vert)

    fun variableKey(name: Char) = PadKey(
        name.toString(), input(CalcKey.Variable(name)), KeyStyle.Function, context.getString(R.string.key_variable, name.toString()),
    )

    private fun solveKey() = PadKey(s(R.string.key_solve_short), PadAction.Solve, KeyStyle.Function, s(R.string.key_solve))
    private fun variablesKey() =
        PadKey(s(R.string.key_variables_short), PadAction.ShowVariables, KeyStyle.Function, s(R.string.key_variables))
    private fun memoryAddKey() = PadKey(
        "M+", PadAction.MemoryAdd, KeyStyle.Function, s(R.string.key_memory_plus),
        longPressAction = PadAction.MemorySubtract,
    )
    private fun memorySubtractKey() = PadKey("M−", PadAction.MemorySubtract, KeyStyle.Function, s(R.string.key_memory_minus))

    // ------------------------------------------------------------ schermata equazioni

    fun backKey() = PadKey("", PadAction.Back, KeyStyle.Function, s(R.string.back), icon = R.drawable.ic_arrow_back)

    fun fractionKey(asFraction: Boolean) = PadKey(
        s(R.string.fraction_short), PadAction.ToggleFraction, KeyStyle.Toggle,
        s(R.string.show_fractions), active = asFraction,
    )

    fun equationModes(current: EquationMode): List<PadKey> = listOf(
        Triple(EquationMode.SYSTEM_2, R.string.mode_system_2, R.string.mode_system_2_description),
        Triple(EquationMode.SYSTEM_3, R.string.mode_system_3, R.string.mode_system_3_description),
        Triple(EquationMode.QUADRATIC, R.string.mode_quadratic, R.string.mode_quadratic_description),
        Triple(EquationMode.CUBIC, R.string.mode_cubic, R.string.mode_cubic_description),
        Triple(EquationMode.INEQUALITY, R.string.mode_inequality, R.string.mode_inequality_description),
    ).map { (mode, label, description) ->
        PadKey(s(label), PadAction.SetEquationMode(mode), KeyStyle.Toggle, s(description), active = mode == current)
    }

    fun statModes(current: StatMode): List<PadKey> = listOf(
        Triple(StatMode.ONE_VARIABLE, R.string.stat_one_variable, R.string.stat_one_variable_description),
        Triple(StatMode.FREQUENCY, R.string.stat_frequency, R.string.stat_frequency_description),
        Triple(StatMode.REGRESSION, R.string.stat_regression, R.string.stat_regression_description),
    ).map { (mode, label, description) ->
        PadKey(s(label), PadAction.SetStatMode(mode), KeyStyle.Toggle, s(description), active = mode == current)
    }

    private fun cellNavigation() = listOf(
        PadKey(
            "", PadAction.Sheet(SheetCommand.PREVIOUS_CELL), KeyStyle.Function,
            s(R.string.previous_coefficient), icon = R.drawable.ic_chevron_left,
        ),
        PadKey(
            "", PadAction.Sheet(SheetCommand.NEXT_CELL), KeyStyle.Function,
            s(R.string.next_coefficient), icon = R.drawable.ic_chevron_right,
        ),
    )

    /** Spostamento tra i coefficienti e funzioni utili per scriverli. */
    fun equationTools(): List<PadKey> = cellNavigation() + listOf(rootKey(inverse = false), rootKey(inverse = true), piKey())

    /** Come per le equazioni, con il tasto per eliminare la riga selezionata. */
    fun statisticsTools(): List<PadKey> = cellNavigation() + listOf(
        PadKey(s(R.string.delete_row_short), PadAction.Sheet(SheetCommand.DELETE_ROW), KeyStyle.Function, s(R.string.delete_row)),
        rootKey(inverse = false),
        piKey(),
    )

    /** Tastierino della schermata equazioni: AC svuota la cella, "=" risolve. */
    fun equationMain(decimalSeparator: Char): List<List<PadKey>> = listOf(
        listOf(
            PadKey(
                "AC", input(CalcKey.Clear), KeyStyle.Clear, s(R.string.clear_coefficient),
                longPressAction = PadAction.Sheet(SheetCommand.CLEAR_CELLS),
            ),
            PadKey("( )", input(CalcKey.Parentheses), KeyStyle.Operator, s(R.string.key_parentheses)),
            PadKey("xʸ", input(CalcKey.Operator('^')), KeyStyle.Operator, s(R.string.key_power)),
            op("÷", '/', R.string.key_divide),
        ),
        listOf(digit('7'), digit('8'), digit('9'), op("×", '*', R.string.key_multiply)),
        listOf(digit('4'), digit('5'), digit('6'), op("−", '-', R.string.key_minus)),
        listOf(digit('1'), digit('2'), digit('3'), op("+", '+', R.string.key_plus)),
        listOf(
            digit('0'),
            PadKey(decimalSeparator.toString(), input(CalcKey.Decimal), KeyStyle.Number, s(R.string.key_decimal)),
            PadKey(
                "", input(CalcKey.Backspace), KeyStyle.Number, s(R.string.key_backspace),
                icon = R.drawable.ic_backspace,
            ),
            PadKey("=", input(CalcKey.Equals), KeyStyle.Equals, s(R.string.solve)),
        ),
    )

    private fun inverseKey(inverse: Boolean) = PadKey(
        s(R.string.inverse_short), PadAction.ToggleInverse, KeyStyle.Toggle, s(R.string.inverse),
        active = inverse,
    )

    /** DEG/RAD, anche fuori dal pannello scientifico (schermata Funzioni). */
    fun angleToggle(angleUnit: AngleUnit): PadKey = angleKey(angleUnit)

    private fun angleKey(angleUnit: AngleUnit): PadKey {
        val degrees = angleUnit == AngleUnit.DEGREES
        val name = s(if (degrees) R.string.angle_degrees else R.string.angle_radians)
        return PadKey(
            s(if (degrees) R.string.angle_degrees_short else R.string.angle_radians_short),
            PadAction.ToggleAngle, KeyStyle.Toggle, context.getString(R.string.angle_toggle, name),
        )
    }

    private fun trigKeys(inverse: Boolean) = if (inverse) {
        listOf(
            fn("sin⁻¹", CalcKey.Function("asin"), R.string.key_asin),
            fn("cos⁻¹", CalcKey.Function("acos"), R.string.key_acos),
            fn("tan⁻¹", CalcKey.Function("atan"), R.string.key_atan),
        )
    } else {
        listOf(
            fn("sin", CalcKey.Function("sin"), R.string.key_sin),
            fn("cos", CalcKey.Function("cos"), R.string.key_cos),
            fn("tan", CalcKey.Function("tan"), R.string.key_tan),
        )
    }

    private fun logKeys(inverse: Boolean) = if (inverse) {
        listOf(fn("eˣ", CalcKey.ExpE, R.string.key_exp_e), fn("10ˣ", CalcKey.Exp10, R.string.key_exp_10))
    } else {
        listOf(
            fn("ln", CalcKey.Function("ln"), R.string.key_ln),
            fn("log", CalcKey.Function("log"), R.string.key_log),
        )
    }

    private fun rootKey(inverse: Boolean) = if (inverse) {
        fn("x²", CalcKey.Square, R.string.key_square)
    } else {
        fn("√", CalcKey.SquareRoot, R.string.key_sqrt)
    }

    private fun powerKey() = fn("xʸ", CalcKey.Operator('^'), R.string.key_power)
    fun mathKey() = PadKey(s(R.string.math_short), PadAction.ShowMath, KeyStyle.Function, s(R.string.key_math))
    fun separatorKey() = fn(";", CalcKey.Separator, R.string.key_separator)
    private fun reciprocalKey() = fn("1/x", CalcKey.Reciprocal, R.string.key_reciprocal)
    private fun nthRootKey() = fn("ⁿ√", CalcKey.Function("root"), R.string.key_nth_root)
    private fun piKey() = fn("π", CalcKey.Constant('π'), R.string.key_pi)
    private fun eKey() = fn("e", CalcKey.Constant('e'), R.string.key_e)
    private fun factorialKey() = fn("x!", CalcKey.Factorial, R.string.key_factorial)

    // ------------------------------------------------------------ schermata funzioni

    fun functionTabs(current: FunctionTab): List<PadKey> = listOf(
        Triple(FunctionTab.TABLE, R.string.tab_table, R.string.tab_table_description),
        Triple(FunctionTab.GRAPH, R.string.tab_graph, R.string.tab_graph_description),
        Triple(FunctionTab.ANALYSIS, R.string.tab_analysis, R.string.tab_analysis_description),
    ).map { (tab, label, description) ->
        PadKey(s(label), PadAction.SetFunctionTab(tab), KeyStyle.Toggle, s(description), active = tab == current)
    }

    /** Prima riga di strumenti: spostamento tra le caselle, potenza, radice, π. */
    fun functionTools(): List<PadKey> = cellNavigation() + listOf(powerKey(), rootKey(inverse = false), piKey())

    /** Seconda riga: funzioni più usate e il menu con tutte le altre. */
    fun functionTools2(): List<PadKey> = listOf(
        fn("sin", CalcKey.Function("sin"), R.string.key_sin),
        fn("cos", CalcKey.Function("cos"), R.string.key_cos),
        fn("tan", CalcKey.Function("tan"), R.string.key_tan),
        fn("ln", CalcKey.Function("ln"), R.string.key_ln),
        mathKey(),
    )

    /** Tastierino della schermata funzioni: come quello delle equazioni, con la X al posto di xʸ. */
    fun functionMain(decimalSeparator: Char): List<List<PadKey>> = equationMain(decimalSeparator).map { row ->
        row.map { key ->
            when (key.action) {
                PadAction.Input(CalcKey.Operator('^')) ->
                    PadKey("X", input(CalcKey.Variable('X')), KeyStyle.Operator, s(R.string.key_variable_x))
                PadAction.Input(CalcKey.Clear) -> key.copy(description = s(R.string.clear_cell))
                PadAction.Input(CalcKey.Equals) -> key.copy(description = s(R.string.key_equals))
                else -> key
            }
        }
    }

    /** In verticale: 4 colonne × 5 righe (la prima sempre visibile). */
    fun scientificPortrait(inverse: Boolean, angleUnit: AngleUnit): List<List<PadKey>> = listOf(
        listOf(inverseKey(inverse), angleKey(angleUnit), rootKey(inverse), powerKey()),
        trigKeys(inverse) + piKey(),
        logKeys(inverse) + factorialKey() + eKey(),
        listOf(mathKey(), separatorKey(), reciprocalKey(), nthRootKey()),
        listOf(variableKey('X'), solveKey(), variablesKey(), memoryAddKey()),
    )

    /** In orizzontale: 4 colonne × 5 righe, allineate al tastierino principale. */
    fun scientificLandscape(inverse: Boolean, angleUnit: AngleUnit): List<List<PadKey>> = listOf(
        listOf(inverseKey(inverse), angleKey(angleUnit), powerKey(), variableKey('X')),
        trigKeys(inverse) + solveKey(),
        logKeys(inverse) + rootKey(inverse) + variablesKey(),
        listOf(piKey(), eKey(), factorialKey(), memoryAddKey()),
        listOf(mathKey(), separatorKey(), reciprocalKey(), memorySubtractKey()),
    )
}
