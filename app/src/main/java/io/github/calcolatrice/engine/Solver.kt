package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * SOLVE come sulle calcolatrici scientifiche: trova un valore dell'incognita per cui
 * l'espressione vale 0, partendo da un valore iniziale. Prima prova il metodo di Newton
 * (veloce vicino alla soluzione), poi cerca un cambio di segno allontanandosi dal valore
 * iniziale e lo restringe con bisezione e secanti.
 */
object NumericSolver {
    private val MC = MathSupport.MC
    private val TWO = BigDecimal(2)

    /** Passo relativo per la derivata numerica (differenze centrali). */
    private val DERIVATIVE_STEP = BigDecimal("1E-12")

    /** Passo relativo sotto il quale il metodo di Newton si considera arrivato. */
    private val STEP_TOLERANCE = BigDecimal("1E-30")

    /** |f| finale accettato, relativo al valore di partenza: esclude i "falsi zeri" vicino ai poli. */
    private val ACCEPT = BigDecimal("1E-20")
    private val POLE_CHECK = BigDecimal("1E-12")

    fun solve(
        expression: String,
        angleUnit: AngleUnit,
        ans: BigDecimal?,
        variables: Map<Char, BigDecimal>,
        unknown: Char = 'X',
        start: BigDecimal = BigDecimal.ZERO,
    ): EvalResult {
        val tokens = try {
            Tokenizer.tokenize(expression)
        } catch (e: CalcException) {
            return EvalResult.Failure(e.error)
        }
        if (tokens.none { it is Token.Variable && it.name == unknown }) return EvalResult.Failure(CalcError.NO_SOLUTION)

        // f(x) = valore dell'espressione; null fuori dal dominio. Gli errori di sintassi fermano tutto.
        val f: (BigDecimal) -> BigDecimal? = { x ->
            try {
                Parser(tokens, angleUnit, ans, variables + (unknown to x)).parse()
            } catch (e: CalcException) {
                if (e.error == CalcError.SYNTAX) throw e
                null
            } catch (e: ArithmeticException) {
                null
            }
        }
        return try {
            val root = newton(f, start) ?: bracket(f, start) ?: return EvalResult.Failure(CalcError.NO_SOLUTION)
            EvalResult.Success(tidy(f, root))
        } catch (e: CalcException) {
            EvalResult.Failure(e.error)
        }
    }

    private fun newton(f: (BigDecimal) -> BigDecimal?, start: BigDecimal): BigDecimal? {
        var x = start
        var fx = f(x) ?: return null
        if (fx.signum() == 0) return x
        val limit = fx.abs().max(BigDecimal.ONE).multiply(ACCEPT)
        repeat(100) {
            val scale = x.abs().max(BigDecimal.ONE)
            val h = scale.multiply(DERIVATIVE_STEP)
            val ahead = f(x.add(h)) ?: return null
            val behind = f(x.subtract(h)) ?: return null
            val derivative = ahead.subtract(behind).divide(h.multiply(TWO), MC)
            if (derivative.signum() == 0) return null
            val step = fx.divide(derivative, MC)

            // Passo smorzato: si dimezza finché |f| non diminuisce.
            var t = BigDecimal.ONE
            var next: BigDecimal? = null
            var fNext: BigDecimal? = null
            for (attempt in 0 until 40) {
                val candidate = x.subtract(step.multiply(t, MC), MC)
                val value = f(candidate)
                if (value != null && value.abs() < fx.abs()) {
                    next = candidate
                    fNext = value
                    break
                }
                t = t.divide(TWO)
            }
            // Nessun miglioramento possibile: o si è arrivati al limite di precisione o si è in un minimo.
            if (next == null || fNext == null) return if (fx.abs() <= limit) x else null

            val moved = x.subtract(next).abs()
            x = next
            fx = fNext
            if (fx.signum() == 0 || moved <= scale.multiply(STEP_TOLERANCE)) {
                return if (fx.abs() <= limit) x else null
            }
        }
        return null
    }

    /** Cerca un cambio di segno a destra e a sinistra del punto di partenza, a passi crescenti. */
    private fun bracket(f: (BigDecimal) -> BigDecimal?, start: BigDecimal): BigDecimal? {
        val growth = BigDecimal("1.6")
        var delta = start.abs().max(BigDecimal.ONE).divide(BigDecimal(100))
        var rightX = start
        var rightF = f(start)
        var leftX = start
        var leftF = rightF
        repeat(110) {
            val rx = start.add(delta)
            val rf = f(rx)
            if (rf != null && rf.signum() == 0) return rx
            if (rf != null && rightF != null && rf.signum() != rightF!!.signum()) return refine(f, rightX, rightF!!, rx, rf)
            rightX = rx
            rightF = rf

            val lx = start.subtract(delta)
            val lf = f(lx)
            if (lf != null && lf.signum() == 0) return lx
            if (lf != null && leftF != null && lf.signum() != leftF!!.signum()) return refine(f, lx, lf, leftX, leftF!!)
            leftX = lx
            leftF = lf

            delta = delta.multiply(growth, MC)
        }
        return null
    }

    /** Restringe un intervallo con cambio di segno alternando bisezione e secante (regula falsi). */
    private fun refine(
        f: (BigDecimal) -> BigDecimal?,
        a0: BigDecimal,
        fa0: BigDecimal,
        b0: BigDecimal,
        fb0: BigDecimal,
    ): BigDecimal? {
        var a = a0
        var fa = fa0
        var b = b0
        var fb = fb0
        for (iteration in 0 until 400) {
            val scale = a.abs().max(b.abs()).max(BigDecimal.ONE)
            if (b.subtract(a).abs() <= scale.multiply(STEP_TOLERANCE)) break
            val middle = a.add(b).divide(TWO, MC)
            var m = if (iteration % 2 == 0) {
                middle
            } else {
                a.subtract(fa.multiply(b.subtract(a), MC).divide(fb.subtract(fa), MC), MC)
            }
            if (m <= a.min(b) || m >= a.max(b)) m = middle
            val fm = f(m) ?: return null // un buco nel dominio dentro l'intervallo: non è uno zero
            if (fm.signum() == 0) return m
            if (fm.signum() == fa.signum()) {
                a = m
                fa = fm
            } else {
                b = m
                fb = fm
            }
        }
        val root = if (fa.abs() < fb.abs()) a else b
        val fr = f(root) ?: return null
        // Un cambio di segno attraverso un polo (es. 1/X) non è uno zero.
        val reference = fa0.abs().max(fb0.abs()).max(BigDecimal.ONE)
        return if (fr.abs() <= reference.multiply(POLE_CHECK)) root else null
    }

    /** Se un valore "più pulito" (intero, o con meno cifre) va bene almeno quanto, si usa quello. */
    private fun tidy(f: (BigDecimal) -> BigDecimal?, root: BigDecimal): BigDecimal {
        val fr = f(root)?.abs() ?: return root.round(MC)
        val candidates = listOf(
            root.setScale(0, RoundingMode.HALF_EVEN),
            root.round(java.math.MathContext(20, RoundingMode.HALF_EVEN)),
        )
        for (candidate in candidates) {
            val value = f(candidate) ?: continue
            if (value.abs() <= fr) return candidate.stripTrailingZeros().let { if (it.signum() == 0) BigDecimal.ZERO else it }
        }
        return root.round(MC).stripTrailingZeros()
    }
}
