package com.hkm.carlauncher.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Current time and date text; updated every minute and when the time, zone or 12/24 h setting changes. */
class ClockState(
    private val context: Context,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private val _text = MutableStateFlow(compute())
    val text: StateFlow<ClockText> = _text.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        // System broadcasts only; registering on the application context keeps it for the app's life.
        ContextCompat.registerReceiver(context.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Also called on resume, so a changed 12/24 h setting shows without waiting a minute. */
    fun refresh() {
        _text.value = compute()
    }

    private fun compute(): ClockText =
        formatClock(clock(), DateFormat.is24HourFormat(context), Locale.getDefault())
}
