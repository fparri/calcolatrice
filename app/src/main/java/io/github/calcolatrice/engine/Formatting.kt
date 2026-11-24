package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

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
}

/**
 * Converte il formato interno in testo leggibile secondo la lingua dell'utente:
 * separatori di migliaia e decimali locali e simboli matematici tipografici (× ÷ −).
 */
class DisplayFormatter(
    private val decimalSeparator: Char = '.',
    /** null = nessun raggruppamento delle migliaia (utile per copiare il numero). */
    private val groupingSeparator: Char? = ',',
) {
    fun format(internal: String, ans: BigDecimal? = null): String {
        val s = if (ans != null) ExpressionText.expandAns(internal, ans) else internal
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isDigit() || c == '.') {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                out.append(formatNumber(s.substring(start, i)))
                if (i < s.length && s[i] == 'E') {
                    out.append('E')
                    i++
                    if (i < s.length && s[i] == '-') {
                        out.append('−')
                        i++
                    }
                }
                continue
            }
            val function = DISPLAY_FUNCTIONS.entries.firstOrNull { s.startsWith(it.key, i) }
            if (function != null) {
                out.append(function.value)
                i += function.key.length
                continue
            }
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
        return out.toString()
    }

    private fun formatNumber(number: String): String {
        val dot = number.indexOf('.')
        val integerPart = if (dot >= 0) number.substring(0, dot) else number
        val grouped = StringBuilder()
        integerPart.forEachIndexed { index, digit ->
            if (groupingSeparator != null && index > 0 && (integerPart.length - index) % 3 == 0) {
                grouped.append(groupingSeparator)
            }
            grouped.append(digit)
        }
        if (dot >= 0) grouped.append(decimalSeparator).append(number.substring(dot + 1))
        return grouped.toString()
    }

    private companion object {
        val DISPLAY_FUNCTIONS = linkedMapOf(
            "asin(" to "sin⁻¹(",
            "acos(" to "cos⁻¹(",
            "atan(" to "tan⁻¹(",
        )
    }
}
