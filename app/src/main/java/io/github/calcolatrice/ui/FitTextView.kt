package io.github.calcolatrice.ui

import android.content.Context
import android.graphics.Paint
import android.text.Spanned
import android.text.Layout
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

    /**
     * Se true il testo si rimpicciolisce anche per stare nell'altezza della vista (quando l'altezza è fissata
     * dal layout): una frazione su due livelli è più alta di una riga normale.
     */
    var fitsHeight = false

    /** Chiamata quando cambia la larghezza disponibile (per esempio alla prima misura o ruotando). */
    var onWidthChanged: (() -> Unit)? = null

    init {
        maxLines = 1
        setHorizontallyScrolling(false)
        probe = TextPaint(paint)
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    /**
     * Dimensione massima e minima del testo, in sp. Il testo parte subito alla dimensione massima:
     * così la prima misura dell'altezza è già quella giusta (prima partiva dalla dimensione predefinita
     * e una riga più grande, ingrandita dopo, restava tagliata).
     */
    fun setSizeRange(maxSp: Float, minSp: Float) {
        maxTextPx = sp(maxSp)
        minTextPx = sp(min(minSp, maxSp))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, maxTextPx)
        fit()
    }

    /** Spazio orizzontale per il testo, in pixel (0 prima della prima misura). */
    val availableWidth: Int get() = max(0, width - compoundPaddingLeft - compoundPaddingRight)

    /** Larghezza di [text] alla dimensione minima: se supera [availableWidth] il testo va accorciato. */
    fun widthAtMinimum(text: CharSequence): Float = measure(text, minTextPx)

    /** Larghezza del testo alla dimensione [size], compresi gli span (le frazioni su due livelli). */
    private fun measure(text: CharSequence, size: Float): Float {
        val p = probe ?: return 0f
        p.set(paint)
        p.textSize = size
        return Layout.getDesiredWidth(text, 0, text.length, p)
    }

    /** Altezza della riga alla dimensione [size], comprese le frazioni su due livelli. */
    private fun lineHeight(text: CharSequence, size: Float): Float {
        val p = probe ?: return 0f
        p.set(paint)
        p.textSize = size
        val base = p.fontMetricsInt
        var ascent = if (includeFontPadding) base.top else base.ascent
        var descent = if (includeFontPadding) base.bottom else base.descent
        if (text is Spanned) {
            val fm = Paint.FontMetricsInt()
            for (span in text.getSpans(0, text.length, FractionSpan::class.java)) {
                span.getSize(p, text, text.getSpanStart(span), text.getSpanEnd(span), fm)
                ascent = min(ascent, if (includeFontPadding) fm.top else fm.ascent)
                descent = max(descent, if (includeFontPadding) fm.bottom else fm.descent)
            }
        }
        return (descent - ascent).toFloat()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        fit()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Siamo nel mezzo del layout: la nuova dimensione si applica al passaggio successivo.
        post { fit() }
        if (w != oldw) onWidthChanged?.invoke()
    }

    /** La dimensione più grande (tra minima e massima) a cui il testo sta tutto sulla riga. */
    private fun fit() {
        if (probe == null) return // chiamata dal costruttore di TextView, prima dell'inizializzazione
        val available = availableWidth
        if (available <= 0) return
        val content = text ?: ""
        val natural = measure(content, maxTextPx)
        var target = if (natural <= available * SAFETY) maxTextPx else maxTextPx * available * SAFETY / natural
        val availableHeight = height - compoundPaddingTop - compoundPaddingBottom
        if (fitsHeight && availableHeight > 0) {
            val tall = lineHeight(content, maxTextPx)
            if (tall > 0f) target = min(target, maxTextPx * availableHeight / tall)
        }
        target = max(minTextPx, target)
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
