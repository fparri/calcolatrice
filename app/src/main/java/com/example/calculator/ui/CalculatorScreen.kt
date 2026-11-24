package com.example.calculator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.model.CalculatorAction
import com.example.calculator.model.CalculatorOperation
import com.example.calculator.model.CalculatorState

@Composable
fun CalculatorScreen(
    state: CalculatorState,
    onAction: (CalculatorAction) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Area Display (Material 3 Container con angoli inferiori arrotondati)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 24.dp
                    ),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                if (state.expressionHistory.isNotEmpty()) {
                    Text(
                        text = state.expressionHistory,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val displayFontSize = when {
                    state.displayText.length > 14 -> 36.sp
                    state.displayText.length > 9 -> 48.sp
                    else -> 64.sp
                }

                Text(
                    text = state.displayText,
                    fontSize = displayFontSize,
                    lineHeight = displayFontSize * 1.1f,
                    fontWeight = FontWeight.Normal,
                    color = if (state.isError) colors.error else colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }

            // Tastierino
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val spacing = 12.dp

                // Riga 1: AC, ±, %, ÷
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    CalcButton("AC", colors.tertiaryContainer, colors.onTertiaryContainer, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Clear)
                    }
                    CalcButton("±", colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f)) {
                        onAction(CalculatorAction.ToggleSign)
                    }
                    CalcButton("%", colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Percentage)
                    }
                    CalcButton("÷", colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Operation(CalculatorOperation.Divide))
                    }
                }

                // Riga 2: 7, 8, 9, ×
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    CalcButton("7", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(7))
                    }
                    CalcButton("8", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(8))
                    }
                    CalcButton("9", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(9))
                    }
                    CalcButton("×", colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Operation(CalculatorOperation.Multiply))
                    }
                }

                // Riga 3: 4, 5, 6, −
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    CalcButton("4", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(4))
                    }
                    CalcButton("5", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(5))
                    }
                    CalcButton("6", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(6))
                    }
                    CalcButton("−", colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Operation(CalculatorOperation.Subtract))
                    }
                }

                // Riga 4: 1, 2, 3, +
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    CalcButton("1", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(1))
                    }
                    CalcButton("2", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(2))
                    }
                    CalcButton("3", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(3))
                    }
                    CalcButton("+", colors.secondaryContainer, colors.onSecondaryContainer, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Operation(CalculatorOperation.Add))
                    }
                }

                // Riga 5: 0, ., ⌫, =
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    CalcButton("0", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Number(0))
                    }
                    CalcButton(".", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Decimal)
                    }
                    CalcButton("⌫", colors.surfaceContainerLow, colors.onSurface, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Delete)
                    }
                    CalcButton("=", colors.primary, colors.onPrimary, Modifier.weight(1f)) {
                        onAction(CalculatorAction.Calculate)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcButton(
    symbol: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.aspectRatio(1f),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
