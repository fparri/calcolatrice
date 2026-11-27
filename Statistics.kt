package io.github.calcolatrice.engine

import java.math.BigDecimal

/** Statistica a una variabile, come la modalità STAT 1-VAR delle calcolatrici. */
data class OneVariableStats(
    val n: Int,
    val sum: BigDecimal,
    val sumOfSquares: BigDecimal,
    val mean: BigDecimal,
    /** σ: deviazione standard della popolazione (divide per n). */
    val populationSd: BigDecimal,
    /** s: deviazione standard campionaria (divide per n − 1); null con un solo dato. */
    val sampleSd: BigDecimal?,
    val min: BigDecimal,
    val q1: BigDecimal,
    val median: BigDecimal,
    val q3: BigDecimal,
    val max: BigDecimal,
    /** Moda: i valori più frequenti; vuota se tutti i valori compaiono lo stesso numero di volte. */
    val modes: List<BigDecimal> = emptyList(),
)

/** Regressione lineare y = a + bx, come la modalità STAT A+BX. */
data class RegressionStats(
    val n: Int,
    val meanX: BigDecimal,
    val meanY: BigDecimal,
    val sdX: BigDecimal,
    val sdY: BigDecimal,
    val a: BigDecimal,
    val b: BigDecimal,
    /** Coefficiente di correlazione; null se tutte le y sono uguali. */
    val r: BigDecimal?,
)

object Statistics {
    private val MC = MathSupport.MC

    fun oneVariable(values: List<BigDecimal>): OneVariableStats = weighted(values.map { it to 1L })

    /**
     * Statistica di dati con frequenze (valore, quante volte compare), senza ripetere i valori:
     * anche frequenze grandi restano veloci. Con frequenze tutte 1 è la statistica a una variabile.
     */
    fun weighted(data: List<Pair<BigDecimal, Long>>): OneVariableStats {
        val pairs = data.filter { it.second > 0 }
        require(pairs.isNotEmpty())
        val n = pairs.sumOf { it.second }
        val count = BigDecimal(n)
        val sum = pairs.fold(BigDecimal.ZERO) { acc, (x, f) -> acc.add(x.multiply(BigDecimal(f))) }
        val sumOfSquares = pairs.fold(BigDecimal.ZERO) { acc, (x, f) -> acc.add(x.multiply(x).multiply(BigDecimal(f))) }
        val mean = sum.divide(count, MC)
        // Due passaggi (scarti dalla media): più preciso di Σx² − n·x̄².
        val squares = pairs.fold(BigDecimal.ZERO) { acc, (x, f) ->
            acc.add(x.subtract(mean).pow(2).multiply(BigDecimal(f)), MC)
        }
        val sorted = pairs.sortedBy { it.first }
        val cumulative = LongArray(sorted.size)
        var running = 0L
        sorted.forEachIndexed { i, (_, f) ->
            running += f
            cumulative[i] = running
        }
        /** Il valore in posizione [k] (da 0) dei dati ordinati, come se ogni valore fosse ripetuto. */
        fun kth(k: Long): BigDecimal {
            var low = 0
            var high = sorted.size - 1
            while (low < high) {
                val middle = (low + high) / 2
                if (cumulative[middle] > k) high = middle else low = middle + 1
            }
            return sorted[low].first
        }
        /** Mediana delle posizioni [from, to). */
        fun median(from: Long, to: Long): BigDecimal {
            val m = to - from
            return if (m % 2 == 1L) {
                kth(from + m / 2)
            } else {
                kth(from + m / 2 - 1).add(kth(from + m / 2)).divide(BigDecimal(2), MC)
            }
        }
        // Quartili come sulle calcolatrici: mediane delle due metà, esclusa la mediana se n è dispari.
        val median = median(0, n)
        return OneVariableStats(
            n = n.toInt().takeIf { it.toLong() == n } ?: Int.MAX_VALUE,
            sum = MathSupport.checked(sum.round(MC)),
            sumOfSquares = MathSupport.checked(sumOfSquares.round(MC)),
            mean = mean,
            populationSd = MathSupport.sqrt(squares.divide(count, MC)),
            sampleSd = if (n >= 2) MathSupport.sqrt(squares.divide(BigDecimal(n - 1), MC)) else null,
            min = sorted.first().first,
            q1 = if (n / 2 == 0L) median else median(0, n / 2),
            median = median,
            q3 = if (n / 2 == 0L) median else median((n + 1) / 2, n),
            max = sorted.last().first,
            modes = modes(sorted),
        )
    }

    /** Valori con la frequenza massima; nessuna moda se ci sono più valori tutti con la stessa frequenza. */
    private fun modes(sorted: List<Pair<BigDecimal, Long>>): List<BigDecimal> {
        // Valori uguali scritti in righe diverse si sommano (2 e 2,0 sono lo stesso valore).
        val groups = ArrayList<Pair<BigDecimal, Long>>()
        for ((x, f) in sorted) {
            val last = groups.lastOrNull()
            if (last != null && last.first.compareTo(x) == 0) groups[groups.size - 1] = last.first to last.second + f else groups += x to f
        }
        val top = groups.maxOf { it.second }
        if (groups.size > 1 && groups.all { it.second == top }) return emptyList()
        return groups.filter { it.second == top }.map { it.first }
    }

    /** Retta dei minimi quadrati; servono almeno due punti con x diverse. */
    fun linearRegression(xs: List<BigDecimal>, ys: List<BigDecimal>): RegressionStats {
        require(xs.size == ys.size)
        val n = xs.size
        if (n < 2) throw CalcException(CalcError.UNDEFINED)
        val count = BigDecimal(n)
        val meanX = xs.fold(BigDecimal.ZERO, BigDecimal::add).divide(count, MC)
        val meanY = ys.fold(BigDecimal.ZERO, BigDecimal::add).divide(count, MC)
        var sxx = BigDecimal.ZERO
        var syy = BigDecimal.ZERO
        var sxy = BigDecimal.ZERO
        for (i in 0 until n) {
            val dx = xs[i].subtract(meanX, MC)
            val dy = ys[i].subtract(meanY, MC)
            sxx = sxx.add(dx.multiply(dx, MC), MC)
            syy = syy.add(dy.multiply(dy, MC), MC)
            sxy = sxy.add(dx.multiply(dy, MC), MC)
        }
        if (sxx.signum() == 0) throw CalcException(CalcError.UNDEFINED)
        val b = sxy.divide(sxx, MC)
        val a = meanY.subtract(b.multiply(meanX, MC), MC)
        val r = if (syy.signum() == 0) null else sxy.divide(MathSupport.sqrt(sxx.multiply(syy, MC)), MC)
        return RegressionStats(
            n = n,
            meanX = meanX,
            meanY = meanY,
            sdX = MathSupport.sqrt(sxx.divide(count, MC)),
            sdY = MathSupport.sqrt(syy.divide(count, MC)),
            a = MathSupport.checked(a),
            b = MathSupport.checked(b),
            r = r,
        )
    }

}

/** Tipo di analisi: una colonna di dati oppure coppie (x, y). */
enum class StatMode(val columns: Int) {
    ONE_VARIABLE(1),

    /** Coppie (valore, frequenza). */
    FREQUENCY(2),
    REGRESSION(2),
}

sealed interface StatOutcome {
    data class OneVariable(val stats: OneVariableStats) : StatOutcome
    data class Regression(val stats: RegressionStats) : StatOutcome

    /** Dati insufficienti: nessun dato (una variabile, frequenze) o meno di due punti (regressione). */
    data object NotEnoughData : StatOutcome

    /** Errore; [row] e [column] indicano la cella da correggere, se è quello il problema. */
    data class Error(val error: CalcError, val row: Int? = null, val column: Int? = null) : StatOutcome
}

/**
 * Stato della schermata statistica, indipendente da Android: la tabella dei dati di ogni modalità,
 * la cella selezionata e l'ultimo risultato. Sotto i dati c'è sempre una riga vuota per aggiungerne.
 */
class StatisticsSheet(
    mode: StatMode = StatMode.ONE_VARIABLE,
    data: Map<StatMode, List<List<String>>> = emptyMap(),
) {
    var mode: StatMode = mode
        private set
    var row: Int = 0
        private set
    var column: Int = 0
        private set
    var outcome: StatOutcome? = null
        private set

    private val tables: Map<StatMode, MutableList<MutableList<String>>> = StatMode.entries.associateWith { m ->
        data[m].orEmpty()
            .map { saved -> MutableList(m.columns) { saved.getOrNull(it).orEmpty() } }
            .filter { r -> r.any { it.isNotEmpty() } }
            .take(MAX_ROWS)
            .toMutableList()
    }

    private val table get() = tables.getValue(mode)

    /** Righe di dati (senza la riga vuota finale). */
    fun rows(m: StatMode = mode): List<List<String>> = tables.getValue(m).map { it.toList() }

    /** Righe mostrate: i dati più una riga vuota, finché c'è posto. */
    val rowCount: Int get() = if (table.size >= MAX_ROWS) table.size else table.size + 1

    fun cell(r: Int, c: Int): String = table.getOrNull(r)?.getOrNull(c).orEmpty()

    fun setMode(m: StatMode) {
        if (m == mode) return
        mode = m
        row = 0
        column = 0
        outcome = null
    }

    fun select(r: Int, c: Int) {
        require(r in 0 until rowCount && c in 0 until mode.columns)
        row = r
        column = c
    }

    fun next() {
        if (column < mode.columns - 1) {
            column++
        } else if (row < rowCount - 1) {
            row++
            column = 0
        }
    }

    fun previous() {
        if (column > 0) {
            column--
        } else if (row > 0) {
            row--
            column = mode.columns - 1
        }
    }

    /** Applica un tasto alla cella selezionata; "=" calcola. */
    fun onKey(key: CalcKey) {
        if (key == CalcKey.Equals) {
            solve()
            return
        }
        if (row == table.size) {
            if (table.size >= MAX_ROWS) return
            table += MutableList(mode.columns) { "" }
        }
        val line = table[row]
        line[column] = CalculatorEditor.onKey(CalculatorState(line[column]), key, AngleUnit.DEGREES).state.expression
        // Una riga svuotata del tutto sparisce (resta la riga vuota finale).
        if (line.all { it.isEmpty() }) {
            table.removeAt(row)
            row = row.coerceAtMost(table.size)
        }
        outcome = null
    }

    fun deleteRow() {
        if (row >= table.size) return
        table.removeAt(row)
        row = row.coerceAtMost(rowCount - 1)
        outcome = null
    }

    fun clearAll() {
        table.clear()
        row = 0
        column = 0
        outcome = null
    }

    fun solve(): StatOutcome {
        val result = compute()
        if (result is StatOutcome.Error && result.row != null && result.column != null) {
            row = result.row
            column = result.column
        }
        outcome = result
        return result
    }

    private fun compute(): StatOutcome {
        val columns = List(mode.columns) { ArrayList<BigDecimal>() }
        table.forEachIndexed { r, line ->
            line.forEachIndexed { c, expression ->
                if (expression.isEmpty()) return StatOutcome.Error(CalcError.SYNTAX, r, c)
                when (val value = evaluate(expression, AngleUnit.DEGREES)) {
                    is EvalResult.Success -> columns[c] += value.value
                    is EvalResult.Failure -> return StatOutcome.Error(value.error, r, c)
                }
            }
        }
        return try {
            when (mode) {
                StatMode.ONE_VARIABLE ->
                    if (columns[0].isEmpty()) StatOutcome.NotEnoughData else StatOutcome.OneVariable(Statistics.oneVariable(columns[0]))
                StatMode.FREQUENCY -> {
                    // Le frequenze devono essere interi non negativi.
                    val counts = columns[1].mapIndexed { r, f ->
                        if (f.signum() < 0 || !MathSupport.isInteger(f)) return StatOutcome.Error(CalcError.UNDEFINED, r, 1)
                        if (f > MAX_FREQUENCY) return StatOutcome.Error(CalcError.OVERFLOW, r, 1)
                        f.toLong()
                    }
                    if (counts.sum() == 0L) {
                        StatOutcome.NotEnoughData
                    } else {
                        StatOutcome.OneVariable(Statistics.weighted(columns[0].zip(counts)))
                    }
                }
                StatMode.REGRESSION ->
                    if (columns[0].size < 2) {
                        StatOutcome.NotEnoughData
                    } else {
                        StatOutcome.Regression(Statistics.linearRegression(columns[0], columns[1]))
                    }
            }
        } catch (e: CalcException) {
            StatOutcome.Error(e.error)
        } catch (e: ArithmeticException) {
            StatOutcome.Error(CalcError.OVERFLOW)
        }
    }

    companion object {
        const val MAX_ROWS = 200

        /** Somme di frequenze fino a circa 10¹⁵ restano esatte. */
        private val MAX_FREQUENCY = BigDecimal("1E12")
    }
}
