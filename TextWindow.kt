package io.github.calcolatrice.engine

/**
 * Quale parte di un testo mostrare su una riga troppo corta: una finestra che contiene la posizione
 * [focus] (il cursore, o la fine), con "…" dove il testo è tagliato. Indipendente da Android:
 * la larghezza del testo arriva da [measure].
 */
object TextWindow {
    const val ELLIPSIS = "…"

    /** Intervallo [start, end) del testo mostrato. */
    data class Window(val start: Int, val end: Int)

    /**
     * @param previousStart inizio della finestra precedente: resta ferma mentre il cursore si sposta al suo interno.
     * @param extra testo aggiunto alla parte mostrata (il cursore disegnato), di cui va considerata la larghezza.
     * @return null se il testo sta tutto in [available].
     */
    fun fit(
        text: String,
        focus: Int,
        previousStart: Int,
        available: Float,
        extra: String = "",
        measure: (String) -> Float,
    ): Window? {
        if (available <= 0 || measure(text + extra) <= available) return null
        fun fits(start: Int, end: Int): Boolean {
            val shown = (if (start > 0) ELLIPSIS else "") + text.substring(start, end) +
                (if (end < text.length) ELLIPSIS else "") + extra
            return measure(shown) <= available
        }
        val at = focus.coerceIn(0, text.length)
        // Si parte dalla finestra precedente; se il cursore ne esce, la si sposta quanto basta.
        var start = previousStart.coerceIn(0, at)
        var end = start
        while (end < text.length && fits(start, end + 1)) end++
        if (at > end || (at == text.length && end < text.length)) {
            end = at
            start = end
            while (start > 0 && fits(start - 1, end)) start--
            while (end < text.length && fits(start, end + 1)) end++
        }
        return Window(start, end)
    }
}
