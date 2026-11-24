package com.example.calculatorlite

import java.math.BigDecimal
import java.math.MathContext

enum class Operation(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷")
}

class CalculatorEngine {
    var number1: String = ""
        private set
    var number2: String = ""
        private set
    var operation: Operation? = null
        private set
    var history: String = ""
        private set
    var isError: Boolean = false
        private set

    val displayText: String
        get() = when {
            isError -> "Errore"
            operation == null -> number1.ifEmpty { "0" }
            else -> "$number1 ${operation!!.symbol} $number2".trim()
        }

    fun onNumber(digit: Int) {
        resetIfError()
        if (operation == null) {
            if (number1.length >= MAX_LENGTH) return
            number1 = if (number1 == "0") digit.toString() else number1 + digit
        } else {
            if (number2.length >= MAX_LENGTH) return
            number2 = if (number2 == "0") digit.toString() else number2 + digit
        }
    }

    fun onDecimal() {
        resetIfError()
        if (operation == null) {
            if (!number1.contains(".")) {
                number1 = "${number1.ifEmpty { "0" }}."
            }
        } else {
            if (!number2.contains(".")) {
                number2 = "${number2.ifEmpty { "0" }}."
            }
        }
    }

    fun onOperation(op: Operation) {
        resetIfError()
        if (number1.isBlank()) {
            number1 = "0"
            operation = op
            return
        }
        if (number2.isNotBlank()) {
            onCalculate()
            if (isError) return
        }
        operation = op
    }

    fun onClear() {
        number1 = ""
        number2 = ""
        operation = null
        history = ""
        isError = false
    }

    fun onDelete() {
        if (isError) {
            onClear()
            return
        }
        when {
            number2.isNotBlank() -> number2 = number2.dropLast(1)
            operation != null -> operation = null
            number1.isNotBlank() -> number1 = number1.dropLast(1)
        }
    }

    fun onPercentage() {
        resetIfError()
        val hundred = BigDecimal(100)
        if (operation == null && number1.isNotBlank()) {
            val v = number1.toBigDecimalOrNull() ?: return
            number1 = format(v.divide(hundred, MathContext.DECIMAL64))
        } else if (number2.isNotBlank()) {
            val v = number2.toBigDecimalOrNull() ?: return
            number2 = format(v.divide(hundred, MathContext.DECIMAL64))
        }
    }

    fun onToggleSign() {
        resetIfError()
        if (operation == null && number1.isNotBlank() && number1 != "0") {
            number1 = if (number1.startsWith("-")) number1.drop(1) else "-$number1"
        } else if (number2.isNotBlank() && number2 != "0") {
            number2 = if (number2.startsWith("-")) number2.drop(1) else "-$number2"
        }
    }

    fun onCalculate() {
        val n1 = number1.toBigDecimalOrNull() ?: return
        val n2 = number2.toBigDecimalOrNull() ?: return
        val op = operation ?: return

        val expr = "$number1 ${op.symbol} $number2"

        val result = try {
            when (op) {
                Operation.ADD -> n1.add(n2, MathContext.DECIMAL64)
                Operation.SUBTRACT -> n1.subtract(n2, MathContext.DECIMAL64)
                Operation.MULTIPLY -> n1.multiply(n2, MathContext.DECIMAL64)
                Operation.DIVIDE -> {
                    if (n2.compareTo(BigDecimal.ZERO) == 0) {
                        setError(expr)
                        return
                    }
                    n1.divide(n2, MathContext.DECIMAL64)
                }
            }
        } catch (_: ArithmeticException) {
            setError(expr)
            return
        }

        number1 = format(result)
        number2 = ""
        operation = null
        history = expr
        isError = false
    }

    private fun resetIfError() {
        if (isError) onClear()
    }

    private fun setError(expr: String) {
        number1 = ""
        number2 = ""
        operation = null
        history = expr
        isError = true
    }

    private fun format(value: BigDecimal): String {
        if (value.compareTo(BigDecimal.ZERO) == 0) return "0"
        return value.stripTrailingZeros().toPlainString()
    }

    companion object {
        private const val MAX_LENGTH = 15
    }
}
