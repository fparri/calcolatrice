package com.example.calculator.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.math.BigDecimal
import java.math.MathContext

enum class CalculatorOperation(val symbol: String) {
    Add("+"),
    Subtract("−"),
    Multiply("×"),
    Divide("÷")
}

data class CalculatorState(
    val number1: String = "",
    val number2: String = "",
    val operation: CalculatorOperation? = null,
    val expressionHistory: String = "",
    val isError: Boolean = false
) {
    val displayText: String
        get() = when {
            isError -> "Errore"
            operation == null -> number1.ifEmpty { "0" }
            else -> "$number1 ${operation.symbol} $number2".trim()
        }
}

sealed interface CalculatorAction {
    data class Number(val value: Int) : CalculatorAction
    data class Operation(val operation: CalculatorOperation) : CalculatorAction
    data object Decimal : CalculatorAction
    data object Clear : CalculatorAction
    data object Delete : CalculatorAction
    data object Percentage : CalculatorAction
    data object ToggleSign : CalculatorAction
    data object Calculate : CalculatorAction
}

class CalculatorViewModel : ViewModel() {

    var state by mutableStateOf(CalculatorState())
        private set

    fun onAction(action: CalculatorAction) {
        if (state.isError && action !is CalculatorAction.Clear) {
            state = CalculatorState()
        }

        when (action) {
            is CalculatorAction.Number -> enterNumber(action.value)
            is CalculatorAction.Decimal -> enterDecimal()
            is CalculatorAction.Clear -> state = CalculatorState()
            is CalculatorAction.Operation -> enterOperation(action.operation)
            is CalculatorAction.Calculate -> performCalculation()
            is CalculatorAction.Delete -> performDeletion()
            is CalculatorAction.Percentage -> applyPercentage()
            is CalculatorAction.ToggleSign -> toggleSign()
        }
    }

    private fun enterNumber(number: Int) {
        if (state.operation == null) {
            if (state.number1.length >= MAX_NUM_LENGTH) return
            val updated = if (state.number1 == "0") number.toString() else state.number1 + number
            state = state.copy(number1 = updated)
        } else {
            if (state.number2.length >= MAX_NUM_LENGTH) return
            val updated = if (state.number2 == "0") number.toString() else state.number2 + number
            state = state.copy(number2 = updated)
        }
    }

    private fun enterDecimal() {
        if (state.operation == null) {
            if (!state.number1.contains(".")) {
                val base = state.number1.ifEmpty { "0" }
                state = state.copy(number1 = "$base.")
            }
        } else {
            if (!state.number2.contains(".")) {
                val base = state.number2.ifEmpty { "0" }
                state = state.copy(number2 = "$base.")
            }
        }
    }

    private fun enterOperation(operation: CalculatorOperation) {
        if (state.number1.isBlank()) {
            state = state.copy(number1 = "0", operation = operation)
            return
        }
        if (state.number2.isNotBlank()) {
            performCalculation()
            if (state.isError) return
        }
        state = state.copy(operation = operation)
    }

    private fun performDeletion() {
        state = when {
            state.number2.isNotBlank() -> state.copy(number2 = state.number2.dropLast(1))
            state.operation != null -> state.copy(operation = null)
            state.number1.isNotBlank() -> state.copy(number1 = state.number1.dropLast(1))
            else -> state
        }
    }

    private fun applyPercentage() {
        val hundred = BigDecimal(100)
        if (state.operation == null && state.number1.isNotBlank()) {
            val value = state.number1.toBigDecimalOrNull() ?: return
            state = state.copy(number1 = formatBigDecimal(value.divide(hundred, MathContext.DECIMAL64)))
        } else if (state.number2.isNotBlank()) {
            val value = state.number2.toBigDecimalOrNull() ?: return
            state = state.copy(number2 = formatBigDecimal(value.divide(hundred, MathContext.DECIMAL64)))
        }
    }

    private fun toggleSign() {
        if (state.operation == null && state.number1.isNotBlank() && state.number1 != "0") {
            val updated = if (state.number1.startsWith("-")) state.number1.drop(1) else "-${state.number1}"
            state = state.copy(number1 = updated)
        } else if (state.number2.isNotBlank() && state.number2 != "0") {
            val updated = if (state.number2.startsWith("-")) state.number2.drop(1) else "-${state.number2}"
            state = state.copy(number2 = updated)
        }
    }

    private fun performCalculation() {
        val num1 = state.number1.toBigDecimalOrNull() ?: return
        val num2 = state.number2.toBigDecimalOrNull() ?: return
        val op = state.operation ?: return

        val history = "${state.number1} ${op.symbol} ${state.number2}"

        val result = try {
            when (op) {
                CalculatorOperation.Add -> num1.add(num2, MathContext.DECIMAL64)
                CalculatorOperation.Subtract -> num1.subtract(num2, MathContext.DECIMAL64)
                CalculatorOperation.Multiply -> num1.multiply(num2, MathContext.DECIMAL64)
                CalculatorOperation.Divide -> {
                    if (num2.compareTo(BigDecimal.ZERO) == 0) {
                        state = CalculatorState(expressionHistory = history, isError = true)
                        return
                    }
                    num1.divide(num2, MathContext.DECIMAL64)
                }
            }
        } catch (_: ArithmeticException) {
            state = CalculatorState(expressionHistory = history, isError = true)
            return
        }

        state = CalculatorState(
            number1 = formatBigDecimal(result),
            expressionHistory = history
        )
    }

    private fun formatBigDecimal(value: BigDecimal): String {
        if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
        return value.stripTrailingZeros().toPlainString()
    }

    companion object {
        private const val MAX_NUM_LENGTH = 15
    }
}
