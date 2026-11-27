package io.github.calcolatrice.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Grafico di una funzione, disegnato a mano (nessuna libreria). Si trascina con un dito,
 * si ingrandisce con due, il doppio tocco torna alla vista iniziale e un tocco mostra
 * le coordinate del punto della curva. La curva si interrompe dove la funzione salta (asintoti).
 */
class GraphView(context: Context, private val palette: Palette) : View(context) {

    /** La funzione da disegnare, in double; null = nessuna funzione valida. */
    var function: ((Double) -> Double)? = null
        private set

    /** Seconda funzione g, facoltativa: tratteggiata, così si distingue anche senza colori. */
    var second: ((Double) -> Double)? = null
        private set

    /** Testo del punto selezionato (calcolato con il motore preciso da chi usa la vista). */
    var traceLabel: ((Double) -> String?)? = null

    /** Messaggio al centro quando non c'è una funzione da disegnare. */
    var emptyMessage: String = ""
        set(value) {
            field = value
            invalidate()
        }

    /** Breve suggerimento mostrato in alto finché non si tocca il grafico. */
    var hint: String = ""

    /** Separatore decimale per le etichette degli assi. */
    var decimalSeparator: Char = '.'

    /** Chiamata quando cambia la parte di piano visibile (per l'accessibilità). */
    var onViewportChanged: ((Double, Double) -> Unit)? = null

    private var xMin = -10.0
    private var xMax = 10.0
    private var yMin = -10.0
    private var yMax = 10.0
    private var fitted = false
    private var traceX: Double? = null
    private var touched = false

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val gridPaint = Paint().apply {
        color = withAlpha(palette.onBackgroundVariant, 40)
        strokeWidth = 1f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(palette.onBackgroundVariant, 170)
        strokeWidth = dp(1.5f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onBackgroundVariant
        textSize = sp(11f)
    }
    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.accent
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onSelectedContainer
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(5f)), 0f)
    }
    private val intersectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onBackground
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val intersectionFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.panel }
    private val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
    private val secondTracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.onSelectedContainer }
    private val traceLinePaint = Paint().apply {
        color = withAlpha(palette.accent, 90)
        strokeWidth = dp(1f)
    }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.panel }
    private val boxTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onBackground
        textSize = sp(14f)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onBackgroundVariant
        textSize = sp(13f)
    }
    private val messagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.onBackgroundVariant
        textSize = sp(15f)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val box = RectF()

    private fun withAlpha(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    init {
        isFocusable = true
        isClickable = true
    }

    // ------------------------------------------------------------ vista

    /** Torna alla vista iniziale: x tra −10 e 10, y adattata alla curva. */
    fun resetView() {
        xMin = -10.0
        xMax = 10.0
        traceX = null
        fitY()
        invalidate()
        onViewportChanged?.invoke(xMin, xMax)
    }

    /** Nuove funzioni: si adatta l'asse y la prima volta e quando la curva di f esce del tutto dalla vista. */
    fun showFunctions(f: ((Double) -> Double)?, g: ((Double) -> Double)? = null) {
        function = f
        second = g
        if (!fitted || !curveVisible()) fitY()
        invalidate()
    }

    private fun curveVisible(): Boolean {
        val f = function ?: return true
        return (0..50).any { i ->
            val y = f(xMin + (xMax - xMin) * i / 50)
            y.isFinite() && y in yMin..yMax
        }
    }

    /** Intervallo y che contiene il 90% centrale dei valori visibili, con un margine. */
    private fun fitY() {
        val f = function
        val values = if (f == null) emptyList() else (0..200).map { f(xMin + (xMax - xMin) * it / 200) }.filter { it.isFinite() }.sorted()
        if (values.isEmpty()) {
            yMin = -10.0
            yMax = 10.0
        } else {
            // Valori enormi renderebbero infinito l'intervallo: si limitano a ±10¹⁰⁰.
            var low = values[(values.size * 0.05).toInt()].coerceIn(-1e100, 1e100)
            var high = values[min(values.size - 1, (values.size * 0.95).toInt())].coerceIn(-1e100, 1e100)
            if (high - low < 1e-9) {
                low -= 1.0
                high += 1.0
            }
            val margin = (high - low) * 0.15
            yMin = low - margin
            yMax = high + margin
        }
        fitted = f != null
    }

    private fun toScreenX(x: Double) = ((x - xMin) / (xMax - xMin) * width).toFloat()
    private fun toScreenY(y: Double) = (height - (y - yMin) / (yMax - yMin) * height).toFloat()
    private fun toGraphX(px: Float) = xMin + px / width * (xMax - xMin)
    private fun toGraphY(py: Float) = yMin + (height - py) / height * (yMax - yMin)

    // ------------------------------------------------------------ gesti

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val dx = distanceX / width * (xMax - xMin)
                val dy = distanceY / height * (yMax - yMin)
                xMin += dx
                xMax += dx
                yMin -= dy
                yMax -= dy
                invalidate()
                onViewportChanged?.invoke(xMin, xMax)
                return true
            }

            // Annuncio esplicito solo al tocco: una "live region" leggerebbe ogni piccolo spostamento del grafico.
            @Suppress("DEPRECATION")
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                traceX = toGraphX(e.x)
                invalidate()
                traceX?.let { x -> traceLabel?.invoke(x)?.let { announceForAccessibility(it) } }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetView()
                return true
            }
        },
    )

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor.toDouble()
                val width = (xMax - xMin) / factor
                val height = (yMax - yMin) / factor
                // Limiti di zoom, anche relativi alla distanza dall'origine (precisione dei double).
                val far = maxOf(abs(xMin), abs(xMax), abs(yMin), abs(yMax), 1.0)
                if (width < far * 1e-9 || height < far * 1e-9 || width > 1e12 || height > 1e12) return true
                val fx = toGraphX(detector.focusX)
                val fy = toGraphY(detector.focusY)
                xMin = fx - (fx - xMin) / factor
                xMax = fx + (xMax - fx) / factor
                yMin = fy - (fy - yMin) / factor
                yMax = fy + (yMax - fy) / factor
                invalidate()
                onViewportChanged?.invoke(xMin, xMax)
                return true
            }
        },
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        if (!touched) {
            touched = true
            invalidate()
        }
        scaler.onTouchEvent(event)
        if (!scaler.isInProgress) gestures.onTouchEvent(event)
        return true
    }

    // ------------------------------------------------------------ disegno

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        drawGrid(canvas)
        val f = function
        if (f == null) {
            if (emptyMessage.isNotEmpty()) canvas.drawText(emptyMessage, width / 2f, height / 2f, messagePaint)
            return
        }
        drawCurve(canvas, f, curvePaint)
        val g = second
        if (g != null) {
            drawCurve(canvas, g, secondPaint)
            drawIntersections(canvas, f, g)
        }
        drawTrace(canvas, f)
    }

    /** Passo "tondo" (1, 2 o 5 per una potenza di 10) per circa [divisions] divisioni. */
    private fun niceStep(range: Double, divisions: Int): Double {
        val raw = range / divisions
        val magnitude = 10.0.pow(floor(log10(raw)))
        val unit = raw / magnitude
        return magnitude * when {
            unit < 1.5 -> 1.0
            unit < 3.5 -> 2.0
            unit < 7.5 -> 5.0
            else -> 10.0
        }
    }

    private fun label(value: Double, step: Double): String {
        if (abs(value) < step * 1e-6) return "0"
        val decimals = max(0, -floor(log10(step)).toInt())
        val text = if (abs(value) >= 1e6 || abs(value) < 1e-4) {
            String.format(java.util.Locale.ROOT, "%.1e", value)
        } else {
            String.format(java.util.Locale.ROOT, "%.${decimals}f", value)
        }
        return text.replace('.', decimalSeparator).replace('-', '−')
    }

    private fun drawGrid(canvas: Canvas) {
        val xStep = niceStep(xMax - xMin, max(3, width / dp(64f).toInt()))
        val yStep = niceStep(yMax - yMin, max(3, height / dp(48f).toInt()))
        val axisX = toScreenY(0.0).coerceIn(0f, height.toFloat())
        val axisY = toScreenX(0.0).coerceIn(0f, width.toFloat())

        // Al più MAX_LINES linee: lontano dall'origine e con molto zoom x + passo può non cambiare (double).
        var x = ceil(xMin / xStep) * xStep
        var lines = 0
        while (x <= xMax && lines++ < MAX_LINES) {
            val px = toScreenX(x)
            canvas.drawLine(px, 0f, px, height.toFloat(), gridPaint)
            if (abs(x) > xStep * 1e-6) {
                val text = label(x, xStep)
                val ty = if (axisX + sp(14f) < height) axisX + sp(13f) else axisX - dp(4f)
                canvas.drawText(text, px + dp(2f), ty, labelPaint)
            }
            val next = x + xStep
            if (next == x) break
            x = next
        }
        var y = ceil(yMin / yStep) * yStep
        lines = 0
        while (y <= yMax && lines++ < MAX_LINES) {
            val py = toScreenY(y)
            canvas.drawLine(0f, py, width.toFloat(), py, gridPaint)
            if (abs(y) > yStep * 1e-6) {
                val text = label(y, yStep)
                val tx = if (axisY + dp(4f) + labelPaint.measureText(text) < width) axisY + dp(4f) else axisY - dp(4f) - labelPaint.measureText(text)
                canvas.drawText(text, tx, py - dp(2f), labelPaint)
            }
            val next = y + yStep
            if (next == y) break
            y = next
        }
        if (0.0 in yMin..yMax) canvas.drawLine(0f, toScreenY(0.0), width.toFloat(), toScreenY(0.0), axisPaint)
        if (0.0 in xMin..xMax) canvas.drawLine(toScreenX(0.0), 0f, toScreenX(0.0), height.toFloat(), axisPaint)
    }

    private fun drawCurve(canvas: Canvas, f: (Double) -> Double, paint: Paint) {
        path.reset()
        val h = height.toFloat()
        val limit = h * 4
        var drawing = false
        var previousY = 0f
        var previousX = 0.0
        val step = 1
        var px = 0
        while (px <= width) {
            val x = toGraphX(px.toFloat())
            val y = f(x)
            if (!y.isFinite()) {
                drawing = false
                px += step
                continue
            }
            val py = toScreenY(y).coerceIn(-limit, h + limit)
            if (drawing && abs(py - previousY) > h) {
                // Salto grande: se il punto di mezzo non sta tra i due è una discontinuità (asintoto).
                val middle = f((x + previousX) / 2)
                val my = if (middle.isFinite()) toScreenY(middle) else Float.NaN
                val between = my.isFinite() && my >= min(py, previousY) - 1 && my <= max(py, previousY) + 1
                if (!between) drawing = false
            }
            if (drawing) path.lineTo(px.toFloat(), py) else path.moveTo(px.toFloat(), py)
            drawing = true
            previousY = py
            previousX = x
            px += step
        }
        canvas.drawPath(path, paint)
    }

    /**
     * Punti in cui i due grafici si incontrano, cercati a ogni pixel come cambi di segno di f − g e
     * affinati per bisezione. Le discontinuità (salti più alti della vista) non contano.
     */
    private fun drawIntersections(canvas: Canvas, f: (Double) -> Double, g: (Double) -> Double) {
        fun d(x: Double) = f(x) - g(x)
        val range = yMax - yMin
        var previousX = toGraphX(0f)
        var previous = d(previousX)
        var found = 0
        var px = 1
        while (px <= width && found < MAX_INTERSECTIONS) {
            val x = toGraphX(px.toFloat())
            val current = d(x)
            val crosses = previous.isFinite() && current.isFinite() &&
                (current == 0.0 || (previous < 0) != (current < 0)) && abs(previous - current) < range
            if (crosses) {
                var low = previousX
                var high = x
                repeat(40) {
                    val mid = (low + high) / 2
                    val dm = d(mid)
                    if (!dm.isFinite()) return@repeat
                    if ((dm < 0) == (previous < 0) && dm != 0.0) low = mid else high = mid
                }
                val root = (low + high) / 2
                val y = f(root)
                if (y.isFinite() && abs(d(root)) < range * 1e-3) {
                    val cx = toScreenX(root)
                    val cy = toScreenY(y)
                    canvas.drawCircle(cx, cy, dp(5f), intersectionFill)
                    canvas.drawCircle(cx, cy, dp(5f), intersectionPaint)
                    found++
                }
            }
            previousX = x
            previous = current
            px++
        }
    }

    private companion object {
        const val MAX_LINES = 400
        const val MAX_INTERSECTIONS = 50
    }

    /** Riquadro in alto a sinistra con un testo su una riga, ridotto se non ci sta. */
    private fun drawBox(canvas: Canvas, text: String, paint: Paint) {
        val padding = dp(8f)
        val available = width - padding * 4
        val size = paint.textSize
        val natural = paint.measureText(text)
        if (natural > available) paint.textSize = size * available / natural
        val textWidth = paint.measureText(text)
        box.set(padding, padding, padding * 3 + textWidth, padding * 3 + paint.textSize)
        canvas.drawRoundRect(box, dp(10f), dp(10f), boxPaint)
        canvas.drawText(text, padding * 2, padding * 2 + paint.textSize * 0.86f, paint)
        paint.textSize = size
    }

    private fun drawTrace(canvas: Canvas, f: (Double) -> Double) {
        val x = traceX
        if (x == null) {
            if (!touched && hint.isNotEmpty()) drawBox(canvas, hint, hintPaint)
            return
        }
        val px = toScreenX(x)
        canvas.drawLine(px, 0f, px, height.toFloat(), traceLinePaint)
        val y = f(x)
        if (y.isFinite()) canvas.drawCircle(px, toScreenY(y), dp(5f), tracePaint)
        second?.invoke(x)?.takeIf { it.isFinite() }?.let { canvas.drawCircle(px, toScreenY(it), dp(5f), secondTracePaint) }
        val text = traceLabel?.invoke(x) ?: return
        drawBox(canvas, text, boxTextPaint)
    }
}
