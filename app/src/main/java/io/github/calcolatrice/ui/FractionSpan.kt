package io.github.calcolatrice.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import io.github.calcolatrice.engine.ExpressionText
import io.github.calcolatrice.engine.FractionText
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Frazione scritta come sul quaderno: numeratore sopra, linea di frazione, denominatore sotto,
 * centrati sull'asse matematico della riga (dove sta il segno meno). La riga si alza quanto serve.
 * Numeratore e denominatore possono contenere altre frazioni (disegnate più piccole) e il cursore;
 * una parte vuota si mostra come un riquadro da riempire.
 */
class FractionSpan(private val numerator: CharSequence, private val denominator: CharSequence) : ReplacementSpan() {

    private val small = TextPaint()
    private val numeratorBox = MathText.Box()
    private val denominatorBox = MathText.Box()

    /** Pennello ridotto e ingombro reale delle due parti (le cifre non scendono sotto la linea di base). */
    private fun prepare(paint: Paint) {
        small.set(paint)
        small.textSize = paint.textSize * SCALE
        MathText.measure(numerator, small, numeratorBox)
        MathText.measure(denominator, small, denominatorBox)
    }

    /** Asse della frazione rispetto alla linea di base (negativo = sopra). */
    private fun axis(paint: Paint) = -paint.textSize * AXIS
    private fun bar(paint: Paint) = max(1f, paint.textSize * BAR)
    private fun gap(paint: Paint) = paint.textSize * GAP
    private fun padding(paint: Paint) = paint.textSize * PADDING
    private fun width(paint: Paint) = max(numeratorBox.width, denominatorBox.width) + 2 * padding(paint)

    private fun numeratorBaseline(paint: Paint) = axis(paint) - bar(paint) / 2 - gap(paint) - numeratorBox.bottom
    private fun denominatorBaseline(paint: Paint) = axis(paint) + bar(paint) / 2 + gap(paint) - denominatorBox.top

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        prepare(paint)
        if (fm != null) {
            // La riga si allarga in alto e in basso per contenere i due livelli.
            val base = paint.fontMetricsInt
            val top = (numeratorBaseline(paint) + numeratorBox.top - gap(paint)).roundToInt()
            val bottom = (denominatorBaseline(paint) + denominatorBox.bottom + gap(paint)).roundToInt()
            fm.ascent = min(base.ascent, top)
            fm.top = min(base.top, top)
            fm.descent = max(base.descent, bottom)
            fm.bottom = max(base.bottom, bottom)
            fm.leading = base.leading
        }
        return ceil(width(paint)).toInt()
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
        val width = width(paint)
        MathText.draw(canvas, numerator, x + (width - numeratorBox.width) / 2, y + numeratorBaseline(paint), small)
        MathText.draw(canvas, denominator, x + (width - denominatorBox.width) / 2, y + denominatorBaseline(paint), small)
        val axis = y + axis(paint)
        val half = bar(paint) / 2
        val style = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawRect(x + padding(paint) / 2, axis - half, x + width - padding(paint) / 2, axis + half, paint)
        paint.style = style
    }

    private companion object {
        const val SCALE = 0.72f
        /** Altezza del segno meno sopra la linea di base, in em: lì sta la linea di frazione. */
        const val AXIS = 0.33f
        const val BAR = 0.04f
        const val GAP = 0.09f
        const val PADDING = 0.08f
    }
}

/**
 * Misura e disegno di un testo che contiene frazioni su due livelli, fuori da un TextView: serve per
 * le parti di una frazione, che possono contenere altre frazioni, il cursore colorato e riquadri vuoti.
 */
object MathText {
    /** Il cursore disegnato nel testo del display. */
    const val CURSOR = '|'

    /** Ingombro: larghezza, e altezza sopra (negativa) e sotto la linea di base. */
    class Box {
        var width = 0f
        var top = 0f
        var bottom = 0f
    }

    private val bounds = Rect()
    private val metrics = Paint.FontMetricsInt()

    private fun placeholderWidth(paint: Paint) = paint.textSize * 0.62f
    private fun placeholderHeight(paint: Paint) = paint.textSize * 0.72f

    /** Una parte vuota (al più col cursore) si disegna come un riquadro da riempire. */
    private fun isEmpty(text: CharSequence) = text.all { it == CURSOR }

    fun measure(text: CharSequence, paint: TextPaint, box: Box) {
        box.width = 0f
        box.top = 0f
        box.bottom = 0f
        if (isEmpty(text)) {
            box.width = placeholderWidth(paint) + if (text.isNotEmpty()) paint.measureText(CURSOR.toString()) else 0f
            box.top = -placeholderHeight(paint)
            return
        }
        forEachRun(text) { start, end, span ->
            if (span != null) {
                box.width += span.getSize(paint, text, start, end, metrics)
                box.top = min(box.top, metrics.ascent.toFloat())
                box.bottom = max(box.bottom, metrics.descent.toFloat())
            } else {
                box.width += paint.measureText(text, start, end)
                paint.getTextBounds(text.toString(), start, end, bounds)
                box.top = min(box.top, bounds.top.toFloat())
                box.bottom = max(box.bottom, bounds.bottom.toFloat())
            }
        }
    }

    fun draw(canvas: Canvas, text: CharSequence, x: Float, baseline: Float, paint: TextPaint) {
        val color = paint.color
        if (isEmpty(text)) {
            var left = x
            if (text.isNotEmpty()) {
                paint.color = colorAt(text, 0) ?: color
                canvas.drawText(CURSOR.toString(), left, baseline, paint)
                left += paint.measureText(CURSOR.toString())
                paint.color = color
            }
            val style = paint.style
            val stroke = paint.strokeWidth
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, paint.textSize * 0.06f)
            paint.alpha = 150
            val inset = paint.textSize * 0.06f
            canvas.drawRect(left + inset, baseline - placeholderHeight(paint), left + placeholderWidth(paint) - inset, baseline, paint)
            paint.color = color
            paint.style = style
            paint.strokeWidth = stroke
            return
        }
        var left = x
        forEachRun(text) { start, end, span ->
            if (span != null) {
                val width = span.getSize(paint, text, start, end, null)
                span.draw(canvas, text, start, end, left, 0, baseline.roundToInt(), 0, paint)
                left += width
            } else {
                paint.color = colorAt(text, start) ?: color
                canvas.drawText(text, start, end, left, baseline, paint)
                left += paint.measureText(text, start, end)
                paint.color = color
            }
        }
    }

    private fun colorAt(text: CharSequence, index: Int): Int? = (text as? Spanned)
        ?.getSpans(index, index + 1, ForegroundColorSpan::class.java)
        ?.lastOrNull()?.foregroundColor

    /** Pezzi di testo uniformi: frazioni intere, oppure testo semplice con un solo colore. */
    private inline fun forEachRun(text: CharSequence, action: (Int, Int, FractionSpan?) -> Unit) {
        val spanned = text as? Spanned
        var i = 0
        while (i < text.length) {
            val fraction = spanned?.getSpans(i, i + 1, FractionSpan::class.java)
                ?.firstOrNull { spanned.getSpanStart(it) == i }
            if (fraction != null) {
                val end = spanned.getSpanEnd(fraction)
                action(i, end, fraction)
                i = end
                continue
            }
            var end = spanned?.nextSpanTransition(i, text.length, Any::class.java) ?: text.length
            if (end <= i) end = i + 1
            action(i, end, null)
            i = end
        }
    }
}

/** Il testo con le frazioni dei risultati esatti ("3/4", "√3/2") disegnate su due livelli. */
fun stackedFractions(text: CharSequence): CharSequence {
    val matches = FractionText.find(text)
    if (matches.isEmpty()) return text
    val builder = SpannableStringBuilder(text)
    for (m in matches) {
        builder.setSpan(FractionSpan(m.numerator, m.denominator), m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return builder
}

/**
 * Il testo dell'editor con le frazioni `{numeratore~denominatore}` disegnate su due livelli, anche una
 * dentro l'altra. Il testo sotto non cambia; segni senza la loro coppia (in un pezzo di testo tagliato)
 * restano come sono.
 */
fun mathSpans(text: CharSequence): CharSequence {
    if (!ExpressionText.hasFraction(text)) return text
    val builder = SpannableStringBuilder(text)
    var i = 0
    while (i < text.length) {
        if (text[i] != ExpressionText.FRACTION_OPEN) {
            i++
            continue
        }
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
            i++
            continue
        }
        val span = FractionSpan(mathSpans(text.subSequence(i + 1, bar)), mathSpans(text.subSequence(bar + 1, close)))
        builder.setSpan(span, i, close + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        i = close + 1
    }
    return builder
}
