package io.github.calcolatrice

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.DisplayFormatter
import io.github.calcolatrice.engine.SolutionFormatter
import io.github.calcolatrice.engine.SpokenMath
import io.github.calcolatrice.ui.Chip
import io.github.calcolatrice.ui.HardwareKeys
import io.github.calcolatrice.ui.applyColorTheme
import io.github.calcolatrice.ui.KeyFactory
import io.github.calcolatrice.ui.KeyPad
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.PadKey
import io.github.calcolatrice.ui.Palette
import io.github.calcolatrice.ui.Settings
import io.github.calcolatrice.ui.SheetCommand
import io.github.calcolatrice.ui.copyText
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.localFunctionNames
import io.github.calcolatrice.ui.padForSystemBars
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.setupEdgeToEdge
import java.text.DecimalFormatSymbols

/**
 * Base delle schermate con caselle da compilare (equazioni, funzioni, statistica): impostazioni
 * condivise, barra in alto, disposizione in verticale e in orizzontale, pannelli di strumenti,
 * copia dei risultati e tastiera fisica. Solo View di sistema, nessuna libreria.
 */
abstract class SheetActivity : Activity() {

    protected lateinit var prefs: SharedPreferences
    protected lateinit var settings: Settings
    protected lateinit var palette: Palette
    protected lateinit var keys: KeyFactory
    protected lateinit var pad: KeyPad
    protected lateinit var display: DisplayFormatter
    protected lateinit var solutions: SolutionFormatter
    protected var decimalSeparator = '.'
    protected var landscape = false

    /** File SharedPreferences della schermata. */
    protected abstract val prefsName: String

    /** Titolo usato negli appunti quando si copiano i risultati. */
    protected abstract val clipboardLabel: Int

    protected abstract fun onAction(action: PadAction)

    /** Testo da copiare (Ctrl+C o pressione prolungata sui risultati); null se non c'è nulla. */
    protected abstract fun resultsText(): CharSequence?

    override fun onCreate(savedInstanceState: Bundle?) {
        applyColorTheme()
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        settings = Settings(this)
        palette = Palette(this)
        keys = KeyFactory(this)
        pad = KeyPad(this, palette, ::onAction)
        val symbols = DecimalFormatSymbols.getInstance(resources.configuration.locales[0])
        decimalSeparator = symbols.decimalSeparator
        display = DisplayFormatter(symbols.decimalSeparator, symbols.groupingSeparator, localFunctionNames())
        solutions = SolutionFormatter(display, settings.numberFormat)
        val config = resources.configuration
        landscape = config.screenWidthDp > config.screenHeightDp
    }

    protected fun copyResults() {
        val text = resultsText()?.takeIf { it.isNotEmpty() } ?: return
        copyText(getString(clipboardLabel), text)
    }

    // ---------------------------------------------------------------- layout

    /**
     * Schermata completa: [editor] sopra e [keypad] sotto, oppure affiancati in orizzontale.
     * Su tablet in verticale il contenuto resta centrato con una larghezza comoda.
     */
    protected fun scaffold(editor: View, keypad: View, editorWeight: Float = 1f): View {
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val width = if (!landscape && resources.configuration.screenWidthDp > 600) dp(600) else MATCH_PARENT
        root.addView(column, FrameLayout.LayoutParams(width, MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
        if (landscape) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(editor, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            row.addView(keypad, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(16) })
            column.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            column.addView(editor, LinearLayout.LayoutParams(MATCH_PARENT, 0, editorWeight))
            column.addView(keypad, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        }
        // Edge-to-edge: il contenuto evita barre di sistema e notch.
        root.padForSystemBars(dp(16), dp(8))
        return root
    }

    /** Barra in alto: tasto indietro, titolo e, a destra, eventuali chip (Frazioni, DEG/RAD). */
    protected fun topBar(title: View, vararg trailing: View): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(pad.button(keys.backKey(), 16f), LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        trailing.forEach { bar.addView(it, LinearLayout.LayoutParams(WRAP_CONTENT, dp(32)).apply { marginStart = dp(8) }) }
        return bar
    }

    /** Chip "Frazioni" della barra in alto. */
    protected fun fractionChip(): Chip = io.github.calcolatrice.ui.fractionChip(this, palette) { onAction(PadAction.ToggleFraction) }

    /** Testo per TalkBack: le formule lette a parole. */
    protected fun spoken(text: CharSequence): String =
        SpokenMath.describe(text, resources.configuration.locales[0].language)

    protected fun titleView(text: CharSequence = "") = TextView(this).apply {
        this.text = text
        textSize = 22f
        setTextColor(palette.onBackground)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(8), 0, dp(8), 0)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    /**
     * Tastiera: una o più righe di strumenti su fondo arrotondato, poi il tastierino principale.
     */
    protected fun keypad(toolRows: List<List<PadKey>>, main: List<List<PadKey>>, mainTextSp: Float = 28f): View {
        val keypad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolRows.forEachIndexed { index, row ->
            val panel = FrameLayout(this).apply {
                background = roundedDrawable(palette.panel, dp(28).toFloat())
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            val (rowView, _) = pad.row(row, dp(4), 17f)
            panel.addView(rowView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            keypad.addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, dp(TOOL_ROW_HEIGHT)).apply { if (index > 0) topMargin = dp(6) })
        }
        val (grid, _) = pad.grid(main, dp(if (landscape) 6 else 8), if (landscape) 22f else mainTextSp)
        keypad.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(8) })
        return keypad
    }

    // ---------------------------------------------------------------- tastiera fisica

    /** Tab passa alla casella successiva (Maiusc+Tab alla precedente), Invio calcola, Ctrl+C copia. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isMetaPressed) {
            if (keyCode != KeyEvent.KEYCODE_C) return super.onKeyDown(keyCode, event)
            copyResults()
            return true
        }
        val action = when (keyCode) {
            KeyEvent.KEYCODE_TAB -> PadAction.Sheet(
                if (event.isShiftPressed) SheetCommand.PREVIOUS_CELL else SheetCommand.NEXT_CELL,
            )
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> PadAction.Input(CalcKey.Equals)
            KeyEvent.KEYCODE_DEL -> PadAction.Input(CalcKey.Backspace)
            KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_ESCAPE -> PadAction.Input(CalcKey.Clear)
            else -> characterKey(event.unicodeChar.toChar())?.let { PadAction.Input(it) }
        } ?: return super.onKeyDown(keyCode, event)
        onAction(action)
        return true
    }

    /** Tasto per un carattere digitato sulla tastiera fisica. */
    protected open fun characterKey(c: Char): CalcKey? = HardwareKeys.basic(c)

    private companion object {
        const val TOOL_ROW_HEIGHT = 46
    }
}
