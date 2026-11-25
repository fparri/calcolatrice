package io.github.calcolatrice

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
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
import android.widget.Toast
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
import io.github.calcolatrice.ui.HardwareKeys
import io.github.calcolatrice.ui.KeyButton
import io.github.calcolatrice.ui.KeyFactory
import io.github.calcolatrice.ui.KeyPad
import io.github.calcolatrice.ui.PadAction
import io.github.calcolatrice.ui.Palette
import io.github.calcolatrice.ui.Settings
import io.github.calcolatrice.ui.dp
import io.github.calcolatrice.ui.errorMessage
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
    private lateinit var mainView: TextView
    private lateinit var secondaryView: TextView
    private lateinit var angleView: TextView
    private lateinit var formatView: TextView
    private lateinit var memoryView: TextView
    private lateinit var fractionButton: KeyButton
    private lateinit var menuButton: View
    private lateinit var mainGridView: View
    private var scientificButtons: List<List<KeyButton>> = emptyList()
    private var expandButton: KeyButton? = null
    private var scientificExtraRows: View? = null

    /** Posizioni nel testo del display, per spostare il cursore toccando lo schermo. */
    private var displayPositions: IntArray? = null
    private var cursorGlyphAt = -1
    private var touchX = 0f
    private var touchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        settings = Settings(this)
        prefs = settings.prefs
        palette = Palette(this)
        keys = KeyFactory(this)
        pad = KeyPad(this, palette, ::onAction)
        val symbols = DecimalFormatSymbols.getInstance(resources.configuration.locales[0])
        decimalSeparator = symbols.decimalSeparator
        formatter = DisplayFormatter(symbols.decimalSeparator, symbols.groupingSeparator)
        plainFormatter = DisplayFormatter(symbols.decimalSeparator, null)
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
            PadAction.Solve -> session.solve().also { saveVariables() }
            PadAction.MemoryAdd -> addToMemory(subtract = false)
            PadAction.MemorySubtract -> addToMemory(subtract = true)
            PadAction.ToggleFraction -> {
                asFraction = !asFraction
                prefs.edit().putBoolean(PREF_FRACTION, asFraction).apply()
            }
            // Azioni delle altre schermate.
            PadAction.Back, is PadAction.Sheet, is PadAction.SetEquationMode, is PadAction.SetStatMode -> Unit
        }
        render()
    }

    private fun addToMemory(subtract: Boolean) {
        if (session.addToMemory(subtract)) saveHistory()
        saveVariables()
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
        // Lettere maiuscole: variabili. Minuscole: funzioni scientifiche (rispettano il tasto INV).
        in VARIABLES -> CalcKey.Variable(c)
        's' -> CalcKey.Function(if (session.inverse) "asin" else "sin")
        'c' -> CalcKey.Function(if (session.inverse) "acos" else "cos")
        't' -> CalcKey.Function(if (session.inverse) "atan" else "tan")
        'l' -> if (session.inverse) CalcKey.ExpE else CalcKey.Function("ln")
        'g' -> if (session.inverse) CalcKey.Exp10 else CalcKey.Function("log")
        'e' -> CalcKey.Constant('e')
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
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), plainFormatter.format(value, ans)))
        // Da Android 13 il sistema mostra già una conferma.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
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
        val index = positions.indices.minByOrNull { kotlin.math.abs(positions[it] - offset) } ?: return
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
        val main: CharSequence = when {
            showsResult -> {
                val value = solutions.value(ans, asFraction)
                session.solvedFor?.let { "$it = $value" } ?: value
            }
            calc.expression.isEmpty() -> "0"
            else -> {
                val layout = formatter.layout(calc.expression, ans)
                displayPositions = layout.positions
                val cursor = calc.cursor
                if (cursor == null) {
                    layout.text
                } else {
                    // Cursore visibile solo quando non è in fondo.
                    val at = layout.positions[cursor.coerceIn(0, layout.positions.size - 1)]
                    cursorGlyphAt = at
                    SpannableStringBuilder(layout.text).apply {
                        insert(at, CURSOR)
                        setSpan(ForegroundColorSpan(palette.accent), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
        mainView.text = main

        val last = session.lastExpression
        val previous = last?.let { formatter.format(it) + if (session.solvedFor != null) " = 0" else " =" }
        previousView.text = previous.orEmpty()
        previousView.visibility = if (previous == null) View.GONE else View.VISIBLE

        val error = calc.error?.let { getString(errorMessage(it)) }
        val preview = session.preview
            ?.let { solutions.value(it, asFraction) }
            ?.takeIf { it != main.toString() }
        secondaryView.text = error ?: preview ?: ""
        secondaryView.setTextColor(if (error != null) palette.error else palette.onBackgroundVariant)

        val degrees = session.angleUnit == AngleUnit.DEGREES
        val angleName = getString(if (degrees) R.string.angle_degrees else R.string.angle_radians)
        angleView.text = angleName
        angleView.contentDescription = getString(R.string.angle_toggle, angleName)
        formatView.text = numberFormat.code
        formatView.contentDescription = getString(R.string.format_description, numberFormat.code)
        val memory = session.variables['M']
        memoryView.visibility = if (memory != null && memory.signum() != 0) View.VISIBLE else View.GONE
        keys.fractionKey(asFraction).let { if (fractionButton.key != it) fractionButton.bind(it, palette) }

        val specs = if (landscape) {
            keys.scientificLandscape(session.inverse, session.angleUnit)
        } else {
            keys.scientificPortrait(session.inverse, session.angleUnit)
        }
        scientificButtons.zip(specs).forEach { (buttons, row) ->
            buttons.zip(row).forEach { (button, key) -> if (button.key != key) button.bind(key, palette) }
        }

        if (!landscape) {
            val expandKey = keys.expandKey(scientificExpanded)
            expandButton?.let { if (it.key != expandKey) it.bind(expandKey, palette) }
            scientificExtraRows?.visibility = if (scientificExpanded) View.VISIBLE else View.GONE
            val params = mainGridView.layoutParams as LinearLayout.LayoutParams
            val weight = if (scientificExpanded) 1.5f else 2f
            if (params.weight != weight) {
                params.weight = weight
                mainGridView.layoutParams = params
            }
        }
    }

    private fun buildContent(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // Su tablet in verticale il contenuto resta centrato con una larghezza comoda.
        val width = if (!landscape && resources.configuration.screenWidthDp > 600) dp(600) else MATCH_PARENT
        root.addView(column, FrameLayout.LayoutParams(width, MATCH_PARENT, Gravity.CENTER_HORIZONTAL))

        column.addView(buildTopBar(), LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        column.addView(buildDisplay(), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        column.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))

        if (landscape) {
            val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val panel = FrameLayout(this).apply {
                background = roundedDrawable(palette.panel, dp(28).toFloat())
                setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            val (scientificGrid, buttons) = pad.grid(
                keys.scientificLandscape(session.inverse, session.angleUnit), dp(4), 18f,
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
            val (mainGrid, _) = pad.grid(keys.main(decimalSeparator), dp(8), 30f)
            mainGridView = mainGrid
            column.addView(mainGrid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 2f))
        }

        // Edge-to-edge: il contenuto evita barre di sistema e notch.
        root.padForSystemBars(dp(16), dp(8))
        return root
    }

    private fun topBarLabel(onClick: () -> Unit) = TextView(this).apply {
        setTextColor(palette.accent)
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(10), 0)
        background = rippleDrawable(palette.onBackground, dp(20).toFloat())
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        angleView = topBarLabel { onAction(PadAction.ToggleAngle) }
        bar.addView(angleView, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)))
        formatView = topBarLabel { chooseFormat() }
        bar.addView(formatView, LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)))
        // "M" come sulle calcolatrici: la memoria contiene un valore.
        memoryView = TextView(this).apply {
            text = "M"
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(palette.onBackgroundVariant)
            setPadding(dp(6), 0, dp(6), 0)
            contentDescription = getString(R.string.memory_indicator_description)
        }
        bar.addView(memoryView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        fractionButton = pad.button(keys.fractionKey(asFraction), 15f)
        bar.addView(fractionButton, LinearLayout.LayoutParams(dp(52), dp(36)))
        bar.addView(pad.button(keys.historyKey(), 16f), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
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
        mainView = TextView(this).apply {
            setTextColor(palette.onBackground)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.END or Gravity.BOTTOM
            maxLines = 3
            includeFontPadding = false
            setAutoSizeTextTypeUniformWithConfiguration(
                24, if (landscape) 48 else 72, 2, TypedValue.COMPLEX_UNIT_SP,
            )
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
        secondaryView = TextView(this).apply {
            textSize = 26f
            gravity = Gravity.END
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            // TalkBack annuncia il risultato quando cambia.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        display.addView(previousView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        display.addView(mainView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        display.addView(secondaryView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return display
    }

    /** Pannello scientifico verticale: prima riga sempre visibile, le altre a scomparsa. */
    private fun buildScientificPanel(): View {
        val rows = keys.scientificPortrait(session.inverse, session.angleUnit)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(palette.panel, dp(28).toFloat())
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutTransition = LayoutTransition()
        }

        val firstLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (firstRow, firstButtons) = pad.row(rows.first(), dp(4), 18f)
        firstLine.addView(firstRow, LinearLayout.LayoutParams(0, dp(44), 1f))
        val expand = pad.button(keys.expandKey(scientificExpanded), 18f)
        expandButton = expand
        firstLine.addView(expand, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(4) })
        panel.addView(firstLine, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val extra = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val extraButtons = rows.drop(1).map { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val (rowView, rowButtons) = pad.row(row, dp(4), 18f)
            line.addView(rowView, LinearLayout.LayoutParams(0, dp(44), 1f))
            line.addView(View(this), LinearLayout.LayoutParams(dp(48), 1))
            extra.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(4) })
            rowButtons
        }
        panel.addView(extra, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        scientificExtraRows = extra
        scientificButtons = listOf(firstButtons) + extraButtons
        return panel
    }

    // ---------------------------------------------------------------- menu e dialoghi

    private fun showMenu() {
        PopupMenu(this, menuButton).apply {
            menu.add(0, MENU_EQUATIONS, 0, R.string.equations)
            menu.add(0, MENU_STATISTICS, 1, R.string.statistics)
            menu.add(0, MENU_VARIABLES, 2, R.string.variables)
            menu.add(0, MENU_FORMAT, 3, R.string.number_format)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EQUATIONS -> startActivity(Intent(this@MainActivity, EquationActivity::class.java))
                    MENU_STATISTICS -> startActivity(Intent(this@MainActivity, StatisticsActivity::class.java))
                    MENU_VARIABLES -> showVariables()
                    MENU_FORMAT -> chooseFormat()
                }
                true
            }
            show()
        }
    }

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

        private const val CURSOR = "|"
        private const val PREF_ANGLE = "angle_unit"
        private const val PREF_HISTORY = "history"
        private const val PREF_STATE = "state"
        private const val PREF_FRACTION = "fraction_main"
        private const val KEY_SCIENTIFIC_EXPANDED = "scientific_expanded"
        private const val MENU_EQUATIONS = 1
        private const val MENU_STATISTICS = 2
        private const val MENU_VARIABLES = 3
        private const val MENU_FORMAT = 4
    }
}
