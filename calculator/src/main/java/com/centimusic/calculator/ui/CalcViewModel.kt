package com.centimusic.calculator.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.centimusic.calculator.engine.AngleMode
import com.centimusic.calculator.engine.CalcState
import com.centimusic.calculator.engine.Calculator
import com.centimusic.calculator.engine.HistoryEntry
import com.centimusic.calculator.engine.Key
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds the calculator state; history, angle mode and the scientific toggle survive restarts. */
class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("centicalc", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        CalcState(
            angle = if (prefs.getString(KEY_ANGLE, null) == AngleMode.RAD.name) AngleMode.RAD else AngleMode.DEG,
            history = loadHistory(),
        ),
    )
    val state: StateFlow<CalcState> = _state.asStateFlow()

    private val _scientific = MutableStateFlow(prefs.getBoolean(KEY_SCI, false))
    val scientific: StateFlow<Boolean> = _scientific.asStateFlow()

    fun press(key: Key) {
        val before = _state.value
        _state.update { Calculator.reduce(it, key) }
        val after = _state.value
        if (after.history !== before.history) saveHistory(after.history)
        if (after.angle != before.angle) prefs.edit { putString(KEY_ANGLE, after.angle.name) }
    }

    fun recall(entry: HistoryEntry) = _state.update { Calculator.recall(it, entry) }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
        saveHistory(emptyList())
    }

    fun setScientific(on: Boolean) {
        _scientific.value = on
        prefs.edit { putBoolean(KEY_SCI, on) }
    }

    private fun loadHistory(): List<HistoryEntry> =
        prefs.getString(KEY_HISTORY, null).orEmpty().lines().mapNotNull { line ->
            val parts = line.split(SEP)
            if (parts.size == 3) HistoryEntry(parts[0], parts[1], parts[2]) else null
        }

    private fun saveHistory(history: List<HistoryEntry>) = prefs.edit {
        putString(KEY_HISTORY, history.joinToString("\n") { listOf(it.expression, it.result, it.resultToken).joinToString(SEP) })
    }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_ANGLE = "angle"
        const val KEY_SCI = "scientific"
        const val SEP = "\u001F" // unit separator: never typed on the keypad
    }
}
