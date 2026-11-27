package com.custom.chargingmonitor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvWatt: TextView
    private lateinit var tvChargeSpeed: TextView
    private lateinit var tvVolt: TextView
    private lateinit var tvAmpere: TextView
    private lateinit var tvMilliAmp: TextView
    private lateinit var tvLevel: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvPlugged: TextView
    private lateinit var tvHealth: TextView
    private lateinit var tvCycles: TextView
    private lateinit var tvTech: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateBatteryStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvWatt = findViewById(R.id.tvWatt)
        tvChargeSpeed = findViewById(R.id.tvChargeSpeed)
        tvVolt = findViewById(R.id.tvVolt)
        tvAmpere = findViewById(R.id.tvAmpere)
        tvMilliAmp = findViewById(R.id.tvMilliAmp)
        tvLevel = findViewById(R.id.tvLevel)
        tvTemp = findViewById(R.id.tvTemp)
        tvPlugged = findViewById(R.id.tvPlugged)
        tvHealth = findViewById(R.id.tvHealth)
        tvCycles = findViewById(R.id.tvCycles)
        tvTech = findViewById(R.id.tvTech)
    }

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateBatteryStats() {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = registerReceiver(null, intentFilter)

        // Current in microamperes -> Ampere & mA
        val currentMicro = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAbsMicro = if (currentMicro == Int.MIN_VALUE) 0 else abs(currentMicro)
        // Some OEMs return mA instead of uA if value is very small (< 10000 when charging fast)
        val ampere = if (currentAbsMicro > 10000) {
            currentAbsMicro / 1_000_000.0
        } else {
            currentAbsMicro / 1_000.0
        }
        val milliAmp = (ampere * 1000).toInt()

        // Voltage in millivolts -> Volt
        val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val volt = if (voltageMv > 10000) voltageMv / 1_000_000.0 else voltageMv / 1_000.0

        // Power in Watts
        val watt = volt * ampere

        // Status
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "PENUH"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "TIDAK MENGISI"
            else -> "UNKNOWN"
        }

        // Plugged source
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val pluggedText = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Adaptor AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "Kabel USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Baterai"
        }

        // Level
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 0

        // Temperature
        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10.0

        // Health
        val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Baik (Good)"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Rusak"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Dingin"
            else -> "Normal"
        }

        // Technology
        val tech = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        // Cycle Count (Android 14+ API 34)
        var cyclesText = "N/A"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val cycles = batteryStatus?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
            if (cycles >= 0) {
                cyclesText = "$cycles siklus"
            }
        }

        // Update UI
        tvStatusBadge.text = statusText
        if (isCharging) {
            tvStatusBadge.setTextColor(Color.parseColor("#00E676"))
            tvWatt.setTextColor(Color.parseColor("#00E676"))
            tvChargeSpeed.text = when {
                watt >= 45.0 -> "Super Fast Charging terdeteksi"
                watt >= 25.0 -> "Fast Charging terdeteksi"
                watt >= 12.0 -> "Pengisian Cepat standar"
                else -> "Pengisian daya normal"
            }
        } else {
            tvStatusBadge.setTextColor(Color.parseColor("#F87171"))
            tvWatt.setTextColor(Color.parseColor("#F87171"))
            tvChargeSpeed.text = "Konsumsi daya perangkat (Discharging)"
        }

        tvWatt.text = String.format(Locale.US, "%.2f", watt)
        tvVolt.text = String.format(Locale.US, "%.2f V", volt)
        tvAmpere.text = String.format(Locale.US, "%.2f A", ampere)
        tvMilliAmp.text = "$milliAmp mA"
        tvLevel.text = "$batteryPct%"
        tvTemp.text = String.format(Locale.US, "%.1f °C", tempCelsius)
        tvPlugged.text = pluggedText
        tvHealth.text = healthText
        tvCycles.text = cyclesText
        tvTech.text = tech
    }
}
