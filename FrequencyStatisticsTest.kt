package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FrequencyStatisticsTest {

    private fun n(s: String) = BigDecimal(s)
    private fun show(x: BigDecimal?) = x?.let(ResultFormatter::toInternal)
    private fun values(vararg v: Int) = v.map { BigDecimal(it) }

    @Test
    fun frequenciesGiveTheSameResultsAsRepeatedValues() {
        val expanded = Statistics.oneVariable(values(2, 2, 2, 3, 5, 5, 7, 7, 7, 7, 9))
        val weighted = Statistics.weighted(listOf(n("7") to 4L, n("2") to 3L, n("3") to 1L, n("5") to 2L, n("9") to 1L))
        assertEquals(expanded.copy(modes = emptyList()), weighted.copy(modes = emptyList()))
        assertEquals(listOf("7"), weighted.modes.map(::show))
    }

    @Test
    fun quartilesWithEvenAndOddCounts() {
        // 1 1 2 2 3 3 4 4: Q1 = 1,5, mediana 2,5, Q3 = 3,5.
        val even = Statistics.weighted(listOf(n("1") to 2L, n("2") to 2L, n("3") to 2L, n("4") to 2L))
        assertEquals(listOf("1.5", "2.5", "3.5"), listOf(even.q1, even.median, even.q3).map(::show))
        // 1 2 2 2 9: mediana 2, Q1 = mediana di (1, 2) = 1,5, Q3 = mediana di (2, 9) = 5,5.
        val odd = Statistics.weighted(listOf(n("1") to 1L, n("2") to 3L, n("9") to 1L))
        assertEquals(listOf("1.5", "2", "5.5"), listOf(odd.q1, odd.median, odd.q3).map(::show))
    }

    @Test
    fun largeFrequenciesAreFast() {
        val stats = Statistics.weighted(listOf(n("10") to 1_000_000_000L, n("20") to 1_000_000_000L))
        assertEquals("15", show(stats.mean))
        assertEquals("15", show(stats.median))
        assertEquals("5", show(stats.populationSd))
    }

    @Test
    fun modes() {
        assertEquals(listOf("3"), Statistics.oneVariable(values(1, 3, 3, 4)).modes.map(::show))
        assertEquals(listOf("3", "4"), Statistics.oneVariable(values(1, 3, 3, 4, 4)).modes.map(::show))
        // Tutti con la stessa frequenza: nessuna moda.
        assertEquals(emptyList<String>(), Statistics.oneVariable(values(1, 2, 3)).modes.map(::show))
        assertEquals(listOf("5"), Statistics.oneVariable(values(5)).modes.map(::show))
        // 2 e 2,0 sono lo stesso valore.
        assertEquals(listOf("2"), Statistics.oneVariable(listOf(n("2"), n("2.0"), n("3"))).modes.map(::show))
    }

    private fun sheetWith(vararg rows: Pair<String, String>): StatisticsSheet {
        val data = mapOf(StatMode.FREQUENCY to rows.map { (x, f) -> listOf(x, f) })
        return StatisticsSheet(StatMode.FREQUENCY, data)
    }

    @Test
    fun frequencySheet() {
        val outcome = sheetWith("18" to "3", "24" to "5", "30" to "2").solve() as StatOutcome.OneVariable
        assertEquals(10, outcome.stats.n)
        assertEquals("23.4", show(outcome.stats.mean))
        assertEquals(listOf("24"), outcome.stats.modes.map(::show))
    }

    @Test
    fun invalidFrequencies() {
        assertEquals(StatOutcome.Error(CalcError.UNDEFINED, 1, 1), sheetWith("1" to "2", "3" to "1.5").solve())
        assertEquals(StatOutcome.Error(CalcError.UNDEFINED, 0, 1), sheetWith("1" to "-2").solve())
        assertEquals(StatOutcome.NotEnoughData, sheetWith("1" to "0", "2" to "0").solve())
        // Frequenza zero: il valore non conta.
        val outcome = sheetWith("1" to "0", "5" to "2").solve() as StatOutcome.OneVariable
        assertEquals("5", show(outcome.stats.min))
    }
}
