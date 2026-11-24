package com.example.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calculator.model.CalculatorViewModel
import com.example.calculator.ui.CalculatorScreen
import com.example.calculator.ui.theme.CalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalculatorTheme {
                val viewModel: CalculatorViewModel = viewModel()
                CalculatorScreen(
                    state = viewModel.state,
                    onAction = viewModel::onAction
                )
            }
        }
    }
}
