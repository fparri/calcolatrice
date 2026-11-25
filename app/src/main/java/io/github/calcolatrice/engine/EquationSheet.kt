package io.github.calcolatrice.engine

import java.math.BigDecimal

/** Tipi di problema: numero di equazioni e di coefficienti per equazione. */
enum class EquationMode(val equations: Int, val coefficients: Int) {
    /** ax + by = c, due equazioni. */
    SYSTEM_2(2, 3),

    /** ax + by + cz = d, tre equazioni. */
    SYSTEM_3(3, 4),

    /** ax² + bx + c = 0. */
    QUADRATIC(1, 3),
    ;

    val cellCount: Int get() = equations * coefficients
}

sealed interface EquationOutcome {
    data class System(val solution: LinearSolution) : EquationOutcome
    data class Quadratic(val solution: QuadraticSolution) : EquationOutcome

    /** Errore nel calcolo; [cell] è il coefficiente che non si riesce a calcolare, se è quello il problema. */
    data class Error(val error: CalcError, val cell: Int? = null) : EquationOutcome
}

/**
 * Stato della schermata equazioni, indipendente da Android: la tabella dei coefficienti di ogni
 * modalità, la cella selezionata e l'ultimo risultato. Ogni cella è una piccola espressione
 * (`1/3`, `√2`, `-π`…) modificata con le stesse regole della calcolatrice.
 */
class EquationSheet(
    mode: EquationMode = EquationMode.SYSTEM_2,
    cells: Map<EquationMode, List<String>> = emptyMap(),
) {
    var mode: EquationMode = mode
        private set
    var selected: Int = 0
        private set
    var outcome: EquationOutcome? = null
        private set

    private val cells: Map<EquationMode, MutableList<String>> = EquationMode.entries.associateWith { m ->
        MutableList(m.cellCount) { i -> cells[m]?.getOrNull(i).orEmpty() }
    }

    /** Espressioni delle celle (vuoto = 0), riga per riga. */
    fun expressions(m: EquationMode = mode): List<String> = cells.getValue(m).toList()

    fun setMode(m: EquationMode) {
        if (m == mode) return
        mode = m
        selected = 0
        outcome = null
    }

    fun select(index: Int) {
        require(index in 0 until mode.cellCount)
        selected = index
    }

    fun next() {
        selected = (selected + 1) % mode.cellCount
    }

    fun previous() {
        selected = (selected - 1 + mode.cellCount) % mode.cellCount
    }

    /** Applica un tasto alla cella selezionata; "=" risolve. */
    fun onKey(key: CalcKey) {
        if (key == CalcKey.Equals) {
            solve()
            return
        }
        val list = cells.getValue(mode)
        list[selected] = CalculatorEditor.onKey(CalculatorState(list[selected]), key, AngleUnit.DEGREES).state.expression
        outcome = null
    }

    /** Svuota tutti i coefficienti della modalità corrente. */
    fun clearAll() {
        cells.getValue(mode).fill("")
        selected = 0
        outcome = null
    }

    fun solve(): EquationOutcome {
        val result = compute()
        if (result is EquationOutcome.Error && result.cell != null) selected = result.cell
        outcome = result
        return result
    }

    private fun compute(): EquationOutcome {
        val values = ArrayList<BigDecimal>(mode.cellCount)
        cells.getValue(mode).forEachIndexed { index, expression ->
            if (expression.isEmpty()) {
                values += BigDecimal.ZERO
            } else {
                when (val r = evaluate(expression, AngleUnit.DEGREES)) {
                    is EvalResult.Success -> values += r.value
                    is EvalResult.Failure -> return EquationOutcome.Error(r.error, index)
                }
            }
        }
        return try {
            when (mode) {
                EquationMode.SYSTEM_2, EquationMode.SYSTEM_3 ->
                    EquationOutcome.System(LinearSystem.solve(values.chunked(mode.coefficients)))
                EquationMode.QUADRATIC ->
                    EquationOutcome.Quadratic(QuadraticEquation.solve(values[0], values[1], values[2]))
            }
        } catch (e: CalcException) {
            EquationOutcome.Error(e.error)
        } catch (e: ArithmeticException) {
            EquationOutcome.Error(CalcError.OVERFLOW)
        }
    }
}
