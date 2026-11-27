package io.github.calcolatrice

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.CalculatorSession
import io.github.calcolatrice.engine.CalculatorState
import io.github.calcolatrice.engine.DisplayFormatter
import io.github.calcolatrice.engine.HistoryEntry
import io.github.calcolatrice.engine.NumberFormat
import io.github.calcolatrice.engine.ResultFormatter
import io.github.calcolatrice.engine.SolutionFormatter
import io.github.calcolatrice.engine.VARIABLES
import io.github.calcolatrice.engine.SpokenMath
import io.github.calcolatrice.ui.Chip
import io.github.calcolatrice.ui.ColorTheme
import io.github.calcolatrice.ui.FitTextView
import io.github.calcolatrice.ui.LauncherIcon
import io.github.calcolatrice.ui.applyColorTheme
import io.github.calcolatrice.ui.stackedFractions
import io.github.calcolatrice.ui.HardwareKeys
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.KeyFactory
import io.github.calcolatrice.ui.KeyPad
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.Palette
import io.github.calcolatrice.ui.Settings
import io.github.calcolatrice.ui.copyText
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
import io.github.calcolatrice.ui.localFunctionNames
import io.github.calcolatrice.ui.mathEntries
import io.github.calcolatrice.ui.showMathMenu
import io.github.calcolatrice.ui.padForSystemBars
import io.github.calcolatrice.ui.rippleDrawable
import io.github.calcolatrice.ui.roundedDrawable
import io.github.calcolatrice.ui.setupEdgeToEdge
import io.github.calcolatrice.ui.showNumberFormatDialog
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

/**
 * Schermata principale, costruita solo con le View del sistema Android (nessuna libreria).
 * In verticale: display, pannello scientifico a scomparsa e tastierino.
 * In orizzontale (e su tablet ruotati): pannello scientifico e tastierino affiancati.
 */
class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var prefs: SharedPreferences
    private lateinit var session: CalculatorSession
    private lateinit var palette: Palette
    private lateinit var keys: KeyFactory
    private lateinit var pad: KeyPad
    private lateinit var formatter: DisplayFormatter
    private lateinit var plainFormatter: DisplayFormatter
    private var numberFormat: NumberFormat = NumberFormat.Normal
    private var asFraction = false
    private var decimalSeparator = '.'
    private var scientificExpanded = false
    private var landscape = false

    private lateinit var previousView: TextView
    private lateinit var mainView: FitTextView
    private lateinit var secondaryView: FitTextView
    private lateinit var angleChip: Chip
    private lateinit var fractionChip: Chip
    private lateinit var menuButton: View

    /** Cronologia sempre visibile a fianco della calcolatrice, sugli schermi larghi (tablet, pieghevoli). */
    private var historyPane: LinearLayout? = null
    private var shownHistory: List<HistoryEntry>? = null
    private lateinit var mainGridView: View
    private var scientificButtons: List<List<KeyButton>> = emptyList()
    private var expandButton: KeyButton? = null
    private var scientificExtraRows: View? = null

    /** Posizioni nel testo del display, per spostare il cursore toccando lo schermo. */
    private var displayPositions: IntArray? = null
    private var cursorGlyphAt = -1

    /** Inizio della parte di espressione mostrata quando non sta tutta sulla riga (0 = dall'inizio). */
    private var windowStart = 0

    /** Differenza tra una posizione nel testo mostrato e la stessa nel testo completo (per i tocchi). */
    private var windowShift = 0
    private var touchX = 0f
    private var touchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        applyColorTheme()
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        settings = Settings(this)
        prefs = settings.prefs
        palette = Palette(this)
        keys = KeyFactory(this)
        pad = KeyPad(this, palette, ::onAction)
        val symbols = DecimalFormatSymbols.getInstance(resources.configuration.locales[0])
        decimalSeparator = symbols.decimalSeparator
        formatter = DisplayFormatter(symbols.decimalSeparator, symbols.groupingSeparator, localFunctionNames())
        plainFormatter = DisplayFormatter(symbols.decimalSeparator, null, localFunctionNames(), scientificPowers = false)
        numberFormat = settings.numberFormat
        asFraction = prefs.getBoolean(PREF_FRACTION, false)

        session = restoreSession()
        scientificExpanded = savedInstanceState?.getBoolean(KEY_SCIENTIFIC_EXPANDED) ?: false
        val config = resources.configuration
        landscape = config.screenWidthDp > config.screenHeightDp

        setContentView(buildContent())
        render()
        if (savedInstanceState == null) openShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openShortcut(intent)
    }

    /** Le scorciatoie dell'icona aprono questa schermata, che passa a quella richiesta. */
    private fun openShortcut(intent: Intent?) {
        val target = when (intent?.action) {
            ACTION_EQUATIONS -> EquationActivity::class.java
            ACTION_STATISTICS -> StatisticsActivity::class.java
            ACTION_FUNCTIONS -> FunctionActivity::class.java
            else -> return
        }
        startActivity(Intent(this, target))
    }

    override fun onResume() {
        super.onResume()
        // Le altre schermate possono aver salvato valori nelle variabili o cambiato il formato.
        session.setVariables(settings.variables)
        numberFormat = settings.numberFormat
        render()
    }

    override fun onPause() {
        super.onPause()
        saveState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SCIENTIFIC_EXPANDED, scientificExpanded)
    }

    // ---------------------------------------------------------------- azioni

    private fun onAction(action: PadAction) {
        when (action) {
            is PadAction.Input -> if (session.onKey(action.key)) saveHistory()
            PadAction.ToggleInverse -> session.inverse = !session.inverse
            PadAction.ToggleAngle -> {
                session.toggleAngleUnit()
                prefs.edit().putString(PREF_ANGLE, session.angleUnit.name).apply()
            }
            PadAction.ToggleScientific -> scientificExpanded = !scientificExpanded
            PadAction.ShowHistory -> showHistory()
            PadAction.ShowMenu -> showMenu()
            PadAction.ShowVariables -> showVariables()
            PadAction.ShowMath -> showMathMenu(mathEntries(formatter), palette) { onAction(PadAction.Input(it)) }
            PadAction.ToggleFraction -> {
                asFraction = !asFraction
                prefs.edit().putBoolean(PREF_FRACTION, asFraction).apply()
            }
            // Azioni delle altre schermate.
            PadAction.Back, is PadAction.Sheet, is PadAction.SetEquationMode, is PadAction.SetStatMode,
            is PadAction.SetFunctionTab -> Unit
        }
        render()
    }

    /** Tastiere fisiche: tablet, Chromebook, tastiere Bluetooth. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isMetaPressed) {
            if (keyCode != KeyEvent.KEYCODE_C) return super.onKeyDown(keyCode, event)
            copyResult()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                session.moveCursor(if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                render()
                return true
            }
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END -> {
                session.moveCursorTo(if (keyCode == KeyEvent.KEYCODE_MOVE_HOME) 0 else Int.MAX_VALUE)
                render()
                return true
            }
        }
        val key = hardwareKey(keyCode, event) ?: return super.onKeyDown(keyCode, event)
        onAction(PadAction.Input(key))
        return true
    }

    private fun hardwareKey(keyCode: Int, event: KeyEvent): CalcKey? = when (keyCode) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> CalcKey.Equals
        KeyEvent.KEYCODE_DEL -> CalcKey.Backspace
        KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_ESCAPE -> CalcKey.Clear
        else -> {
            val c = event.unicodeChar.toChar()
            HardwareKeys.basic(c) ?: scientificKey(c)
        }
    }

    private fun scientificKey(c: Char): CalcKey? = when (c) {
        // Lettere maiuscole: variabili (E = ×10ⁿ). Minuscole: funzioni scientifiche (rispettano il tasto INV).
        in VARIABLES -> CalcKey.Variable(c)
        'E' -> CalcKey.Exponent
        's' -> CalcKey.Function(if (session.inverse) "asin" else "sin")
        'c' -> CalcKey.Function(if (session.inverse) "acos" else "cos")
        't' -> CalcKey.Function(if (session.inverse) "atan" else "tan")
        'l' -> if (session.inverse) CalcKey.ExpE else CalcKey.Function("ln")
        'g' -> if (session.inverse) CalcKey.Exp10 else CalcKey.Function("log")
        'e' -> CalcKey.Constant('e')
        ';' -> CalcKey.Separator
        else -> null
    }

    private fun copyResult() {
        val calc = session.calc
        val ans = calc.ans
        val preview = session.preview
        val value = when {
            calc.justEvaluated && ans != null -> ResultFormatter.toInternal(ans)
            preview != null -> ResultFormatter.toInternal(preview)
            else -> calc.expression.ifEmpty { "0" }
        }
        copyText(getString(R.string.app_name), plainFormatter.format(value, ans))
    }

    /** Tocco sul display: sposta il cursore nel punto toccato. */
    private fun placeCursorAtTouch() {
        val calc = session.calc
        if (calc.expression.isEmpty()) return
        val positions = displayPositions
        if (calc.justEvaluated || positions == null) {
            // Il risultato diventa testo modificabile, con il cursore in fondo.
            session.moveCursorTo(Int.MAX_VALUE)
            render()
            return
        }
        var offset = mainView.getOffsetForPosition(touchX, touchY)
        if (cursorGlyphAt in 0 until offset) offset-- // il cursore disegnato occupa un carattere
        // Con l'espressione accorciata la posizione si riporta al testo completo; toccare "…" porta oltre il bordo.
        val target = offset + windowShift
        val index = positions.indices.minByOrNull { kotlin.math.abs(positions[it] - target) } ?: return
        session.moveCursorTo(index)
        render()
    }

    // ---------------------------------------------------------------- disegno

    private fun render() {
        val calc = session.calc
        val solutions = SolutionFormatter(formatter, numberFormat)
        val ans = calc.ans
        val showsResult = calc.justEvaluated && calc.expression == "@" && ans != null
        displayPositions = null
        cursorGlyphAt = -1
        windowShift = 0
        val main: CharSequence = when {
            showsResult -> {
                val value = solutions.value(ans, asFraction)
                windowStart = 0
                val shown = displayText(value, cursorAt = null)
                if (asFraction) stackedFractions(shown) else shown
            }
            calc.expression.isEmpty() -> {
                windowStart = 0
                "0"
            }
            else -> {
                val layout = formatter.layout(calc.expression, ans)
                displayPositions = layout.positions
                // Cursore visibile solo quando non è in fondo.
                val cursor = calc.cursor?.let { layout.positions[it.coerceIn(0, layout.positions.size - 1)] }
                displayText(layout.text, cursor)
            }
        }
        mainView.text = main
        mainView.contentDescription = spoken(main.toString().replace(CURSOR, ""))

        val last = session.lastExpression
        val previous = last?.let { formatter.format(it) + " =" }
        previousView.text = previous.orEmpty()
        previousView.visibility = if (previous == null) View.GONE else View.VISIBLE

        val error = calc.error?.let { getString(errorMessage(it)) }
        val preview = session.preview
            ?.let { solutions.value(it, asFraction) }
            ?.takeIf { it != main.toString() }
        secondaryView.text = error ?: preview?.let { if (asFraction) stackedFractions(it) else it } ?: ""
        secondaryView.setTextColor(if (error != null) palette.error else palette.onBackgroundVariant)
        secondaryView.contentDescription = spoken(secondaryView.text)

        val degrees = session.angleUnit == AngleUnit.DEGREES
        val angleName = getString(if (degrees) R.string.angle_degrees else R.string.angle_radians)
        angleChip.update(getString(if (degrees) R.string.angle_degrees_short else R.string.angle_radians_short), checked = false)
        angleChip.contentDescription = getString(R.string.angle_toggle, angleName)
        fractionChip.update(getString(R.string.fraction_short), asFraction)
        renderHistoryPane()

        val specs = if (landscape) keys.scientificLandscape(session.inverse) else keys.scientificPortrait(session.inverse)
        scientificButtons.zip(specs).forEach { (buttons, row) ->
            buttons.zip(row).forEach { (button, key) -> if (button.key != key) button.bind(key, palette) }
        }

        if (!landscape) {
            val expandKey = keys.expandKey(scientificExpanded)
            expandButton?.let { if (it.key != expandKey) it.bind(expandKey, palette) }
            scientificExtraRows?.visibility = if (scientificExpanded) View.VISIBLE else View.GONE
            val params = mainGridView.layoutParams as LinearLayout.LayoutParams
            val weight = if (scientificExpanded) 1.6f else 2f
            if (params.weight != weight) {
                params.weight = weight
                mainGridView.layoutParams = params
            }
        }
    }

    /**
     * Testo del display principale, sempre su una riga: le cifre si rimpiccioliscono fino al minimo leggibile,
     * poi si mostra la parte attorno al cursore (o la fine), con "…" dove l'espressione è tagliata.
     */
    private fun displayText(text: String, cursorAt: Int?): CharSequence {
        val focus = cursorAt ?: text.length
        val window = mainView.fittingWindow(text, focus, windowStart, extra = if (cursorAt != null) CURSOR else "")
        val start = window?.start ?: 0
        val end = window?.end ?: text.length
        windowStart = start
        val prefix = if (start > 0) FitTextView.ELLIPSIS else ""
        val suffix = if (end < text.length) FitTextView.ELLIPSIS else ""
        windowShift = start - prefix.length
        val visible = prefix + text.substring(start, end) + suffix
        if (cursorAt == null) return visible
        val at = (cursorAt - start + prefix.length).coerceIn(prefix.length, prefix.length + end - start)
        cursorGlyphAt = at
        return SpannableStringBuilder(visible).apply {
            insert(at, CURSOR)
            setSpan(ForegroundColorSpan(palette.accent), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val config = resources.configuration
        if (config.screenWidthDp >= WIDE_SCREEN_DP) {
            // Schermi larghi (tablet, pieghevoli aperti): calcolatrice a sinistra, cronologia a destra.
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(column, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            row.addView(buildHistoryPane(), LinearLayout.LayoutParams(dp(HISTORY_PANE_DP), MATCH_PARENT).apply { marginStart = dp(24) })
            root.addView(row, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        } else {
            // Su tablet in verticale il contenuto resta centrato con una larghezza comoda.
            val width = if (!landscape && config.screenWidthDp > 600) dp(600) else MATCH_PARENT
            root.addView(column, FrameLayout.LayoutParams(width, MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
        }

        column.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))
        column.addView(buildDisplay(), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        column.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))

        if (landscape) {
            val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val panel = FrameLayout(this).apply {
                background = roundedDrawable(palette.panel, dp(28).toFloat())
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            val (scientificGrid, buttons) = pad.grid(
                keys.scientificLandscape(session.inverse), dp(4), 18f,
            )
            scientificButtons = buttons
            panel.addView(scientificGrid, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            bottom.addView(panel, LinearLayout.LayoutParams(0, MATCH_PARENT, 4f))
            bottom.addView(View(this), LinearLayout.LayoutParams(dp(12), MATCH_PARENT))
            val (mainGrid, _) = pad.grid(keys.main(decimalSeparator), dp(6), 22f)
            mainGridView = mainGrid
            bottom.addView(mainGrid, LinearLayout.LayoutParams(0, MATCH_PARENT, 4f))
            column.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, 0, 2.2f))
        } else {
            column.addView(buildScientificPanel(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            column.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(12)))
            val (mainGrid, _) = pad.grid(keys.main(decimalSeparator), dp(KEY_SPACING), 30f)
            mainGridView = mainGrid
            column.addView(mainGrid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 2f))
        }

        // Edge-to-edge: il contenuto evita barre di sistema e notch.
        root.padForSystemBars(dp(16), dp(8))
        return root
    }

    /** Barra in alto: DEG/RAD e Frazioni come chip a sinistra, cronologia e menu a destra. */
    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        angleChip = Chip(this, palette, checkable = false).apply { setOnClickListener { onAction(PadAction.ToggleAngle) } }
        bar.addView(angleChip, LinearLayout.LayoutParams(WRAP_CONTENT, dp(32)))
        fractionChip = Chip(this, palette, checkable = true).apply {
            setOnClickListener { onAction(PadAction.ToggleFraction) }
            // TalkBack legge cosa fa, non solo l'etichetta.
            contentDescription = getString(R.string.show_fractions)
        }
        bar.addView(fractionChip, LinearLayout.LayoutParams(WRAP_CONTENT, dp(32)).apply { marginStart = dp(8) })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        // Sugli schermi larghi la cronologia è già a fianco.
        if (resources.configuration.screenWidthDp < WIDE_SCREEN_DP) {
            bar.addView(pad.button(keys.historyKey(), 16f), LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        menuButton = pad.button(keys.menuKey(), 16f)
        bar.addView(menuButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        return bar
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildDisplay(): View {
        val display = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.END
            setPadding(dp(8), 0, dp(8), 0)
            setOnLongClickListener {
                copyResult()
                true
            }
        }
        previousView = TextView(this).apply {
            textSize = 22f
            setTextColor(palette.onBackgroundVariant)
            gravity = Gravity.END
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        mainView = FitTextView(this).apply {
            setTextColor(palette.onBackground)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.END or Gravity.BOTTOM
            includeFontPadding = false
            // Altezza fissata dal layout: una frazione su due livelli rimpicciolisce il testo se non ci sta.
            fitsHeight = true
            // Riserva: se il testo superasse comunque la riga, si taglia all'inizio e la fine resta visibile.
            ellipsize = TextUtils.TruncateAt.START
            // Una sola riga: le cifre si rimpiccioliscono fino a questo minimo, ancora ben leggibile.
            if (landscape) setSizeRange(48f, 24f) else setSizeRange(72f, 30f)
            // La larghezza è nota solo dopo la prima misura: allora si ricalcola il testo.
            onWidthChanged = { post { render() } }
            // Tocco: sposta il cursore; pressione prolungata: copia il risultato.
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchX = event.x
                    touchY = event.y
                }
                false
            }
            setOnClickListener { placeCursorAtTouch() }
            setOnLongClickListener {
                copyResult()
                true
            }
        }
        secondaryView = FitTextView(this).apply {
            setSizeRange(26f, 16f)
            gravity = Gravity.END
            ellipsize = TextUtils.TruncateAt.END
            // TalkBack annuncia il risultato quando cambia.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        display.addView(previousView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        display.addView(mainView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        display.addView(secondaryView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return display
    }

    /**
     * Pannello scientifico verticale: 6 colonne; la prima riga sempre visibile, le altre a scomparsa,
     * e in fondo una maniglia con la freccia per aprirlo e chiuderlo.
     */
    private fun buildScientificPanel(): View {
        val rows = keys.scientificPortrait(session.inverse)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(palette.panel, dp(28).toFloat())
            setPadding(dp(8), dp(8), dp(8), 0)
            layoutTransition = LayoutTransition()
        }
        fun addRow(container: LinearLayout, row: List<io.github.calcolatrice.ui.PadKey>, top: Int): List<KeyButton> {
            val (rowView, buttons) = pad.row(row, dp(4), 17f)
            container.addView(rowView, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)).apply { topMargin = top })
            return buttons
        }
        val firstButtons = addRow(panel, rows.first(), 0)
        val extra = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val extraButtons = rows.drop(1).map { addRow(extra, it, dp(4)) }
        panel.addView(extra, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val expand = pad.button(keys.expandKey(scientificExpanded), 16f)
        expandButton = expand
        panel.addView(expand, LinearLayout.LayoutParams(MATCH_PARENT, dp(28)))
        scientificExtraRows = extra
        scientificButtons = listOf(firstButtons) + extraButtons
        return panel
    }

    // ---------------------------------------------------------------- menu e dialoghi

    private fun showMenu() {
        PopupMenu(this, menuButton).apply {
            menu.add(0, MENU_EQUATIONS, 0, R.string.equations)
            menu.add(0, MENU_FUNCTIONS, 1, R.string.functions)
            menu.add(0, MENU_STATISTICS, 2, R.string.statistics)
            menu.add(0, MENU_VARIABLES, 3, R.string.variables)
            menu.add(0, MENU_FORMAT, 4, R.string.number_format)
            menu.add(0, MENU_COLORS, 5, R.string.colors)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EQUATIONS -> startActivity(Intent(this@MainActivity, EquationActivity::class.java))
                    MENU_STATISTICS -> startActivity(Intent(this@MainActivity, StatisticsActivity::class.java))
                    MENU_FUNCTIONS -> startActivity(Intent(this@MainActivity, FunctionActivity::class.java))
                    MENU_VARIABLES -> showVariables()
                    MENU_FORMAT -> chooseFormat()
                    MENU_COLORS -> chooseColors()
                }
                true
            }
            show()
        }
    }

    /**
     * Colori dell'app: corallo, azzurro o dal sistema. L'icona sulla Home segue la scelta; poiché su
     * alcuni launcher l'icona sparisce e va riaggiunta, prima di cambiarla si chiede conferma.
     */
    private fun chooseColors() {
        val choices = ColorTheme.available
        val labels = choices.map {
            getString(
                when (it) {
                    ColorTheme.CORAL -> R.string.colors_coral
                    ColorTheme.BLUE -> R.string.colors_blue
                    ColorTheme.SYSTEM -> R.string.colors_system
                },
            )
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.colors)
            .setSingleChoiceItems(labels, choices.indexOf(palette.theme)) { dialog, which ->
                dialog.dismiss()
                val chosen = choices[which]
                if (chosen == palette.theme) return@setSingleChoiceItems
                if (LauncherIcon.changes(this, chosen)) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.icon_change_title)
                        .setMessage(R.string.icon_change_message)
                        .setPositiveButton(R.string.icon_change_confirm) { _, _ -> applyColors(chosen) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    applyColors(chosen)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyColors(theme: ColorTheme) {
        saveState()
        settings.colorTheme = theme
        LauncherIcon.apply(this, theme)
        recreate()
    }

    /** Testo per TalkBack: le formule lette a parole ("3 quarti", "x al quadrato"). */
    private fun spoken(text: CharSequence): String =
        SpokenMath.describe(text, resources.configuration.locales[0].language)

    private fun chooseFormat() {
        showNumberFormatDialog(numberFormat) { format ->
            numberFormat = format
            settings.numberFormat = format
            render()
        }
    }

    /** Variabili e memoria: tocca una riga per inserire la variabile, STO per salvarci il risultato. */
    private fun showVariables() {
        var dialog: AlertDialog? = null
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val canStore = session.canStore
        for (name in VARIABLES) {
            val value = formatter.format(ResultFormatter.toInternal(session.variables[name] ?: BigDecimal.ZERO))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), dp(4), dp(16), dp(4))
                background = rippleDrawable(palette.onBackground, 0f)
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.insert_variable, name.toString(), value)
                setOnClickListener {
                    dialog?.dismiss()
                    onAction(PadAction.Input(CalcKey.Variable(name)))
                }
            }
            row.addView(
                TextView(this).apply {
                    text = name.toString()
                    textSize = 20f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(palette.accent)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(dp(36), WRAP_CONTENT),
            )
            row.addView(
                TextView(this).apply {
                    text = value
                    textSize = 18f
                    setTextColor(palette.onBackground)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
            )
            row.addView(
                TextView(this).apply {
                    text = getString(R.string.store_short)
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(palette.accent)
                    gravity = Gravity.CENTER
                    background = rippleDrawable(palette.accent, dp(18).toFloat())
                    contentDescription = getString(R.string.store_description, name.toString())
                    isEnabled = canStore
                    alpha = if (canStore) 1f else 0.38f
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        dialog?.dismiss()
                        if (session.store(name)) saveHistory()
                        saveVariables()
                        render()
                    }
                },
                LinearLayout.LayoutParams(dp(64), dp(44)),
            )
            list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.variables)
            .setView(ScrollView(this).apply { addView(list) })
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.clear_variables) { _, _ ->
                session.clearVariables()
                saveVariables()
                render()
            }
            .show()
    }

    // ---------------------------------------------------------------- cronologia

    private fun showHistory() {
        val history = session.history
        var dialog: AlertDialog? = null
        val content: View = if (history.isEmpty()) {
            TextView(this).apply {
                setText(R.string.history_empty)
                textSize = 16f
                setTextColor(palette.onBackgroundVariant)
                setPadding(dp(24), dp(16), dp(24), dp(16))
            }
        } else {
            val list = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            history.forEach { entry -> list.addView(historyItem(entry) { dialog?.dismiss() }) }
            ScrollView(this).apply { addView(list) }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.history)
            .setView(content)
            .setPositiveButton(R.string.close, null)
        if (history.isNotEmpty()) {
            builder.setNeutralButton(R.string.clear_history) { _, _ ->
                session.clearHistory()
                saveHistory()
            }
        }
        dialog = builder.show()
    }

    /** Pannello della cronologia per gli schermi larghi: titolo, "Cancella" e l'elenco dei calcoli. */
    private fun buildHistoryPane(): View {
        val pane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(palette.panel, dp(28).toFloat())
            setPadding(0, dp(12), 0, dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(12), 0)
        }
        header.addView(
            TextView(this).apply {
                setText(R.string.history)
                textSize = 18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(palette.onBackground)
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) isAccessibilityHeading = true
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        header.addView(
            TextView(this).apply {
                setText(R.string.clear_history)
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(palette.accent)
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                background = rippleDrawable(palette.accent, dp(20).toFloat())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    session.clearHistory()
                    saveHistory()
                    render()
                }
            },
            LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)),
        )
        pane.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pane.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        historyPane = list
        return pane
    }

    /** Aggiorna il pannello della cronologia solo quando la cronologia cambia. */
    private fun renderHistoryPane() {
        val list = historyPane ?: return
        val history = session.history
        if (history === shownHistory) return
        shownHistory = history
        list.removeAllViews()
        if (history.isEmpty()) {
            list.addView(
                TextView(this).apply {
                    setText(R.string.history_empty)
                    textSize = 15f
                    setTextColor(palette.onBackgroundVariant)
                    setPadding(dp(24), dp(16), dp(24), dp(16))
                },
            )
            return
        }
        history.forEach { entry -> list.addView(historyItem(entry) {}) }
    }

    private fun historyItem(entry: HistoryEntry, onSelected: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(dp(24), dp(12), dp(24), dp(12))
            background = rippleDrawable(palette.onBackground, 0f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                session.useHistoryValue(entry)
                render()
                onSelected()
            }
            contentDescription = spoken(formatter.format(entry.expression) + " = " + formatter.format(entry.result))
            addView(
                TextView(context).apply {
                    text = formatter.format(entry.expression)
                    textSize = 15f
                    setTextColor(palette.onBackgroundVariant)
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
            )
            addView(
                TextView(context).apply {
                    text = "= " + formatter.format(entry.result)
                    textSize = 24f
                    setTextColor(palette.onBackground)
                    gravity = Gravity.END
                },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
            )
        }

    // ---------------------------------------------------------------- salvataggio

    /** Il calcolo in corso resta anche chiudendo l'app, come sulle calcolatrici. */
    private fun restoreSession(): CalculatorSession {
        val saved = runCatching { JSONObject(prefs.getString(PREF_STATE, null) ?: "{}") }.getOrDefault(JSONObject())
        val expression = saved.optString("expression", "")
        val calc = CalculatorState(
            expression = expression,
            ans = saved.optString("ans", "").toBigDecimalOrNull(),
            justEvaluated = saved.optBoolean("justEvaluated", false),
            cursor = saved.optInt("cursor", -1).takeIf { it in 0 until expression.length },
        )
        val angleUnit = prefs.getString(PREF_ANGLE, null)
            ?.let { name -> AngleUnit.entries.firstOrNull { it.name == name } }
            ?: AngleUnit.DEGREES
        return CalculatorSession(
            calc = calc,
            angleUnit = angleUnit,
            inverse = saved.optBoolean("inverse", false),
            history = decodeHistory(prefs.getString(PREF_HISTORY, null)),
            lastExpression = saved.optString("lastExpression", "").ifEmpty { null },
            variables = settings.variables,
        )
    }

    private fun saveState() {
        val calc = session.calc
        val state = JSONObject()
            .put("expression", calc.expression)
            .put("ans", calc.ans?.toString().orEmpty())
            .put("justEvaluated", calc.justEvaluated)
            .put("cursor", calc.cursor ?: -1)
            .put("inverse", session.inverse)
            .put("lastExpression", session.lastExpression.orEmpty())
        prefs.edit().putString(PREF_STATE, state.toString()).apply()
        saveVariables()
    }

    private fun saveVariables() {
        settings.variables = session.variables
    }

    private fun saveHistory() {
        val array = JSONArray()
        session.history.forEach { array.put(JSONObject().put("e", it.expression).put("r", it.result)) }
        prefs.edit().putString(PREF_HISTORY, array.toString()).apply()
    }

    private fun decodeHistory(json: String?): List<HistoryEntry> {
        if (json == null) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                HistoryEntry(item.getString("e"), item.getString("r"))
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        /** Azioni delle scorciatoie dell'icona (res/xml/shortcuts.xml). */
        const val ACTION_EQUATIONS = "io.github.calcolatrice.action.EQUATIONS"
        const val ACTION_STATISTICS = "io.github.calcolatrice.action.STATISTICS"
        const val ACTION_FUNCTIONS = "io.github.calcolatrice.action.FUNCTIONS"

        private const val CURSOR = "|"

        /** Spazio tra i tasti del tastierino in verticale. */
        private const val KEY_SPACING = 10
        private const val PREF_ANGLE = "angle_unit"
        private const val PREF_HISTORY = "history"
        private const val PREF_STATE = "state"
        private const val PREF_FRACTION = "fraction_main"
        private const val KEY_SCIENTIFIC_EXPANDED = "scientific_expanded"
        private const val MENU_EQUATIONS = 1
        private const val MENU_STATISTICS = 2
        private const val MENU_VARIABLES = 3
        private const val MENU_FORMAT = 4
        private const val MENU_FUNCTIONS = 5
        private const val MENU_COLORS = 6

        /** Da questa larghezza (classe "expanded" di Material 3) la cronologia sta a fianco. */
        private const val WIDE_SCREEN_DP = 840
        private const val HISTORY_PANE_DP = 340
    }
}
