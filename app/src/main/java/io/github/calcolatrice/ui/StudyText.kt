package io.github.calcolatrice.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import io.github.calcolatrice.R
import io.github.calcolatrice.engine.Asymptote
import io.github.calcolatrice.engine.FunctionStudyResult
import io.github.calcolatrice.engine.InequalityText
import io.github.calcolatrice.engine.Parity
import io.github.calcolatrice.engine.Reliability
import io.github.calcolatrice.engine.SolutionSet
import io.github.calcolatrice.engine.StudyPoint
import java.math.BigDecimal

/**
 * Testo dello studio di funzione, nell'ordine del quaderno: dominio, simmetrie, intersezioni, segno,
 * crescenza, massimi e minimi, concavità e flessi, asintoti. Ogni sezione dice quanto è affidabile:
 * niente segno se è calcolata con certezza, "≈" se è trovata numericamente, "da verificare" (nel colore
 * degli errori) se è una stima ai grandi x. In fondo la legenda.
 */
class StudyText(
    private val context: Context,
    private val palette: Palette,
    private val value: (BigDecimal) -> String,
) {
    private val out = SpannableStringBuilder()
    private val sets = InequalityText(value, italianIntervals = context.resources.configuration.locales[0].language == "it")

    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    fun build(r: FunctionStudyResult): CharSequence {
        line(s(R.string.study_interval, value(r.from), value(r.to)), palette.onBackgroundVariant, small = true)

        heading(R.string.study_domain, r.domainReliability)
        line(set(r.domain, always = s(R.string.study_all_interval)))

        heading(R.string.study_parity, Reliability.RELIABLE)
        line(
            when (r.parity) {
                Parity.EVEN -> s(R.string.study_even)
                Parity.ODD -> s(R.string.study_odd)
                Parity.NONE -> s(R.string.study_no_parity)
                null -> s(R.string.study_parity_unknown)
            },
        )

        heading(R.string.study_intersections, Reliability.RELIABLE)
        line(s(R.string.study_y_axis, r.yIntercept?.let { "(0; ${value(it)})" } ?: s(R.string.study_none)))
        line(
            s(
                R.string.study_x_axis,
                if (r.zeros.isEmpty()) s(R.string.study_none) else r.zeros.joinToString("; ") { "x = ${value(it)}" },
            ),
        )

        heading(R.string.study_sign, Reliability.RELIABLE)
        line(s(R.string.study_positive, set(r.positive)))
        line(s(R.string.study_negative, set(r.negative)))

        heading(R.string.study_monotonic, Reliability.RELIABLE)
        line(s(R.string.study_increasing, set(r.increasing)))
        line(s(R.string.study_decreasing, set(r.decreasing)))
        line(s(R.string.study_maxima, points(r.maxima)))
        line(s(R.string.study_minima, points(r.minima)))

        heading(R.string.study_concavity, Reliability.APPROXIMATE)
        line(s(R.string.study_convex, set(r.convex)))
        line(s(R.string.study_concave, set(r.concave)))
        line(s(R.string.study_inflections, points(r.inflections)))

        heading(R.string.study_vertical, Reliability.APPROXIMATE)
        line(if (r.vertical.isEmpty()) s(R.string.study_none) else r.vertical.joinToString("; ") { "x = ${value(it)}" })

        heading(R.string.study_lines, Reliability.RISKY)
        if (r.lines.isEmpty()) line(s(R.string.study_none))
        r.lines.forEach { line(asymptote(it)) }

        out.append('\n')
        line(s(R.string.study_legend), palette.onBackgroundVariant, small = true)
        return out
    }

    private fun set(set: SolutionSet, always: String = s(R.string.study_always)): String = when {
        set.isEmpty -> s(R.string.study_never)
        set.isAll -> always
        else -> sets.algebraic(set) ?: always
    }

    private fun points(list: List<StudyPoint>): String =
        if (list.isEmpty()) s(R.string.study_none) else list.joinToString("; ") { "(${value(it.x)}; ${value(it.y)})" }

    private fun asymptote(line: Asymptote.Line): String {
        val equation = when {
            line.m.signum() == 0 -> "y = ${value(line.q)}"
            line.q.signum() == 0 -> "y = ${coefficient(line.m)}x"
            line.q.signum() > 0 -> "y = ${coefficient(line.m)}x + ${value(line.q)}"
            else -> "y = ${coefficient(line.m)}x − ${value(line.q.negate())}"
        }
        val kind = s(if (line.m.signum() == 0) R.string.study_horizontal else R.string.study_oblique)
        val side = when (line.side) {
            1 -> "x → +∞"
            -1 -> "x → −∞"
            else -> "x → ±∞"
        }
        return "$kind: $equation ($side)"
    }

    private fun coefficient(m: BigDecimal): String = when {
        m.compareTo(BigDecimal.ONE) == 0 -> ""
        m.compareTo(BigDecimal.ONE.negate()) == 0 -> "−"
        else -> value(m)
    }

    /** Titolo della sezione, con il segno di affidabilità. */
    private fun heading(title: Int, reliability: Reliability) {
        if (out.isNotEmpty()) out.append('\n')
        val start = out.length
        out.append(s(title))
        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(ForegroundColorSpan(palette.accent), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        when (reliability) {
            Reliability.RELIABLE -> Unit
            Reliability.APPROXIMATE -> mark("  ≈", palette.onBackgroundVariant)
            Reliability.RISKY -> mark("  " + s(R.string.study_risky), palette.error)
        }
        out.append('\n')
    }

    private fun mark(text: String, color: Int) {
        val start = out.length
        out.append(text)
        out.setSpan(ForegroundColorSpan(color), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(RelativeSizeSpan(0.85f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun line(text: String, color: Int? = null, small: Boolean = false) {
        val start = out.length
        out.append(text).append('\n')
        if (color != null) out.setSpan(ForegroundColorSpan(color), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (small) out.setSpan(RelativeSizeSpan(0.85f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
