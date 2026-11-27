package io.github.calcolatrice.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import kotlin.math.min

/**
 * Tasto disegnato a mano: forma a pillola che si "squadra" quando viene premuto (stile Material 3
 * Expressive), effetto ripple, testo che si adatta allo spazio, icona opzionale e accessibilità.
 */
class KeyButton(context: Context) : View(context) {

    var key: PadKey? = null
        private set

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val shape = GradientDrawable()
    private val mask = GradientDrawable().apply { setColor(Color.WHITE) }
    private var icon: Drawable? = null
    private var maxTextSize = sp(28f)
    private val iconSize = dp(26f)

    private var cornerFraction = REST_CORNER
    private var targetCornerFraction = REST_CORNER
    private var cornerAnimator: ValueAnimator? = null

    /** Evita di reagire a cambi di stato chiamati dal costruttore di View prima dell'inizializzazione. */
    private var ready = false

    init {
        isClickable = true
        isFocusable = true
        isHapticFeedbackEnabled = true
        ready = true
    }

    fun setTextSizeSp(size: Float) {
        maxTextSize = sp(size)
        invalidate()
    }

    fun bind(key: PadKey, palette: Palette) {
        this.key = key
        val (container, content) = palette.forKey(key)
        shape.setColor(container)
        textPaint.color = content
        icon = key.icon?.let { id -> context.getDrawable(id)?.mutate()?.apply { setTint(content) } }
        val rippleColor = Color.argb(40, Color.red(content), Color.green(content), Color.blue(content))
        background = RippleDrawable(ColorStateList.valueOf(rippleColor), shape, mask)
        contentDescription = key.description ?: key.label
        isSelected = key.active
        isLongClickable = key.longPressAction != null
        applyCorner()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (ready) applyCorner()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (!ready) return
        val target = if (isPressed) PRESSED_CORNER else REST_CORNER
        if (target == targetCornerFraction) return
        targetCornerFraction = target
        cornerAnimator?.cancel()
        cornerAnimator = ValueAnimator.ofFloat(cornerFraction, target).apply {
            // Molla: si squadra in fretta e torna tonda con un piccolo rimbalzo (Material 3 Expressive).
            duration = if (target == PRESSED_CORNER) 120 else 320
            interpolator = if (target == PRESSED_CORNER) DecelerateInterpolator() else SpringInterpolator()
            addUpdateListener {
                cornerFraction = it.animatedValue as Float
                applyCorner()
            }
            start()
        }
    }

    private fun applyCorner() {
        val radius = min(width, height) * cornerFraction
        shape.cornerRadius = radius
        mask.cornerRadius = radius
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val drawable = icon
        if (drawable != null) {
            val half = (min(iconSize, min(width, height) * 0.6f) / 2f).toInt()
            drawable.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
            drawable.draw(canvas)
            return
        }
        val label = key?.label ?: return
        // Riduce il testo se non entra nel tasto.
        textPaint.textSize = min(maxTextSize, height * 0.5f)
        val available = width * 0.8f
        val measured = textPaint.measureText(label)
        if (measured > available && measured > 0f) textPaint.textSize *= available / measured
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, baseline, textPaint)
    }

    /** TalkBack lo annuncia come pulsante. */
    override fun getAccessibilityClassName(): CharSequence = Button::class.java.name

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val REST_CORNER = 0.5f
        const val PRESSED_CORNER = 0.28f
    }
}
