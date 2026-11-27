package io.github.calcolatrice.engine

/**
 * Testo da far leggere a TalkBack al posto dei simboli: "3/4" diventa "3 quarti", "x²" "x al quadrato",
 * "6,02×10²³" "6,02 per 10 alla 23". Lavora sul testo già formattato per lo schermo.
 * Lingue: italiano e inglese (le altre usano l'inglese).
 */
object SpokenMath {

    private class Words(
        val minus: String, val plus: String, val times: String, val dividedBy: String,
        val root: String, val pi: String, val equals: String, val approximately: String,
        val lessEqual: String, val greaterEqual: String, val less: String, val greater: String,
        val notEqual: String, val or: String, val union: String, val infinity: String,
        val squared: String, val cubed: String, val toThe: String, val over: String,
        val inverse: Map<String, String>,
        /** Denominatori con un nome (2 → mezzi): singolare (numeratore 1) e plurale. */
        val denominators: Map<String, Pair<String, String>>,
        val one: String,
    )

    private val ITALIAN = Words(
        minus = "meno", plus = "più", times = "per", dividedBy = "diviso",
        root = "radice di", pi = "pi greco", equals = "uguale", approximately = "circa",
        lessEqual = "minore o uguale a", greaterEqual = "maggiore o uguale a", less = "minore di",
        greater = "maggiore di", notEqual = "diverso da", or = "oppure", union = "unione", infinity = "infinito",
        squared = "al quadrato", cubed = "al cubo", toThe = "alla", over = "fratto",
        inverse = mapOf("sin⁻¹" to "arcoseno", "cos⁻¹" to "arcocoseno", "tan⁻¹" to "arcotangente"),
        denominators = mapOf(
            "2" to ("mezzo" to "mezzi"), "3" to ("terzo" to "terzi"), "4" to ("quarto" to "quarti"),
            "5" to ("quinto" to "quinti"), "6" to ("sesto" to "sesti"), "7" to ("settimo" to "settimi"),
            "8" to ("ottavo" to "ottavi"), "9" to ("nono" to "noni"), "10" to ("decimo" to "decimi"),
        ),
        one = "un",
    )

    private val ENGLISH = Words(
        minus = "minus", plus = "plus", times = "times", dividedBy = "divided by",
        root = "square root of", pi = "pi", equals = "equals", approximately = "approximately",
        lessEqual = "less than or equal to", greaterEqual = "greater than or equal to", less = "less than",
        greater = "greater than", notEqual = "not equal to", or = "or", union = "union", infinity = "infinity",
        squared = "squared", cubed = "cubed", toThe = "to the power of", over = "over",
        inverse = mapOf("sin⁻¹" to "arc sine", "cos⁻¹" to "arc cosine", "tan⁻¹" to "arc tangent"),
        denominators = mapOf(
            "2" to ("half" to "halves"), "3" to ("third" to "thirds"), "4" to ("quarter" to "quarters"),
            "5" to ("fifth" to "fifths"), "6" to ("sixth" to "sixths"), "7" to ("seventh" to "sevenths"),
            "8" to ("eighth" to "eighths"), "9" to ("ninth" to "ninths"), "10" to ("tenth" to "tenths"),
        ),
        one = "one",
    )

    private const val SUPERSCRIPT = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻"
    private const val SUBSCRIPT = "₀₁₂₃₄₅₆₇₈₉"

    fun describe(text: CharSequence, language: String): String {
        val w = if (language == "it") ITALIAN else ENGLISH
        var s = text.toString()
        w.inverse.forEach { (symbol, name) -> s = s.replace(symbol, name) }
        s = fractions(s, w)
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c in SUPERSCRIPT -> {
                    var end = i
                    while (end < s.length && s[end] in SUPERSCRIPT) end++
                    val power = s.substring(i, end).map { if (it == '⁻') '-' else '0' + SUPERSCRIPT.indexOf(it) }
                        .joinToString("")
                    word(
                        out,
                        when (power) {
                            "2" -> w.squared
                            "3" -> w.cubed
                            else -> "${w.toThe} " + power.replace("-", "${w.minus} ")
                        },
                    )
                    i = end
                    continue
                }
                c in SUBSCRIPT -> out.append(' ').append('0' + SUBSCRIPT.indexOf(c))
                else -> when (val name = symbol(c, w)) {
                    null -> out.append(c)
                    else -> word(out, name)
                }
            }
            i++
        }
        return out.toString().replace(Regex(" {2,}"), " ").trim()
    }

    private fun symbol(c: Char, w: Words): String? = when (c) {
        '−', '-' -> w.minus
        '+' -> w.plus
        '×', '·' -> w.times
        '÷' -> w.dividedBy
        '√' -> w.root
        'π' -> w.pi
        '=' -> w.equals
        '≈' -> w.approximately
        '≤' -> w.lessEqual
        '≥' -> w.greaterEqual
        '<' -> w.less
        '>' -> w.greater
        '≠' -> w.notEqual
        '∨' -> w.or
        '∪' -> w.union
        '∞' -> w.infinity
        else -> null
    }

    private fun word(out: StringBuilder, word: String) {
        out.append(' ').append(word).append(' ')
    }

    /** Le frazioni: "3/4" → "3 quarti", "1/2" → "un mezzo", "√3/2" → "radice di 3 fratto 2". */
    private fun fractions(s: String, w: Words): String {
        val matches = FractionText.find(s)
        if (matches.isEmpty()) return s
        val out = StringBuilder()
        var last = 0
        for (m in matches) {
            out.append(s, last, m.start)
            val named = w.denominators[m.denominator].takeIf { m.numerator.all(Char::isDigit) }
            out.append(' ').append(
                when {
                    named == null -> "${m.numerator} ${w.over} ${m.denominator}"
                    m.numerator == "1" -> "${w.one} ${named.first}"
                    else -> "${m.numerator} ${named.second}"
                },
            ).append(' ')
            last = m.end
        }
        out.append(s, last, s.length)
        return out.toString()
    }
}
