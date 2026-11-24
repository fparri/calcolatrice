package io.github.calcolatrice.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.calcolatrice.CalculatorUiState
import io.github.calcolatrice.CalculatorViewModel
import io.github.calcolatrice.HistoryEntry
import io.github.calcolatrice.R
import io.github.calcolatrice.engine.AngleUnit
import io.github.calcolatrice.engine.CalcError
import io.github.calcolatrice.engine.CalcKey
import io.github.calcolatrice.engine.CalculatorState
import io.github.calcolatrice.engine.DisplayFormatter
import io.github.calcolatrice.engine.ResultFormatter
import io.github.calcolatrice.ui.theme.CalculatorTheme
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormatSymbols

/** Punto d'ingresso dell'interfaccia: collega il ViewModel alla schermata. */
@Composable
fun CalculatorApp(viewModel: CalculatorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalculatorScreen(
        state = state,
        onAction = { action ->
            when (action) {
                is PadAction.Input -> viewModel.onKey(action.key)
                PadAction.ToggleAngle -> viewModel.toggleAngleUnit()
                PadAction.ToggleInverse -> viewModel.toggleInverse()
            }
        },
        onUseHistoryEntry = viewModel::useHistoryValue,
        onClearHistory = viewModel::clearHistory,
    )
}

@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    onAction: (PadAction) -> Unit,
    onUseHistoryEntry: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val symbols = remember(locale) { DecimalFormatSymbols.getInstance(locale) }
    val formatter = remember(symbols) { DisplayFormatter(symbols.decimalSeparator, symbols.groupingSeparator) }
    val plainFormatter = remember(symbols) { DisplayFormatter(symbols.decimalSeparator, null) }

    var showHistory by rememberSaveable { mutableStateOf(false) }
    var scientificExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied)
    val appName = stringResource(R.string.app_name)

    // Supporto per tastiere fisiche (tablet, Chromebook, tastiere Bluetooth).
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val calc = state.calc
    val mainText = if (calc.expression.isEmpty()) "0" else formatter.format(calc.expression, calc.ans)
    val previewText = state.preview
        ?.let { formatter.format(ResultFormatter.toInternal(it)) }
        ?.takeIf { it != mainText }
    val errorText = calc.error?.let { stringResource(it.messageRes()) }
    val previousExpression = state.lastExpression?.let { formatter.format(it) + " =" }

    val copyToClipboard: () -> Unit = {
        val value = when {
            calc.justEvaluated && calc.ans != null -> ResultFormatter.toInternal(calc.ans)
            state.preview != null -> ResultFormatter.toInternal(state.preview)
            else -> calc.expression.ifEmpty { "0" }
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(appName, plainFormatter.format(value, calc.ans)))
        // Da Android 13 il sistema mostra già una conferma.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
        }
    }

    Scaffold(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event -> hardwareKeyAction(event)?.let { onAction(it); true } ?: false },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val landscape = maxWidth > maxHeight
            val display: @Composable (Modifier, TextUnit) -> Unit = { modifier, maxFont ->
                CalculatorDisplay(
                    mainText = mainText,
                    previousExpression = previousExpression,
                    secondaryText = errorText ?: previewText,
                    secondaryIsError = errorText != null,
                    maxFontSize = maxFont,
                    onLongPress = copyToClipboard,
                    modifier = modifier,
                )
            }
            val topBar: @Composable () -> Unit = {
                TopBar(
                    angleUnit = state.angleUnit,
                    onToggleAngle = { onAction(PadAction.ToggleAngle) },
                    onShowHistory = { showHistory = true },
                )
            }

            if (landscape) {
                Column(Modifier.fillMaxSize()) {
                    topBar()
                    display(Modifier.fillMaxWidth().weight(1f), 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().weight(2.2f)) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(3f).fillMaxHeight(),
                        ) {
                            KeyGrid(
                                rows = scientificKeysLandscape(state.inverse, state.angleUnit),
                                onAction = onAction,
                                textStyle = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(6.dp),
                                spacing = 4.dp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        KeyGrid(
                            rows = mainKeys(symbols.decimalSeparator),
                            onAction = onAction,
                            textStyle = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(4f).fillMaxHeight(),
                            spacing = 6.dp,
                        )
                    }
                }
            } else {
                // Su tablet in verticale il contenuto resta centrato con una larghezza leggibile.
                Column(Modifier.fillMaxHeight().widthIn(max = 600.dp)) {
                    topBar()
                    display(Modifier.fillMaxWidth().weight(1f), 72.sp)
                    Spacer(Modifier.height(8.dp))
                    ScientificPanel(
                        rows = scientificKeysPortrait(state.inverse, state.angleUnit),
                        expanded = scientificExpanded,
                        onToggleExpanded = { scientificExpanded = !scientificExpanded },
                        onAction = onAction,
                    )
                    Spacer(Modifier.height(12.dp))
                    KeyGrid(
                        rows = mainKeys(symbols.decimalSeparator),
                        onAction = onAction,
                        textStyle = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.fillMaxWidth().weight(if (scientificExpanded) 1.6f else 2f),
                    )
                }
            }
        }
    }

    if (showHistory) {
        HistorySheet(
            history = state.history,
            formatter = formatter,
            onDismiss = { showHistory = false },
            onSelect = { entry ->
                onUseHistoryEntry(entry)
                showHistory = false
            },
            onClear = onClearHistory,
        )
    }
}

@Composable
private fun TopBar(angleUnit: AngleUnit, onToggleAngle: () -> Unit, onShowHistory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val degrees = angleUnit == AngleUnit.DEGREES
        val name = stringResource(if (degrees) R.string.angle_degrees else R.string.angle_radians)
        TextButton(onClick = onToggleAngle) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShowHistory) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = stringResource(R.string.history),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalculatorDisplay(
    mainText: String,
    previousExpression: String?,
    secondaryText: String?,
    secondaryIsError: Boolean,
    maxFontSize: TextUnit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val copyLabel = stringResource(R.string.copy_result)
    Column(
        modifier = modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClickLabel = copyLabel,
                onLongClick = onLongPress,
                onClick = {},
            )
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End,
    ) {
        if (previousExpression != null) {
            Text(
                text = previousExpression,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicText(
            text = mainText,
            style = MaterialTheme.typography.displayLarge.copy(
                color = colors.onSurface,
                textAlign = TextAlign.End,
            ),
            maxLines = 3,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 24.sp,
                maxFontSize = maxFontSize,
                stepSize = 2.sp,
            ),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        )
        Text(
            text = secondaryText.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (secondaryIsError) colors.error else colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Pannello delle funzioni scientifiche (verticale): una riga sempre visibile, le altre a scomparsa. */
@Composable
private fun ScientificPanel(
    rows: List<List<PadKey>>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onAction: (PadAction) -> Unit,
) {
    val textStyle = MaterialTheme.typography.titleMedium
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyRow(rows.first(), onAction, textStyle, Modifier.weight(1f).height(44.dp), 4.dp)
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(44.dp)) {
                    Icon(
                        painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                        contentDescription = stringResource(
                            if (expanded) R.string.hide_scientific else R.string.show_scientific,
                        ),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rows.drop(1).forEach { row ->
                        Row {
                            KeyRow(row, onAction, textStyle, Modifier.weight(1f).height(44.dp), 4.dp)
                            Spacer(Modifier.width(44.dp))
                        }
                    }
                }
            }
        }
    }
}

@StringRes
private fun CalcError.messageRes(): Int = when (this) {
    CalcError.DIVISION_BY_ZERO -> R.string.error_division_by_zero
    CalcError.UNDEFINED -> R.string.error_undefined
    CalcError.OVERFLOW -> R.string.error_overflow
    CalcError.SYNTAX -> R.string.error_syntax
}

/** Traduce i tasti della tastiera fisica in azioni della calcolatrice. */
private fun hardwareKeyAction(event: KeyEvent): PadAction? {
    if (event.type != KeyEventType.KeyDown) return null
    val key = when (event.key) {
        Key.Enter, Key.NumPadEnter -> CalcKey.Equals
        Key.Backspace -> CalcKey.Backspace
        Key.Escape, Key.Delete -> CalcKey.Clear
        else -> when (val c = event.utf16CodePoint.toChar()) {
            in '0'..'9' -> CalcKey.Digit(c)
            '.', ',' -> CalcKey.Decimal
            '+', '-', '*', '/', '^' -> CalcKey.Operator(c)
            '%' -> CalcKey.Percent
            '!' -> CalcKey.Factorial
            '(' -> CalcKey.OpenParen
            ')' -> CalcKey.CloseParen
            '=' -> CalcKey.Equals
            else -> null
        }
    }
    return key?.let { PadAction.Input(it) }
}

@Preview(name = "Verticale", widthDp = 400, heightDp = 860)
@Preview(name = "Orizzontale", widthDp = 860, heightDp = 400)
@Composable
private fun CalculatorScreenPreview() {
    CalculatorTheme(dynamicColor = false) {
        CalculatorScreen(
            state = CalculatorUiState(
                calc = CalculatorState(expression = "1234*5.6+√2"),
                preview = BigDecimal("6911.81421356237"),
            ),
            onAction = {},
            onUseHistoryEntry = {},
            onClearHistory = {},
        )
    }
}
