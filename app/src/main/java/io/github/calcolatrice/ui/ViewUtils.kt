package io.github.calcolatrice.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import android.view.WindowInsets
import io.github.calcolatrice.R
import io.github.calcolatrice.engine.CalcError
import kotlin.math.max
import kotlin.math.roundToInt

/** Utilità di disegno e layout condivise dalle schermate (nessuna libreria). */

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun roundedDrawable(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}

/** Effetto ripple del colore indicato (trasparente), senza sfondo. */
fun rippleDrawable(color: Int, radius: Float): Drawable =
    RippleDrawable(ColorStateList.valueOf(rippleColor(color)), null, roundedDrawable(Color.WHITE, radius))

fun rippleColor(color: Int): Int = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))

/** Edge-to-edge: il contenuto passa sotto le barre di sistema, che restano trasparenti. */
@Suppress("DEPRECATION")
fun Activity.setupEdgeToEdge() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
    } else {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (!night) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }
}

/** Spazio occupato da barre di sistema e notch: sinistra, sopra, destra, sotto. */
@Suppress("DEPRECATION")
fun systemBarInsets(insets: WindowInsets): IntArray {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return intArrayOf(i.left, i.top, i.right, i.bottom)
    }
    val result = intArrayOf(
        insets.systemWindowInsetLeft,
        insets.systemWindowInsetTop,
        insets.systemWindowInsetRight,
        insets.systemWindowInsetBottom,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        insets.displayCutout?.let { cutout ->
            result[0] = max(result[0], cutout.safeInsetLeft)
            result[1] = max(result[1], cutout.safeInsetTop)
            result[2] = max(result[2], cutout.safeInsetRight)
            result[3] = max(result[3], cutout.safeInsetBottom)
        }
    }
    return result
}

/** Imposta come padding lo spazio di barre e notch più un margine fisso. */
fun View.padForSystemBars(horizontal: Int, vertical: Int) {
    setOnApplyWindowInsetsListener { view, insets ->
        val bars = systemBarInsets(insets)
        view.setPadding(bars[0] + horizontal, bars[1] + vertical, bars[2] + horizontal, bars[3] + vertical)
        insets
    }
}

/** Nomi delle funzioni che cambiano con la lingua (MCD e mcm in italiano), per [io.github.calcolatrice.engine.DisplayFormatter]. */
fun Context.localFunctionNames(): Map<String, String> = mapOf(
    "gcd" to getString(R.string.fn_gcd),
    "lcm" to getString(R.string.fn_lcm),
)

/** Messaggio da mostrare per un errore di calcolo. */
fun errorMessage(error: CalcError): Int = when (error) {
    CalcError.DIVISION_BY_ZERO -> R.string.error_division_by_zero
    CalcError.UNDEFINED -> R.string.error_undefined
    CalcError.OVERFLOW -> R.string.error_overflow
    CalcError.SYNTAX -> R.string.error_syntax
    CalcError.NO_SOLUTION -> R.string.error_no_solution
}

/** Sfondo di una cella di tabella: bordo colorato se selezionata o in errore. */
fun Context.cellBackground(palette: Palette, selected: Boolean, error: Boolean): Drawable {
    val shape = GradientDrawable().apply {
        setColor(palette.panel)
        cornerRadius = dp(12).toFloat()
        when {
            error -> setStroke(dp(2), palette.error)
            selected -> setStroke(dp(2), palette.accent)
        }
    }
    return RippleDrawable(ColorStateList.valueOf(rippleColor(palette.onBackground)), shape, null)
}
