package io.github.calcolatrice.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import io.github.calcolatrice.R

/**
 * Colori dell'app scelti dal menu ⋮ → Colori. Corallo e azzurro sono tavolozze fisse, uguali su
 * tutte le versioni di Android; "dal sistema" usa i colori dinamici (Material You), da Android 12.
 */
enum class ColorTheme(val code: String) {
    CORAL("coral"),
    BLUE("blue"),
    SYSTEM("system");

    companion object {
        val DEFAULT = CORAL

        /** Le scelte offerte su questo telefono: i colori dinamici esistono da Android 12. */
        val available: List<ColorTheme>
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) entries else listOf(CORAL, BLUE)

        fun parse(code: String?): ColorTheme =
            entries.firstOrNull { it.code == code }?.takeIf { it in available } ?: DEFAULT
    }
}

/**
 * Ruoli di colore nello stile di Material 3: sfondo, testo, contenitori e colore d'accento,
 * con il colore del contenuto per ogni contenitore.
 */
data class ColorScheme(
    val background: Int,
    val onBackground: Int,
    val onBackgroundVariant: Int,
    /** Contenitori: pannello scientifico, schede dei risultati, caselle. */
    val panel: Int,
    val accent: Int,
    val onAccent: Int,
    val error: Int,
    /** Bordi sottili (chip, separatori). */
    val outline: Int,
    val number: Int,
    val operator: Int,
    val onOperator: Int,
    val clear: Int,
    val onClear: Int,
)

/** Le tavolozze fisse, chiara e scura. I valori sono quelli dei mockup approvati. */
object Schemes {
    private fun c(hex: Long) = hex.toInt()

    val coralLight = ColorScheme(
        background = c(0xFFFFF8F6), onBackground = c(0xFF231917), onBackgroundVariant = c(0xFF534341),
        panel = c(0xFFFCEAE5), accent = c(0xFFB8432E), onAccent = c(0xFFFFFFFF), error = c(0xFFBA1A1A),
        outline = c(0xFFD8C2BD), number = c(0xFFF3E1DC), operator = c(0xFFFFDAD0), onOperator = c(0xFF5A1C0F),
        clear = c(0xFFFFE58A), onClear = c(0xFF3D2D00),
    )
    val coralDark = ColorScheme(
        background = c(0xFF1A110F), onBackground = c(0xFFF1DFDA), onBackgroundVariant = c(0xFFD8C2BD),
        panel = c(0xFF271D1B), accent = c(0xFFFFB4A3), onAccent = c(0xFF5F1608), error = c(0xFFFFB4AB),
        outline = c(0xFF534341), number = c(0xFF3A2E2B), operator = c(0xFF5D3F37), onOperator = c(0xFFFFDAD0),
        clear = c(0xFF574400), onClear = c(0xFFFFE08A),
    )
    val blueLight = ColorScheme(
        background = c(0xFFF7F9FF), onBackground = c(0xFF181C22), onBackgroundVariant = c(0xFF43474E),
        panel = c(0xFFE9EEF7), accent = c(0xFF1F63B8), onAccent = c(0xFFFFFFFF), error = c(0xFFBA1A1A),
        outline = c(0xFFC3C6CF), number = c(0xFFDFE3EC), operator = c(0xFFD3E4FF), onOperator = c(0xFF0B3A66),
        clear = c(0xFFFFE07A), onClear = c(0xFF3D2D00),
    )
    val blueDark = ColorScheme(
        background = c(0xFF10141A), onBackground = c(0xFFE0E2EA), onBackgroundVariant = c(0xFFC3C6CF),
        panel = c(0xFF1C2026), accent = c(0xFFA2C9FF), onAccent = c(0xFF00315C), error = c(0xFFFFB4AB),
        outline = c(0xFF43474E), number = c(0xFF31353C), operator = c(0xFF2B4A6F), onOperator = c(0xFFD3E4FF),
        clear = c(0xFF574400), onClear = c(0xFFFFE08A),
    )

    /** Colori dinamici del sistema (risorse in values-v31 e values-night-v31). */
    fun system(context: Context) = ColorScheme(
        background = context.getColor(R.color.background),
        onBackground = context.getColor(R.color.on_background),
        onBackgroundVariant = context.getColor(R.color.on_background_variant),
        panel = context.getColor(R.color.panel),
        accent = context.getColor(R.color.accent),
        onAccent = context.getColor(R.color.on_accent),
        error = context.getColor(R.color.error),
        outline = context.getColor(R.color.outline),
        number = context.getColor(R.color.key_number),
        operator = context.getColor(R.color.key_operator),
        onOperator = context.getColor(R.color.on_key_operator),
        clear = context.getColor(R.color.key_clear),
        onClear = context.getColor(R.color.on_key_clear),
    )

    fun of(context: Context, theme: ColorTheme): ColorScheme {
        val night = context.isNightMode()
        return when (theme) {
            ColorTheme.CORAL -> if (night) coralDark else coralLight
            ColorTheme.BLUE -> if (night) blueDark else blueLight
            ColorTheme.SYSTEM -> system(context)
        }
    }
}

fun Context.isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** I colori in uso, presi dalla scelta salvata nelle impostazioni. */
class Palette(context: Context, val theme: ColorTheme = Settings(context).colorTheme) {
    private val scheme = Schemes.of(context, theme)
    val background = scheme.background
    val onBackground = scheme.onBackground
    val onBackgroundVariant = scheme.onBackgroundVariant
    val panel = scheme.panel
    val accent = scheme.accent
    val onAccent = scheme.onAccent
    val error = scheme.error
    val outline = scheme.outline

    /** Contenitore tonale (tasti numerici, caselle) e contenitore evidenziato (operatori, selezione). */
    val container = scheme.number
    val selectedContainer = scheme.operator
    val onSelectedContainer = scheme.onOperator

    /** Colore di sfondo e colore del contenuto per un tasto. */
    fun forKey(key: PadKey): Pair<Int, Int> = when (key.style) {
        KeyStyle.Number -> scheme.number to onBackground
        KeyStyle.Operator -> scheme.operator to scheme.onOperator
        KeyStyle.Clear -> scheme.clear to scheme.onClear
        KeyStyle.Equals -> accent to onAccent
        KeyStyle.Function -> android.graphics.Color.TRANSPARENT to onBackground
        // Interruttori e schede: pieni con il colore d'accento se attivi, tonali se no.
        KeyStyle.Toggle -> if (key.active) accent to onAccent else scheme.number to onBackgroundVariant
    }
}

/**
 * Tema della finestra corrispondente ai colori scelti (sfondo, accento dei dialoghi e dei menu).
 * Va chiamato prima di super.onCreate().
 */
fun Activity.applyColorTheme() {
    setTheme(
        when (Settings(this).colorTheme) {
            ColorTheme.CORAL -> R.style.Theme_Calcolatrice_Coral
            ColorTheme.BLUE -> R.style.Theme_Calcolatrice_Blue
            ColorTheme.SYSTEM -> R.style.Theme_Calcolatrice
        },
    )
}

/**
 * Icona dell'app sulla schermata Home: corallo o azzurra. Il manifest dichiara due activity-alias
 * della stessa schermata, uno per icona; se ne tiene attivo uno solo. Con i colori del sistema
 * resta l'icona corallo (le icone a tema le colora il sistema con il livello monocromatico).
 */
object LauncherIcon {
    private const val CORAL = "io.github.calcolatrice.IconCoral"
    private const val BLUE = "io.github.calcolatrice.IconBlue"

    private fun aliasFor(theme: ColorTheme) = if (theme == ColorTheme.BLUE) BLUE else CORAL

    /** True se scegliendo [theme] l'icona sulla Home cambierebbe. */
    fun changes(context: Context, theme: ColorTheme): Boolean = !isEnabled(context, aliasFor(theme))

    /** Attiva l'icona di [theme] e spegne l'altra (prima si accende la nuova: non resta mai senza icona). */
    fun apply(context: Context, theme: ColorTheme) {
        val wanted = aliasFor(theme)
        val other = if (wanted == CORAL) BLUE else CORAL
        set(context, wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        set(context, other, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    private fun isEnabled(context: Context, alias: String): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(ComponentName(context, alias))
        // Stato predefinito: quello del manifest (attivo solo l'icona corallo).
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias == CORAL
            else -> false
        }
    }

    private fun set(context: Context, alias: String, state: Int) {
        val component = ComponentName(context, alias)
        if (context.packageManager.getComponentEnabledSetting(component) == state) return
        context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
