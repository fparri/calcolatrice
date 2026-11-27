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

    private val SIMPLE = Regex("""[0-9.,'’\u00A0\u202Fπe√A-Z]*[0-9πeA-Z]""")

    /**
     * Le frazioni su due livelli dell'editor (`{1~2}`, già formattate per lo schermo) scritte su una
     * riga: `1/2`, oppure `(1+x)/(3)` quando una parte non è un numero o un simbolo solo.
     * Senza espressioni regolari: su Android (motore ICU) una `}` non preceduta da `\` nel pattern manda
     * l'app in crash, mentre sulla JVM dei test funziona.
     */
    fun linearize(text: String): String {
        if (ExpressionText.FRACTION_OPEN !in text) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != ExpressionText.FRACTION_OPEN) {
                out.append(c)
                i++
                continue
            }
            // Barra e chiusura della frazione che si apre in i (allo stesso livello).
            var depth = 0
            var bar = -1
            var close = -1
            for (k in i until text.length) {
                when (text[k]) {
                    ExpressionText.FRACTION_OPEN -> depth++
                    ExpressionText.FRACTION_BAR -> if (depth == 1 && bar < 0) bar = k
                    ExpressionText.FRACTION_CLOSE -> if (--depth == 0) {
                        close = k
                        break
                    }
                }
            }
            if (bar < 0 || close < 0) {
                out.append(c)
                i++
                continue
            }
            fun part(p: String) = if (SIMPLE.matches(p)) p else "($p)"
            out.append(part(linearize(text.substring(i + 1, bar)))).append('/').append(part(linearize(text.substring(bar + 1, close))))
            i = close + 1
        }
        return out.toString()
    }

    /**
     * La posizione più vicina a [index] fuori da ogni frazione `{…~…}`, cercando all'indietro o in avanti:
     * un pezzo di testo tagliato lì non spezza una frazione.
     */
    fun outside(text: CharSequence, index: Int, forward: Boolean): Int {
        val depth = IntArray(text.length + 1)
        var d = 0
        for (k in text.indices) {
            depth[k] = d
            if (text[k] == ExpressionText.FRACTION_OPEN) d++
            if (text[k] == ExpressionText.FRACTION_CLOSE) d = maxOf(0, d - 1)
        }
        depth[text.length] = d
        var i = index.coerceIn(0, text.length)
        while (depth[i] > 0 && i in 0..text.length) {
            if (forward) {
                if (i == text.length) break
                i++
            } else {
                if (i == 0) break
                i--
            }
        }
        return i
    }

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
