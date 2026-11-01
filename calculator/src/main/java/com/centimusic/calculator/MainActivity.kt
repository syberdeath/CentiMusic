package com.centimusic.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.centimusic.calculator.ui.CalculatorScreen
import com.centimusic.calculator.ui.CentiCalcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CentiCalcTheme { CalculatorScreen() } }
    }
}
