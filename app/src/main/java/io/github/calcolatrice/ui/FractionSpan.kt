package io.github.calcolatrice.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan
import io.github.calcolatrice.engine.FractionText
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Frazione scritta come sul quaderno: numeratore sopra, linea di frazione, denominatore sotto,
 * centrati sull'asse matematico della riga (dove sta il segno meno). La riga si alza quanto serve.
 */
class FractionSpan(private val numerator: String, private val denominator: String) : ReplacementSpan() {

    private val small = TextPaint()
    private val numeratorBounds = Rect()
    private val denominatorBounds = Rect()

    /** Pennello ridotto e ingombro reale dei due termini (le cifre non scendono sotto la linea di base). */
    private fun prepare(paint: Paint) {
        small.set(paint)
        small.textSize = paint.textSize * SCALE
        small.getTextBounds(numerator, 0, numerator.length, numeratorBounds)
        small.getTextBounds(denominator, 0, denominator.length, denominatorBounds)
    }

    /** Asse della frazione rispetto alla linea di base (negativo = sopra). */
    private fun axis(paint: Paint) = -paint.textSize * AXIS
    private fun bar(paint: Paint) = max(1f, paint.textSize * BAR)
    private fun gap(paint: Paint) = paint.textSize * GAP
    private fun padding(paint: Paint) = paint.textSize * PADDING

    private fun numeratorBaseline(paint: Paint) = axis(paint) - bar(paint) / 2 - gap(paint) - numeratorBounds.bottom
    private fun denominatorBaseline(paint: Paint) = axis(paint) + bar(paint) / 2 + gap(paint) - denominatorBounds.top

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        prepare(paint)
        val width = max(small.measureText(numerator), small.measureText(denominator)) + 2 * padding(paint)
        if (fm != null) {
            // La riga si allarga in alto e in basso per contenere i due livelli.
            val base = paint.fontMetricsInt
            val top = (numeratorBaseline(paint) + numeratorBounds.top - gap(paint)).roundToInt()
            val bottom = (denominatorBaseline(paint) + denominatorBounds.bottom + gap(paint)).roundToInt()
            fm.ascent = minOf(base.ascent, top)
            fm.top = minOf(base.top, top)
            fm.descent = maxOf(base.descent, bottom)
            fm.bottom = maxOf(base.bottom, bottom)
            fm.leading = base.leading
        }
        return ceil(width).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        prepare(paint)
        val width = max(small.measureText(numerator), small.measureText(denominator)) + 2 * padding(paint)
        val center = x + width / 2
        small.textAlign = Paint.Align.CENTER
        canvas.drawText(numerator, center, y + numeratorBaseline(paint), small)
        canvas.drawText(denominator, center, y + denominatorBaseline(paint), small)
        val axis = y + axis(paint)
        val half = bar(paint) / 2
        val style = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawRect(x + padding(paint) / 2, axis - half, x + width - padding(paint) / 2, axis + half, paint)
        paint.style = style
    }

    private companion object {
        const val SCALE = 0.7f
        /** Altezza del segno meno sopra la linea di base, in em: lì sta la linea di frazione. */
        const val AXIS = 0.33f
        const val BAR = 0.04f
        const val GAP = 0.09f
        const val PADDING = 0.08f
    }
}

/** Il testo con le frazioni disegnate su due livelli ([FractionSpan]); il testo sottostante non cambia. */
fun stackedFractions(text: CharSequence): CharSequence {
    val matches = FractionText.find(text)
    if (matches.isEmpty()) return text
    val builder = SpannableStringBuilder(text)
    for (m in matches) {
        builder.setSpan(FractionSpan(m.numerator, m.denominator), m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return builder
}
