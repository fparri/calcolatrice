package com.example.calculatorlite

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val engine = CalculatorEngine()
    private lateinit var historyView: TextView
    private lateinit var displayView: TextView
    private lateinit var palette: Palette

    private data class Palette(
        val surface: Int,
        val displayBg: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val numBg: Int,
        val numFg: Int,
        val opBg: Int,
        val opFg: Int,
        val acBg: Int,
        val acFg: Int,
        val eqBg: Int,
        val eqFg: Int,
        val ripple: Int,
        val error: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        palette = buildPalette(isDark)

        setupEdgeToEdge(isDark)
        setContentView(buildUi())
        refreshDisplay()
    }

    @Suppress("DEPRECATION")
    private fun setupEdgeToEdge(isDark: Boolean) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(
                if (isDark) 0 else mask,
                mask
            )
        } else {
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (!isDark) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun buildPalette(isDark: Boolean): Palette {
        // Dynamic Color nativo di Android 12+ senza librerie esterne
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (isDark) {
                Palette(
                    surface = getColor(android.R.color.system_neutral1_900),
                    displayBg = getColor(android.R.color.system_neutral1_800),
                    onSurface = getColor(android.R.color.system_neutral1_50),
                    onSurfaceVariant = getColor(android.R.color.system_neutral2_300),
                    numBg = getColor(android.R.color.system_neutral1_800),
                    numFg = getColor(android.R.color.system_neutral1_50),
                    opBg = getColor(android.R.color.system_accent2_700),
                    opFg = getColor(android.R.color.system_accent2_100),
                    acBg = getColor(android.R.color.system_accent3_700),
                    acFg = getColor(android.R.color.system_accent3_100),
                    eqBg = getColor(android.R.color.system_accent1_200),
                    eqFg = getColor(android.R.color.system_accent1_900),
                    ripple = Color.argb(45, 255, 255, 255),
                    error = Color.parseColor("#F2B8B5")
                )
            } else {
                Palette(
                    surface = getColor(android.R.color.system_neutral1_10),
                    displayBg = getColor(android.R.color.system_neutral1_100),
                    onSurface = getColor(android.R.color.system_neutral1_900),
                    onSurfaceVariant = getColor(android.R.color.system_neutral2_600),
                    numBg = getColor(android.R.color.system_neutral1_50),
                    numFg = getColor(android.R.color.system_neutral1_900),
                    opBg = getColor(android.R.color.system_accent2_100),
                    opFg = getColor(android.R.color.system_accent2_900),
                    acBg = getColor(android.R.color.system_accent3_100),
                    acFg = getColor(android.R.color.system_accent3_900),
                    eqBg = getColor(android.R.color.system_accent1_600),
                    eqFg = Color.WHITE,
                    ripple = Color.argb(35, 0, 0, 0),
                    error = Color.parseColor("#B3261E")
                )
            }
        }

        // Fallback Material 3 per Android 8–11
        return if (isDark) {
            Palette(
                surface = Color.parseColor("#121316"),
                displayBg = Color.parseColor("#1E2025"),
                onSurface = Color.parseColor("#E3E2E6"),
                onSurfaceVariant = Color.parseColor("#C4C6D0"),
                numBg = Color.parseColor("#1E2025"),
                numFg = Color.parseColor("#E3E2E6"),
                opBg = Color.parseColor("#3E4759"),
                opFg = Color.parseColor("#DAE2F9"),
                acBg = Color.parseColor("#573E5C"),
                acFg = Color.parseColor("#FAD8FD"),
                eqBg = Color.parseColor("#ADC6FF"),
                eqFg = Color.parseColor("#002E69"),
                ripple = Color.argb(45, 255, 255, 255),
                error = Color.parseColor("#F2B8B5")
            )
        } else {
            Palette(
                surface = Color.parseColor("#FAF9FD"),
                displayBg = Color.parseColor("#ECEEF4"),
                onSurface = Color.parseColor("#1A1B20"),
                onSurfaceVariant = Color.parseColor("#44474E"),
                numBg = Color.parseColor("#F1F3F9"),
                numFg = Color.parseColor("#1A1B20"),
                opBg = Color.parseColor("#D8E2FF"),
                opFg = Color.parseColor("#001A41"),
                acBg = Color.parseColor("#FAD8FD"),
                acFg = Color.parseColor("#28132E"),
                eqBg = Color.parseColor("#1A73E8"),
                eqFg = Color.WHITE,
                ripple = Color.argb(35, 0, 0, 0),
                error = Color.parseColor("#B3261E")
            )
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.surface)
        }

        val displayRadius = dp(28f).toFloat()
        val displayBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.END
            background = GradientDrawable().apply {
                setColor(palette.displayBg)
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    0f, 0f,
                    displayRadius, displayRadius,
                    displayRadius, displayRadius
                )
            }
            setPadding(dp(24f), dp(24f), dp(24f), dp(24f))
        }

        historyView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(palette.onSurfaceVariant)
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(8f))
        }

        displayView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 62f)
            setTextColor(palette.onSurface)
            gravity = Gravity.END
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        displayBox.addView(
            historyView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        displayBox.addView(
            displayView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            displayBox,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val keypad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(20f), dp(16f), dp(20f))
        }

        val rows = listOf(
            listOf(
                KeySpec("AC", palette.acBg, palette.acFg) { engine.onClear() },
                KeySpec("±", palette.opBg, palette.opFg) { engine.onToggleSign() },
                KeySpec("%", palette.opBg, palette.opFg) { engine.onPercentage() },
                KeySpec("÷", palette.opBg, palette.opFg) { engine.onOperation(Operation.DIVIDE) }
            ),
            listOf(
                KeySpec("7", palette.numBg, palette.numFg) { engine.onNumber(7) },
                KeySpec("8", palette.numBg, palette.numFg) { engine.onNumber(8) },
                KeySpec("9", palette.numBg, palette.numFg) { engine.onNumber(9) },
                KeySpec("×", palette.opBg, palette.opFg) { engine.onOperation(Operation.MULTIPLY) }
            ),
            listOf(
                KeySpec("4", palette.numBg, palette.numFg) { engine.onNumber(4) },
                KeySpec("5", palette.numBg, palette.numFg) { engine.onNumber(5) },
                KeySpec("6", palette.numBg, palette.numFg) { engine.onNumber(6) },
                KeySpec("−", palette.opBg, palette.opFg) { engine.onOperation(Operation.SUBTRACT) }
            ),
            listOf(
                KeySpec("1", palette.numBg, palette.numFg) { engine.onNumber(1) },
                KeySpec("2", palette.numBg, palette.numFg) { engine.onNumber(2) },
                KeySpec("3", palette.numBg, palette.numFg) { engine.onNumber(3) },
                KeySpec("+", palette.opBg, palette.opFg) { engine.onOperation(Operation.ADD) }
            ),
            listOf(
                KeySpec("0", palette.numBg, palette.numFg) { engine.onNumber(0) },
                KeySpec(".", palette.numBg, palette.numFg) { engine.onDecimal() },
                KeySpec("⌫", palette.numBg, palette.numFg) { engine.onDelete() },
                KeySpec("=", palette.eqBg, palette.eqFg) { engine.onCalculate() }
            )
        )

        val gap = dp(12f)
        rows.forEachIndexed { rowIndex, rowKeys ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) topMargin = gap
            }

            rowKeys.forEachIndexed { colIndex, spec ->
                val keyView = SquareKeyView(this).apply {
                    text = spec.label
                    setTextColor(spec.fg)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    background = createCircleRipple(spec.bg, palette.ripple)
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        spec.action()
                        refreshDisplay()
                    }
                }
                val keyParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    if (colIndex > 0) marginStart = gap
                }
                rowLayout.addView(keyView, keyParams)
            }
            keypad.addView(rowLayout, rowParams)
        }

        root.addView(
            keypad,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                topInset = bars.top
                bottomInset = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            displayBox.setPadding(dp(24f), topInset + dp(16f), dp(24f), dp(24f))
            keypad.setPadding(dp(16f), dp(20f), dp(16f), bottomInset + dp(20f))
            insets
        }

        return root
    }

    private fun refreshDisplay() {
        historyView.text = engine.history
        historyView.visibility = if (engine.history.isEmpty()) View.GONE else View.VISIBLE

        val text = engine.displayText
        displayView.text = text
        displayView.setTextColor(if (engine.isError) palette.error else palette.onSurface)

        val sizeSp = when {
            text.length > 14 -> 34f
            text.length > 9 -> 46f
            else -> 62f
        }
        displayView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }

    private fun createCircleRipple(bgColor: Int, rippleColor: Int): RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(bgColor)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).toInt()

    private data class KeySpec(
        val label: String,
        val bg: Int,
        val fg: Int,
        val action: () -> Unit
    )

    private class SquareKeyView(context: Context) : TextView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        }
    }
}
