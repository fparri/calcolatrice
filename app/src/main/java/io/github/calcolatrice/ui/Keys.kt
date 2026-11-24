package io.github.calcolatrice.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.calcolatrice.R
import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalcKey

/** Azioni dei tasti: input per l'editor oppure interruttori della modalità. */
sealed interface PadAction {
    data class Input(val key: CalcKey) : PadAction
    data object ToggleInverse : PadAction
    data object ToggleAngle : PadAction
}

enum class KeyStyle { Number, Operator, Clear, Equals, Function, Toggle }

data class PadKey(
    val label: String,
    val action: PadAction,
    val style: KeyStyle,
    /** Testo letto da TalkBack; se null viene letta l'etichetta. */
    val description: String? = null,
    @param:DrawableRes val icon: Int? = null,
    val active: Boolean = false,
    val longPressAction: PadAction? = null,
)

private fun input(key: CalcKey) = PadAction.Input(key)

private fun digit(d: Char) = PadKey(d.toString(), input(CalcKey.Digit(d)), KeyStyle.Number)

/** Tastierino principale: 4 colonne × 5 righe. */
@Composable
fun mainKeys(decimalSeparator: Char): List<List<PadKey>> = listOf(
    listOf(
        PadKey("AC", input(CalcKey.Clear), KeyStyle.Clear, stringResource(R.string.key_clear)),
        PadKey("( )", input(CalcKey.Parentheses), KeyStyle.Operator, stringResource(R.string.key_parentheses)),
        PadKey("%", input(CalcKey.Percent), KeyStyle.Operator, stringResource(R.string.key_percent)),
        PadKey("÷", input(CalcKey.Operator('/')), KeyStyle.Operator, stringResource(R.string.key_divide)),
    ),
    listOf(
        digit('7'), digit('8'), digit('9'),
        PadKey("×", input(CalcKey.Operator('*')), KeyStyle.Operator, stringResource(R.string.key_multiply)),
    ),
    listOf(
        digit('4'), digit('5'), digit('6'),
        PadKey("−", input(CalcKey.Operator('-')), KeyStyle.Operator, stringResource(R.string.key_minus)),
    ),
    listOf(
        digit('1'), digit('2'), digit('3'),
        PadKey("+", input(CalcKey.Operator('+')), KeyStyle.Operator, stringResource(R.string.key_plus)),
    ),
    listOf(
        digit('0'),
        PadKey(
            decimalSeparator.toString(), input(CalcKey.Decimal), KeyStyle.Number,
            stringResource(R.string.key_decimal),
        ),
        PadKey(
            "", input(CalcKey.Backspace), KeyStyle.Number, stringResource(R.string.key_backspace),
            icon = R.drawable.ic_backspace, longPressAction = input(CalcKey.Clear),
        ),
        PadKey("=", input(CalcKey.Equals), KeyStyle.Equals, stringResource(R.string.key_equals)),
    ),
)

@Composable
private fun inverseKey(inverse: Boolean) = PadKey(
    stringResource(R.string.inverse_short), PadAction.ToggleInverse, KeyStyle.Toggle,
    stringResource(R.string.inverse), active = inverse,
)

@Composable
private fun angleKey(angleUnit: AngleUnit): PadKey {
    val degrees = angleUnit == AngleUnit.DEGREES
    val name = stringResource(if (degrees) R.string.angle_degrees else R.string.angle_radians)
    return PadKey(
        stringResource(if (degrees) R.string.angle_degrees_short else R.string.angle_radians_short),
        PadAction.ToggleAngle, KeyStyle.Toggle, stringResource(R.string.angle_toggle, name),
    )
}

@Composable
private fun function(label: String, name: String, description: Int) =
    PadKey(label, input(CalcKey.Function(name)), KeyStyle.Function, stringResource(description))

@Composable
private fun trigKeys(inverse: Boolean) = if (inverse) {
    listOf(
        function("sin⁻¹", "asin", R.string.key_asin),
        function("cos⁻¹", "acos", R.string.key_acos),
        function("tan⁻¹", "atan", R.string.key_atan),
    )
} else {
    listOf(
        function("sin", "sin", R.string.key_sin),
        function("cos", "cos", R.string.key_cos),
        function("tan", "tan", R.string.key_tan),
    )
}

@Composable
private fun logKeys(inverse: Boolean) = if (inverse) {
    listOf(
        PadKey("eˣ", input(CalcKey.ExpE), KeyStyle.Function, stringResource(R.string.key_exp_e)),
        PadKey("10ˣ", input(CalcKey.Exp10), KeyStyle.Function, stringResource(R.string.key_exp_10)),
    )
} else {
    listOf(
        function("ln", "ln", R.string.key_ln),
        function("log", "log", R.string.key_log),
    )
}

@Composable
private fun rootKey(inverse: Boolean) = if (inverse) {
    PadKey("x²", input(CalcKey.Square), KeyStyle.Function, stringResource(R.string.key_square))
} else {
    PadKey("√", input(CalcKey.SquareRoot), KeyStyle.Function, stringResource(R.string.key_sqrt))
}

@Composable
private fun powerKey() =
    PadKey("xʸ", input(CalcKey.Operator('^')), KeyStyle.Function, stringResource(R.string.key_power))

@Composable
private fun piKey() =
    PadKey("π", input(CalcKey.Constant('π')), KeyStyle.Function, stringResource(R.string.key_pi))

@Composable
private fun eKey() =
    PadKey("e", input(CalcKey.Constant('e')), KeyStyle.Function, stringResource(R.string.key_e))

@Composable
private fun factorialKey() =
    PadKey("x!", input(CalcKey.Factorial), KeyStyle.Function, stringResource(R.string.key_factorial))

/** Funzioni scientifiche in verticale: 4 colonne × 3 righe (la prima sempre visibile). */
@Composable
fun scientificKeysPortrait(inverse: Boolean, angleUnit: AngleUnit): List<List<PadKey>> = listOf(
    listOf(inverseKey(inverse), angleKey(angleUnit), rootKey(inverse), powerKey()),
    trigKeys(inverse) + piKey(),
    logKeys(inverse) + factorialKey() + eKey(),
)

/** Funzioni scientifiche in orizzontale: 3 colonne × 5 righe, allineate al tastierino. */
@Composable
fun scientificKeysLandscape(inverse: Boolean, angleUnit: AngleUnit): List<List<PadKey>> = listOf(
    listOf(inverseKey(inverse), angleKey(angleUnit), powerKey()),
    trigKeys(inverse),
    logKeys(inverse) + rootKey(inverse),
    listOf(piKey(), eKey(), factorialKey()),
    listOf(
        PadKey("(", input(CalcKey.OpenParen), KeyStyle.Function, stringResource(R.string.key_open_paren)),
        PadKey(")", input(CalcKey.CloseParen), KeyStyle.Function, stringResource(R.string.key_close_paren)),
        PadKey("1/x", input(CalcKey.Reciprocal), KeyStyle.Function, stringResource(R.string.key_reciprocal)),
    ),
)
