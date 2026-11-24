package io.github.calcolatrice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.calcolatrice.ui.CalculatorApp
import io.github.calcolatrice.ui.theme.CalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // L'interfaccia si estende sotto la barra di stato e di navigazione (edge-to-edge).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CalculatorTheme {
                CalculatorApp()
            }
        }
    }
}
