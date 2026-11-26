package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Formato di visualizzazione dei risultati, come i modi Norm/Fix/Sci/Eng delle calcolatrici. */
sealed interface NumberFormat {
    /** Fino a 15 cifre significative; notazione scientifica per numeri molto grandi o piccoli. */
    data object Normal : NumberFormat

    /** Numero fisso di decimali (0–9). */
    data class Fix(val decimals: Int) : NumberFormat {
        init {
            require(decimals in 0..9)
        }
    }

    /** Notazione scientifica con un numero fisso di cifre significative (1–10). */
    data class Sci(val digits: Int) : NumberFormat {
        init {
            require(digits in 1..10)
        }
    }

    /** Notazione ingegneristica: esponente multiplo di 3. */
    data object Eng : NumberFormat

    /** Codice da salvare e da mostrare: NORM, FIX 2, SCI 4, ENG. */
    val code: String
        get() = when (this) {
            Normal -> "NORM"
            is Fix -> "FIX $decimals"
            is Sci -> "SCI $digits"
            Eng -> "ENG"
        }

    companion object {
        fun parse(code: String?): NumberFormat {
            val parts = code.orEmpty().split(' ')
            val n = parts.getOrNull(1)?.toIntOrNull()
            return when {
                parts[0] == "FIX" && n != null && n in 0..9 -> Fix(n)
                parts[0] == "SCI" && n != null && n in 1..10 -> Sci(n)
                parts[0] == "ENG" -> Eng
                else -> Normal
            }
        }
    }
}

/** Conversione dei risultati nel formato interno (punto decimale, notazione `E`). */
object ResultFormatter {
    /** Cifre significative mostrate all'utente. */
    const val DISPLAY_DIGITS = 15

    private val LARGE = BigDecimal("1E15")
    private val SMALL = BigDecimal("1E-9")

    fun toInternal(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(DISPLAY_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val abs = rounded.abs()
        if (abs >= LARGE || abs < SMALL) {
            val exponent = MathSupport.exponentOf(rounded)
            val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
            return "${mantissa}E$exponent"
        }
        return rounded.toPlainString()
    }

    /** Il risultato nel [format] scelto, sempre nel formato interno. */
    fun format(value: BigDecimal, format: NumberFormat): String = when (format) {
        NumberFormat.Normal -> toInternal(value)
        is NumberFormat.Fix -> fixed(value, format.decimals)
        is NumberFormat.Sci -> scientific(value, format.digits)
        NumberFormat.Eng -> engineering(value)
    }

    private fun fixed(value: BigDecimal, decimals: Int): String {
        // Troppe cifre intere per la visualizzazione: si torna al formato normale.
        if (value.abs() >= LARGE) return toInternal(value)
        return value.setScale(decimals, RoundingMode.HALF_UP).toPlainString()
    }

    private fun scientific(value: BigDecimal, digits: Int): String {
        if (value.signum() == 0) return BigDecimal.ZERO.setScale(digits - 1).toPlainString() + "E0"
        val rounded = value.round(MathContext(digits, RoundingMode.HALF_UP))
        val exponent = MathSupport.exponentOf(rounded)
        val mantissa = rounded.movePointLeft(exponent).setScale(digits - 1, RoundingMode.HALF_UP)
        return "${mantissa.toPlainString()}E$exponent"
    }

    private fun engineering(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(DISPLAY_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val exponent = Math.floorDiv(MathSupport.exponentOf(rounded), 3) * 3
        val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
        return if (exponent == 0) mantissa else "${mantissa}E$exponent"
    }
}

/**
 * Converte il formato interno in testo leggibile secondo la lingua dell'utente:
 * separatori di migliaia e decimali locali, simboli matematici tipografici (× ÷ −)
 * ed esponenti interi brevi scritti in apice (3² e 10⁻³ invece di 3^2 e 10^-3).
 */
class DisplayFormatter(
    private val decimalSeparator: Char = '.',
    /** null = nessun raggruppamento delle migliaia (utile per copiare il numero). */
    private val groupingSeparator: Char? = ',',
    /** Nomi delle funzioni nella lingua dell'utente (es. `gcd` → `MCD`), oltre a quelli predefiniti. */
    functionNames: Map<String, String> = emptyMap(),
) {
    /** Token interno (`ncr(`) → testo mostrato (`nCr(`), per ogni funzione. */
    private val functionDisplay: List<Pair<String, String>> = Tokenizer.FUNCTIONS.map { name ->
        val shown = functionNames[name] ?: DISPLAY_NAMES[name] ?: name
        "$name(" to "$shown("
    }
    /**
     * Testo per lo schermo e, per ogni posizione dell'espressione (con Ans già sostituito),
     * la posizione corrispondente nel testo: serve per disegnare e spostare il cursore.
     */
    class Layout(val text: String, val positions: IntArray)

    fun format(internal: String, ans: BigDecimal? = null): String = layout(internal, ans).text

    fun layout(internal: String, ans: BigDecimal? = null): Layout {
        val s = if (ans != null) ExpressionText.expandAns(internal, ans) else internal
        val out = StringBuilder()
        val positions = IntArray(s.length + 1)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isDigit() || c == '.') {
                i = appendNumber(s, i, out, positions)
                continue
            }
            if (c == '^') {
                val end = superscriptEnd(s, i)
                if (end != null) {
                    positions[i] = out.length
                    for (k in i + 1 until end) {
                        positions[k] = out.length
                        out.append(SUPERSCRIPTS.getValue(s[k]))
                    }
                    i = end
                    continue
                }
            }
            // I token delle funzioni si scrivono interi: "nCr(" non va letto lettera per lettera.
            val function = functionDisplay.firstOrNull { s.startsWith(it.first, i) }
            if (function != null) {
                for (k in i until i + function.first.length) positions[k] = out.length
                out.append(function.second)
                i += function.first.length
                continue
            }
            if (c == ';') {
                positions[i] = out.length
                out.append("; ")
                i++
                continue
            }
            positions[i] = out.length
            out.append(
                when (c) {
                    '*' -> '×'
                    '/' -> '÷'
                    '-' -> '−'
                    else -> c
                },
            )
            i++
        }
        positions[s.length] = out.length
        return Layout(out.toString(), positions)
    }

    /** Aggiunge il numero che inizia in [start] (con eventuale esponente) e restituisce dove finisce. */
    private fun appendNumber(s: String, start: Int, out: StringBuilder, positions: IntArray): Int {
        var i = start
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        val number = s.substring(start, i)
        val dot = number.indexOf('.')
        val integerDigits = if (dot >= 0) dot else number.length
        number.forEachIndexed { k, ch ->
            positions[start + k] = out.length
            if (groupingSeparator != null && k in 1 until integerDigits && (integerDigits - k) % 3 == 0) {
                out.append(groupingSeparator)
            }
            out.append(if (ch == '.') decimalSeparator else ch)
        }
        // Esponente della notazione scientifica: niente separatori delle migliaia.
        if (i < s.length && s[i] == 'E') {
            positions[i] = out.length
            out.append('E')
            i++
            if (i < s.length && s[i] == '-') {
                positions[i] = out.length
                out.append('−')
                i++
            }
            while (i < s.length && s[i].isDigit()) {
                positions[i] = out.length
                out.append(s[i])
                i++
            }
        }
        return i
    }

    /**
     * Se dopo `^` in [caret] c'è un esponente intero breve (es. `2`, `-1`, `10`) che finisce lì,
     * restituisce la posizione dopo l'esponente; altrimenti null e `^` resta visibile.
     */
    private fun superscriptEnd(s: String, caret: Int): Int? {
        var j = caret + 1
        if (j < s.length && s[j] == '-') j++
        val digitsStart = j
        while (j < s.length && s[j].isDigit()) j++
        val digits = j - digitsStart
        if (digits !in 1..3) return null
        // 2^1.5, 2^3! (= 2^6) e 2^3^2 restano scritti per esteso per non confondere.
        if (j < s.length && s[j] in ".E!%^") return null
        return j
    }

    private companion object {
        /** Nomi mostrati per le funzioni il cui nome interno è diverso. */
        val DISPLAY_NAMES = mapOf(
            "asin" to "sin⁻¹",
            "acos" to "cos⁻¹",
            "atan" to "tan⁻¹",
            "root" to "ⁿ√",
            "ncr" to "nCr",
            "npr" to "nPr",
            "binp" to "BinomP",
            "binc" to "BinomC",
            "normc" to "NormC",
        )
        val SUPERSCRIPTS = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '-' to '⁻',
        )
    }
}
