package com.only1hfx.callcheck

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.telephony.CellInfo
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.only1hfx.callcheck.databinding.ActivityMainBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        runSignalCheck()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.checkButton.setOnClickListener {
            if (hasRequiredPermissions()) {
                runSignalCheck()
            } else {
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }

        runSignalCheck()
    }

    private fun hasRequiredPermissions(): Boolean {
        val phonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
        val locationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return phonePermission == PackageManager.PERMISSION_GRANTED &&
            locationPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun runSignalCheck() {
        val telephony = getSystemService(TelephonyManager::class.java)
        val connectivityManager = getSystemService(ConnectivityManager::class.java)

        val networkType = telephony?.networkType ?: 0
        val operator = telephony?.networkOperatorName ?: "Unknown"

        val signalLevel = getSignalLevel(telephony)
        val callReadiness = evaluateSignal(signalLevel, networkType)

        binding.statusLabel.text = "Status: ${callReadiness.label}"
        binding.resultText.text = callReadiness.message
        binding.networkTypeText.text = "Type: ${networkTypeName(networkType)}"
        binding.operatorText.text = "Operator: $operator"
        binding.recommendationText.text = "Recommendation: ${callReadiness.recommendation}"
        binding.signalText.text = signalLevel.toString()

        val isConnected = connectivityManager?.activeNetworkInfo?.isConnected == true
        binding.downloadText.text = if (isConnected) "${(signalLevel * 10 + 15).coerceAtMost(100)}" else "--"
        binding.uploadText.text = if (isConnected) "${(signalLevel * 8 + 6).coerceAtMost(80)}" else "--"
        binding.pingText.text = if (isConnected) "${(30 + (4 - signalLevel) * 25).coerceAtLeast(18)}" else "--"
    }

    private fun getSignalLevel(telephony: TelephonyManager?): Int {
        if (telephony == null) return 0

        val strength = telephony.signalStrength
        val levelFromStrength = when {
            strength != null -> {
                val gsm = strength.gsmSignalStrength
                val cdma = strength.cdmaDbm
                val evdo = strength.evdoDbm
                when {
                    gsm > 0 -> (gsm / 30.0 * 4.0).roundToInt().coerceIn(0, 4)
                    cdma > 0 -> (cdma / 100.0 * 4.0).roundToInt().coerceIn(0, 4)
                    evdo > 0 -> (evdo / 100.0 * 4.0).roundToInt().coerceIn(0, 4)
                    else -> 0
                }
            }
            else -> {
                val cellInfos = telephony.allCellInfo.orEmpty()
                val values = cellInfos.mapNotNull { cellInfo ->
                    when (cellInfo) {
                        is android.telephony.CellInfoGsm -> cellInfo.cellSignalStrength.level
                        is android.telephony.CellInfoCdma -> cellInfo.cellSignalStrength.level
                        is android.telephony.CellInfoLte -> cellInfo.cellSignalStrength.level
                        is android.telephony.CellInfoWcdma -> cellInfo.cellSignalStrength.level
                        is android.telephony.CellInfoNr -> cellInfo.cellSignalStrength.level
                        else -> null
                    }
                }
                if (values.isEmpty()) 0 else values.maxOrNull() ?: 0
            }
        }

        return levelFromStrength.coerceIn(0, 4)
    }

    data class Readiness(
        val label: String,
        val message: String,
        val recommendation: String
    )

    private fun evaluateSignal(level: Int, networkType: Int): Readiness {
        val strongNetworks = setOf(
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_NR,
            TelephonyManager.NETWORK_TYPE_5G_NR,
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_EHRPD
        )

        val isStrongNetwork = networkType in strongNetworks

        return when {
            level >= 3 && isStrongNetwork -> Readiness(
                "Strong",
                "Your signal looks strong and calls should work reliably.",
                "Good time to make the call."
            )
            level >= 2 -> Readiness(
                "Fair",
                "Connection is usable, but quality may fluctuate during calls.",
                "Only if necessary, and keep your signal stable."
            )
            else -> Readiness(
                "Weak",
                "Signal is weak, so calls may fail or drop.",
                "Avoid making an important call until the network improves."
            )
        }
    }

    private fun networkTypeName(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G"
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_NR, TelephonyManager.NETWORK_TYPE_5G_NR -> "5G"
            else -> "Unknown"
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
