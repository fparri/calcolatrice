package io.github.calcolatrice.ui

import android.content.Context
import android.text.TextPaint
import android.util.TypedValue
import android.widget.TextView
import io.github.calcolatrice.engine.TextWindow
import kotlin.math.max
import kotlin.math.min

/**
 * Testo su una sola riga che si rimpicciolisce progressivamente per restare intero sulla riga,
 * fino a una dimensione minima che resta ben leggibile. Oltre quel limite chi lo usa mostra
 * solo una parte del testo (vedi [fittingWindow]), invece di andare a capo.
 */
class FitTextView(context: Context) : TextView(context) {

    private var maxTextPx = sp(48f)
    private var minTextPx = sp(24f)

    /** Copia del pennello per misurare il testo a una dimensione diversa da quella attuale. */
    private var probe: TextPaint? = null

    /** Chiamata quando cambia la larghezza disponibile (per esempio alla prima misura o ruotando). */
    var onWidthChanged: (() -> Unit)? = null

    init {
        maxLines = 1
        setHorizontallyScrolling(false)
        probe = TextPaint(paint)
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    /** Dimensione massima e minima del testo, in sp. */
    fun setSizeRange(maxSp: Float, minSp: Float) {
        maxTextPx = sp(maxSp)
        minTextPx = sp(min(minSp, maxSp))
        fit()
    }

    /** Spazio orizzontale per il testo, in pixel (0 prima della prima misura). */
    val availableWidth: Int get() = max(0, width - compoundPaddingLeft - compoundPaddingRight)

    /** Larghezza di [text] alla dimensione minima: se supera [availableWidth] il testo va accorciato. */
    fun widthAtMinimum(text: CharSequence): Float = measure(text, minTextPx)

    private fun measure(text: CharSequence, size: Float): Float {
        val p = probe ?: return 0f
        p.set(paint)
        p.textSize = size
        return p.measureText(text, 0, text.length)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
        if (w != oldw) onWidthChanged?.invoke()
    }

    /** La dimensione più grande (tra minima e massima) a cui il testo sta tutto sulla riga. */
    private fun fit() {
        if (probe == null) return // chiamata dal costruttore di TextView, prima dell'inizializzazione
        val available = availableWidth
        if (available <= 0) return
        val content = text ?: ""
        val natural = measure(content, maxTextPx)
        val target = if (natural <= available * SAFETY) maxTextPx else max(minTextPx, maxTextPx * available * SAFETY / natural)
        if (kotlin.math.abs(textSize - target) > 0.5f) setTextSize(TypedValue.COMPLEX_UNIT_PX, target)
    }

    /**
     * Parte di [text] da mostrare quando non ci sta nemmeno alla dimensione minima ([TextWindow.fit]),
     * oppure null se il testo sta tutto.
     */
    fun fittingWindow(text: String, focus: Int, previousStart: Int, extra: String = ""): TextWindow.Window? =
        TextWindow.fit(text, focus, previousStart, availableWidth * SAFETY, extra) { widthAtMinimum(it) }

    companion object {
        const val ELLIPSIS = TextWindow.ELLIPSIS

        /** Margine per le piccole differenze tra misura e disegno del testo. */
        private const val SAFETY = 0.97f
    }
}
