package io.github.calcolatrice.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Griglia di tasti che occupa tutto lo spazio disponibile. */
@Composable
fun KeyGrid(
    rows: List<List<PadKey>>,
    onAction: (PadAction) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        rows.forEach { row ->
            KeyRow(row, onAction, textStyle, Modifier.fillMaxWidth().weight(1f), spacing)
        }
    }
}

@Composable
fun KeyRow(
    keys: List<PadKey>,
    onAction: (PadAction) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        keys.forEach { key ->
            CalculatorButton(key, onAction, textStyle, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/**
 * Tasto in stile Material 3 Expressive: forma a pillola che si "squadra" quando viene premuto,
 * feedback aptico, ripple e descrizione per TalkBack.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorButton(
    key: PadKey,
    onAction: (PadAction) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (containerTarget, contentColor) = when (key.style) {
        KeyStyle.Number -> colors.surfaceContainerHighest to colors.onSurface
        KeyStyle.Operator -> colors.secondaryContainer to colors.onSecondaryContainer
        KeyStyle.Clear -> colors.tertiaryContainer to colors.onTertiaryContainer
        KeyStyle.Equals -> colors.primary to colors.onPrimary
        KeyStyle.Function -> Color.Transparent to colors.onSurface
        KeyStyle.Toggle -> if (key.active) {
            colors.secondaryContainer to colors.onSecondaryContainer
        } else {
            Color.Transparent to colors.primary
        }
    }
    val container by animateColorAsState(containerTarget, label = "keyContainer")

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cornerPercent by animateIntAsState(if (pressed) 28 else 50, label = "keyCorner")
    val shape = RoundedCornerShape(percent = cornerPercent)
    val view = LocalView.current

    Box(
        modifier = modifier
            .clip(shape)
            .background(container)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                // Il feedback aptico della pressione prolungata è già fornito da combinedClickable.
                onLongClick = key.longPressAction?.let { action -> { onAction(action) } },
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAction(key.action)
                },
            )
            .semantics {
                key.description?.let { contentDescription = it }
                if (key.style == KeyStyle.Toggle) selected = key.active
            },
        contentAlignment = Alignment.Center,
    ) {
        if (key.icon != null) {
            Icon(
                painter = painterResource(key.icon),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Text(
                text = key.label,
                style = textStyle,
                color = contentColor,
                maxLines = 1,
                modifier = if (key.description != null) Modifier.clearAndSetSemantics { } else Modifier,
            )
        }
    }
}
