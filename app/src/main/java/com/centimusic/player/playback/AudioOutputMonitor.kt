package com.centimusic.player.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OutputKind { PHONE, WIRED, BLUETOOTH, USB, OTHER }

data class OutputDevice(val name: String, val kind: OutputKind)

/** Tracks which audio output the system will route media to (best effort; Android doesn't expose it directly). */
class AudioOutputMonitor(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _device = MutableStateFlow(resolve())
    val device: StateFlow<OutputDevice> = _device.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { _device.value = resolve() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) { _device.value = resolve() }
    }

    init {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun resolve(): OutputDevice {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        fun find(vararg types: Int) = outputs.firstOrNull { it.type in types }
        val bleTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intArrayOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
        } else {
            intArrayOf()
        }
        find(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, *bleTypes)?.let {
            return OutputDevice(it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: "Bluetooth", OutputKind.BLUETOOTH)
        }
        find(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)?.let {
            return OutputDevice(it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: "USB audio", OutputKind.USB)
        }
        find(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)?.let {
            return OutputDevice("Wired headphones", OutputKind.WIRED)
        }
        return OutputDevice("This phone", OutputKind.PHONE)
    }
}
