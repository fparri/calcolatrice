package io.github.calcolatrice.engine

import java.math.BigDecimal
import java.math.MathContext
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

    /** Se la derivata risulta zero il passo cresce di questo fattore, fino a [MAX_DERIVATIVE_STEP]. */
    private val STEP_GROWTH = BigDecimal("1E3")
    private val MAX_DERIVATIVE_STEP = BigDecimal("1E-3")

    /** Passo relativo sotto il quale il metodo di Newton si considera arrivato. */
    private val STEP_TOLERANCE = BigDecimal("1E-30")

    /**
     * Uno zero senza cambio di segno (come per (X−1)²) è accettato solo se |f| è al livello del rumore
     * di arrotondamento: questa frazione della grandezza dei termini sommati nell'espressione.
     */
    private val NOISE = BigDecimal("1E-31")

    /** Ampiezza relativa dell'intorno in cui si cerca un cambio di segno attorno a uno zero trovato. */
    private val SIGN_CHECK = BigDecimal("1E-25")

    private val POLE_CHECK = BigDecimal("1E-12")

    /** Ricerca del cambio di segno: passi che raddoppiano, fino a circa 10⁴⁰ volte il passo iniziale. */
    private const val BRACKET_STEPS = 140

    /** Valore di f in un punto, con la grandezza dei termini sommati per stimare il rumore. */
    private class Sample(val value: BigDecimal, val magnitude: BigDecimal)

    fun solve(
        expression: String,
        angleUnit: AngleUnit,
        ans: BigDecimal?,
        variables: Map<Char, BigDecimal>,
        unknown: Char = 'X',
        start: BigDecimal = BigDecimal.ZERO,
    ): EvalResult {
        val root = try {
            ExpressionParser.parse(expression)
        } catch (e: CalcException) {
            return EvalResult.Failure(e.error)
        }
        if (!root.uses(unknown)) return EvalResult.Failure(CalcError.NO_SOLUTION)

        // f(x) = valore dell'espressione; null fuori dal dominio. Ans mancante (errore di sintassi) ferma tutto.
        val sample: (BigDecimal) -> Sample? = { x ->
            try {
                val evaluation = Evaluation(angleUnit, ans, variables + (unknown to x))
                val value = evaluation.result(root)
                Sample(value, evaluation.magnitude.max(value.abs()))
            } catch (e: CalcException) {
                if (e.error == CalcError.SYNTAX) throw e
                null
            } catch (e: ArithmeticException) {
                null
            }
        }
        return try {
            val root = newton(sample, start) ?: bracket(sample, start) ?: return EvalResult.Failure(CalcError.NO_SOLUTION)
            EvalResult.Success(tidy({ sample(it)?.value }, root))
        } catch (e: CalcException) {
            EvalResult.Failure(e.error)
        }
    }

    /**
     * Zero di f nell'intervallo [a, b], se f cambia segno agli estremi (usato dalla schermata Funzioni).
     * Restituisce null se non c'è cambio di segno o se il cambio di segno è un polo.
     */
    fun rootBetween(f: (BigDecimal) -> BigDecimal?, a: BigDecimal, b: BigDecimal): BigDecimal? {
        val fa = f(a) ?: return null
        val fb = f(b) ?: return null
        if (fa.signum() == 0) return a
        if (fb.signum() == 0) return b
        if (fa.signum() == fb.signum()) return null
        return refine(f, a, fa, b, fb)?.let { tidy(f, it) }
    }

    private fun newton(f: (BigDecimal) -> Sample?, start: BigDecimal): BigDecimal? {
        var x = start
        var current = f(x) ?: return null
        var fx = current.value
        if (fx.signum() == 0) return x
        repeat(100) {
            val scale = x.abs().max(BigDecimal.ONE)
            val derivative = derivative(f, x, scale) ?: return null
            val step = fx.divide(derivative, MC)

            // Passo smorzato: si dimezza finché |f| non diminuisce.
            var t = BigDecimal.ONE
            var next: BigDecimal? = null
            var nextSample: Sample? = null
            for (attempt in 0 until 40) {
                val candidate = x.subtract(step.multiply(t, MC), MC)
                val value = f(candidate)
                if (value != null && value.value.abs() < fx.abs()) {
                    next = candidate
                    nextSample = value
                    break
                }
                t = t.divide(TWO)
            }
            // Nessun miglioramento possibile: o si è arrivati al limite di precisione o si è in un minimo di |f|.
            if (next == null || nextSample == null) return accept(f, x, current)

            val moved = x.subtract(next).abs()
            x = next
            current = nextSample
            fx = current.value
            if (fx.signum() == 0 || moved <= scale.multiply(STEP_TOLERANCE)) return accept(f, x, current)
        }
        // Zeri multipli: Newton converge lentamente, ma dopo 100 passi si è comunque molto vicini.
        return accept(f, x, current)
    }

    /**
     * Derivata numerica con differenze centrali. Se f è molto grande rispetto al passo (per esempio
     * X − 10²⁵ vicino a 0), x ± h si perde nell'arrotondamento e la derivata risulta zero:
     * allora il passo cresce finché la differenza diventa visibile.
     */
    private fun derivative(f: (BigDecimal) -> Sample?, x: BigDecimal, scale: BigDecimal): BigDecimal? {
        var h = scale.multiply(DERIVATIVE_STEP)
        val maxStep = scale.multiply(MAX_DERIVATIVE_STEP)
        while (true) {
            val ahead = f(x.add(h)) ?: return null
            val behind = f(x.subtract(h)) ?: return null
            val derivative = ahead.value.subtract(behind.value).divide(h.multiply(TWO), MC)
            if (derivative.signum() != 0) return derivative
            if (h >= maxStep) return null
            h = h.multiply(STEP_GROWTH)
        }
    }

    /**
     * Il punto in cui Newton si è fermato, se è uno zero; altrimenti un valore arrotondato vicino
     * in cui f vale esattamente 0 (zeri multipli come X², dove Newton si avvicina solo lentamente), o null.
     */
    private fun accept(f: (BigDecimal) -> Sample?, x: BigDecimal, sample: Sample): BigDecimal? {
        if (isRoot(f, x, sample)) return x
        val candidates = listOf(
            x.setScale(0, RoundingMode.HALF_EVEN),
            x.round(MathContext(20, RoundingMode.HALF_EVEN)),
        )
        return candidates.firstOrNull { f(it)?.value?.signum() == 0 }
    }

    /**
     * Il punto trovato è davvero uno zero? Sì se f vale 0, se f cambia segno in un intorno minuscolo
     * oppure se |f| è al livello del rumore di arrotondamento (zeri "tangenti" come in (X−1)²).
     * Così (X−1)² + 10⁻³⁰, sempre positiva, non ha soluzioni.
     */
    private fun isRoot(f: (BigDecimal) -> Sample?, x: BigDecimal, sample: Sample): Boolean {
        val fx = sample.value
        if (fx.signum() == 0) return true
        if (fx.abs() <= sample.magnitude.multiply(NOISE)) return true
        val delta = x.abs().max(BigDecimal.ONE).multiply(SIGN_CHECK)
        val left = f(x.subtract(delta))?.value ?: return false
        val right = f(x.add(delta))?.value ?: return false
        return left.signum() != fx.signum() || right.signum() != fx.signum()
    }

    /** Cerca un cambio di segno a destra e a sinistra del punto di partenza, a passi crescenti. */
    private fun bracket(sample: (BigDecimal) -> Sample?, start: BigDecimal): BigDecimal? {
        val f: (BigDecimal) -> BigDecimal? = { sample(it)?.value }
        var delta = start.abs().max(BigDecimal.ONE).divide(BigDecimal(100))
        var rightX = start
        var rightF = f(start)
        var leftX = start
        var leftF = rightF
        repeat(BRACKET_STEPS) {
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

            delta = delta.multiply(TWO, MC)
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
            root.round(MathContext(20, RoundingMode.HALF_EVEN)),
        )
        for (candidate in candidates) {
            val value = f(candidate) ?: continue
            if (value.abs() <= fr) return candidate.stripTrailingZeros().let { if (it.signum() == 0) BigDecimal.ZERO else it }
        }
        return root.round(MC).stripTrailingZeros()
    }
}
