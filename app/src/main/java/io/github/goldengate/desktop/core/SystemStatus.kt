package io.github.goldengate.desktop.core

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

enum class NetKind { Wifi, Cellular, Ethernet, Other, None }

/**
 * Live device status for the menu bar and Control Center, read through public Android APIs.
 * Android does not let ordinary apps toggle Wi-Fi or Bluetooth directly (API 29+), so the
 * Control Center opens the matching system panel instead; see docs/LIMITATIONS.md.
 */
class SystemStatus(private val context: Context) {
    var batteryLevel by mutableIntStateOf(100)
        private set
    var charging by mutableStateOf(false)
        private set
    var network by mutableStateOf(NetKind.None)
        private set
    var bluetoothOn by mutableStateOf<Boolean?>(null)
        private set
    var volume by mutableFloatStateOf(0.5f)
        private set

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = readBattery(i)
    }
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            if (s == BluetoothAdapter.STATE_ON) bluetoothOn = true else if (s == BluetoothAdapter.STATE_OFF) bluetoothOn = false
        }
    }
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { updateNet(caps) }
        override fun onLost(network: Network) { this@SystemStatus.network = NetKind.None }
    }

    fun start() {
        ContextCompat.registerReceiver(context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { readBattery(it) }
        try {
            ContextCompat.registerReceiver(context, btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        } catch (_: Throwable) { }
        try {
            connectivity.registerDefaultNetworkCallback(netCallback)
            connectivity.activeNetwork?.let { n -> connectivity.getNetworkCapabilities(n)?.let { updateNet(it) } }
        } catch (_: Throwable) { }
        refreshBluetooth()
        refreshVolume()
    }

    fun stop() {
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Throwable) { }
        try { context.unregisterReceiver(btReceiver) } catch (_: Throwable) { }
        try { connectivity.unregisterNetworkCallback(netCallback) } catch (_: Throwable) { }
    }

    private fun readBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0) batteryLevel = (level * 100 / scale.coerceAtLeast(1))
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun updateNet(caps: NetworkCapabilities) {
        network = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetKind.Ethernet
            else -> NetKind.Other
        }
    }

    val hasBluetoothPermission: Boolean
        get() = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun refreshBluetooth() {
        bluetoothOn = try {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = mgr?.adapter
            if (adapter == null) null else if (!hasBluetoothPermission) null else adapter.isEnabled
        } catch (_: SecurityException) { null } catch (_: Throwable) { null }
    }

    fun refreshVolume() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun changeVolume(v: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v.coerceIn(0f, 1f) * max).toInt(), 0) } catch (_: SecurityException) { }
        volume = v.coerceIn(0f, 1f)
    }
}
