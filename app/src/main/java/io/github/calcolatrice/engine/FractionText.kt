package io.github.calcolatrice.engine

/**
 * Le frazioni dentro il testo di un risultato in forma esatta ("−3/4", "√3/2", "3π/4", "−1/2 + (√3/2)i"),
 * per disegnarle su due livelli. Il testo resta quello lineare: si copia e si legge con TalkBack come prima.
 */
object FractionText {

    /**
     * Una frazione in [start, end): [numerator] sopra, [denominator] sotto. Se era tra parentesi, come
     * la parte immaginaria `(√3/2)i`, le parentesi sono comprese nell'intervallo: su due livelli non servono.
     */
    data class Match(val start: Int, val end: Int, val numerator: String, val denominator: String)

    /** Numeratore: cifre (con i separatori), √ e π; denominatore: cifre. Il segno meno resta fuori. */
    private val FRACTION = Regex("""(\()?([0-9.,'’  √π]*[0-9π])/([0-9][0-9.,'’  ]*)(\))?""")

    fun find(text: CharSequence): List<Match> = FRACTION.findAll(text).map { m ->
        val open = m.groups[1]
        val close = m.groups[4]
        // Parentesi tolte solo se racchiudono proprio la frazione.
        val paired = open != null && close != null
        val start = if (paired) m.range.first else m.groups[2]!!.range.first
        val end = if (paired) m.range.last + 1 else m.groups[3]!!.range.last + 1
        Match(start, end, m.groups[2]!!.value, m.groups[3]!!.value.trimEnd('.', ',', '\'', '’'))
    }.toList()
}
