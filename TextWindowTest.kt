package io.github.calcolatrice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Finestra del display con una misura finta: ogni carattere è largo 1. */
class TextWindowTest {

    private val measure: (String) -> Float = { it.length.toFloat() }
    private val text = "0123456789abcdefghij" // 20 caratteri

    private fun shown(window: TextWindow.Window?): String {
        if (window == null) return text
        return (if (window.start > 0) "…" else "") + text.substring(window.start, window.end) + (if (window.end < text.length) "…" else "")
    }

    @Test
    fun shortTextIsShownWhole() {
        assertNull(TextWindow.fit("12345", 5, 0, 10f, measure = measure))
        assertNull(TextWindow.fit(text, 20, 0, 20f, measure = measure))
    }

    @Test
    fun cursorAtTheEndShowsTheTail() {
        val window = TextWindow.fit(text, text.length, 0, 10f, measure = measure)
        assertEquals("…bcdefghij", shown(window))
    }

    @Test
    fun cursorInTheMiddleStaysVisible() {
        // Cursore all'inizio: si vede l'inizio, con "…" alla fine (e il cursore conta un carattere).
        val start = TextWindow.fit(text, 0, 0, 10f, extra = "|", measure = measure)!!
        assertEquals("01234567…", shown(start))
        assertEquals(0, start.start)
        // Spostando il cursore dentro la finestra, la finestra non si muove.
        val same = TextWindow.fit(text, 4, start.start, 10f, extra = "|", measure = measure)!!
        assertEquals(start, same)
        // Oltre il bordo destro la finestra scorre quanto basta.
        val moved = TextWindow.fit(text, 15, start.start, 10f, extra = "|", measure = measure)!!
        assertEquals(15, moved.end)
        assertEquals(true, 15 in moved.start..moved.end)
        // Tornando a sinistra della finestra, la finestra inizia dal cursore.
        val back = TextWindow.fit(text, 3, moved.start, 10f, extra = "|", measure = measure)!!
        assertEquals(3, back.start)
    }

    @Test
    fun everyWindowFits() {
        for (focus in 0..text.length) {
            for (previous in 0..text.length) {
                val window = TextWindow.fit(text, focus, previous, 9f, extra = "|", measure = measure)!!
                val width = measure(shown(window) + "|")
                assertEquals("focus $focus, previous $previous", true, width <= 9f)
                assertEquals("focus $focus, previous $previous", true, focus in window.start..window.end)
            }
        }
    }
}
