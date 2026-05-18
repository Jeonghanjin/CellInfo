package com.cellinfo.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvInfo: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnExport: Button
    private lateinit var btnToggleLog: Button
    private lateinit var tm: TelephonyManager

    private val handler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 2000L
    private var autoRefresh = true
    private var logging = false
    private val logBuffer = mutableListOf<String>()

    private val PERMISSION_REQ = 100

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateCellInfo()
            if (autoRefresh) handler.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvInfo = findViewById(R.id.tvInfo)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnExport = findViewById(R.id.btnExport)
        btnToggleLog = findViewById(R.id.btnToggleLog)

        tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        btnRefresh.setOnClickListener { updateCellInfo() }
        btnExport.setOnClickListener { exportCsv() }
        btnToggleLog.setOnClickListener { toggleLogging() }

        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQ)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ) {
            updateCellInfo()
        }
    }

    private fun updateCellInfo() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            tvInfo.text = "권한이 필요합니다. 설정에서 위치 및 전화 권한을 허용해주세요."
            return
        }

        val sb = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        sb.append("⏰ $timestamp\n\n")

        // 기본 통신사/네트워크 정보
        sb.append("━━━ 📱 디바이스 ━━━\n")
        sb.append("제조사: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n")

        sb.append("━━━ 📡 통신사 ━━━\n")
        sb.append("Operator: ${tm.networkOperatorName}\n")
        sb.append("PLMN (MCC+MNC): ${tm.networkOperator}\n")
        sb.append("SIM Operator: ${tm.simOperatorName}\n")
        sb.append("SIM PLMN: ${tm.simOperator}\n")
        sb.append("Country: ${tm.networkCountryIso}\n")
        sb.append("Network Type: ${getNetworkTypeName(tm.dataNetworkType)}\n")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            sb.append("Phone Type: ${getPhoneTypeName(tm.phoneType)}\n")
        }
        sb.append("\n")

        // 셀 정보
        try {
            val cells: List<CellInfo>? = tm.allCellInfo
            if (cells.isNullOrEmpty()) {
                sb.append("⚠️ 셀 정보를 가져올 수 없습니다.\n")
                sb.append("(권한 부여 후 잠시 기다리거나 새로고침)\n")
            } else {
                val registered = cells.filter { it.isRegistered }
                val neighbors = cells.filter { !it.isRegistered }

                sb.append("━━━ 📶 Serving Cell (${registered.size}) ━━━\n")
                registered.forEachIndexed { idx, c ->
                    sb.append(formatCell(c, idx + 1))
                }

                if (neighbors.isNotEmpty()) {
                    sb.append("\n━━━ 🔄 Neighbor Cells (${neighbors.size}) ━━━\n")
                    neighbors.take(5).forEachIndexed { idx, c ->
                        sb.append(formatCell(c, idx + 1))
                    }
                }
            }
        } catch (e: SecurityException) {
            sb.append("❌ 권한 오류: ${e.message}\n")
        } catch (e: Exception) {
            sb.append("❌ 오류: ${e.message}\n")
        }

        tvInfo.text = sb.toString()

        if (logging) {
            logBuffer.add(buildCsvRow())
        }
    }

    private fun formatCell(cell: CellInfo, idx: Int): String {
        val sb = StringBuilder()
        sb.append("\n[$idx] ")
        when (cell) {
            is CellInfoNr -> {
                sb.append("🚀 5G NR\n")
                val id = cell.cellIdentity as CellIdentityNr
                val ss = cell.cellSignalStrength as CellSignalStrengthNr
                sb.append("  NCI (gNB+Cell): ${id.nci}\n")
                sb.append("  PCI: ${id.pci}\n")
                sb.append("  TAC: ${id.tac}\n")
                sb.append("  NR-ARFCN: ${id.nrarfcn}\n")
                sb.append("  MCC: ${id.mccString}\n")
                sb.append("  MNC: ${id.mncString}\n")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sb.append("  Bands: ${id.bands.joinToString()}\n")
                }
                sb.append("  SS-RSRP: ${ss.ssRsrp} dBm\n")
                sb.append("  SS-RSRQ: ${ss.ssRsrq} dB\n")
                sb.append("  SS-SINR: ${ss.ssSinr} dB\n")
                sb.append("  CSI-RSRP: ${ss.csiRsrp} dBm\n")
                sb.append("  CSI-RSRQ: ${ss.csiRsrq} dB\n")
                sb.append("  CSI-SINR: ${ss.csiSinr} dB\n")
                sb.append("  Level: ${ss.level}/4\n")
            }
            is CellInfoLte -> {
                sb.append("📶 LTE\n")
                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength
                sb.append("  CI (eNB+Cell): ${id.ci}\n")
                if (id.ci != Int.MAX_VALUE) {
                    sb.append("  eNB ID: ${id.ci shr 8}\n")
                    sb.append("  Cell ID: ${id.ci and 0xFF}\n")
                }
                sb.append("  PCI: ${id.pci}\n")
                sb.append("  TAC: ${id.tac}\n")
                sb.append("  EARFCN: ${id.earfcn}\n")
                sb.append("  Bandwidth: ${id.bandwidth} kHz\n")
                sb.append("  MCC: ${id.mccString}\n")
                sb.append("  MNC: ${id.mncString}\n")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    sb.append("  Bands: ${id.bands.joinToString()}\n")
                }
                sb.append("  RSRP: ${ss.rsrp} dBm\n")
                sb.append("  RSRQ: ${ss.rsrq} dB\n")
                sb.append("  RSSI: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ss.rssi else "N/A"} dBm\n")
                sb.append("  RSSNR: ${ss.rssnr} dB\n")
                sb.append("  CQI: ${ss.cqi}\n")
                sb.append("  TA (Timing Advance): ${ss.timingAdvance}\n")
                sb.append("  Level: ${ss.level}/4\n")
            }
            is CellInfoWcdma -> {
                sb.append("📡 3G WCDMA\n")
                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength
                sb.append("  CID: ${id.cid}\n")
                sb.append("  LAC: ${id.lac}\n")
                sb.append("  PSC: ${id.psc}\n")
                sb.append("  UARFCN: ${id.uarfcn}\n")
                sb.append("  MCC: ${id.mccString}\n")
                sb.append("  MNC: ${id.mncString}\n")
                sb.append("  RSCP: ${ss.dbm} dBm\n")
                sb.append("  Level: ${ss.level}/4\n")
            }
            is CellInfoGsm -> {
                sb.append("📞 2G GSM\n")
                val id = cell.cellIdentity
                val ss = cell.cellSignalStrength
                sb.append("  CID: ${id.cid}\n")
                sb.append("  LAC: ${id.lac}\n")
                sb.append("  ARFCN: ${id.arfcn}\n")
                sb.append("  BSIC: ${id.bsic}\n")
                sb.append("  MCC: ${id.mccString}\n")
                sb.append("  MNC: ${id.mncString}\n")
                sb.append("  RSSI: ${ss.dbm} dBm\n")
                sb.append("  Level: ${ss.level}/4\n")
            }
            is CellInfoCdma -> {
                sb.append("📻 CDMA\n")
                val ss = cell.cellSignalStrength
                sb.append("  CDMA dBm: ${ss.cdmaDbm}\n")
                sb.append("  EVDO dBm: ${ss.evdoDbm}\n")
            }
            else -> sb.append("❓ Unknown cell type: ${cell.javaClass.simpleName}\n")
        }
        return sb.toString()
    }

    private fun buildCsvRow(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        try {
            val cells = tm.allCellInfo?.filter { it.isRegistered } ?: emptyList()
            for (c in cells) {
                when (c) {
                    is CellInfoNr -> {
                        val id = c.cellIdentity as CellIdentityNr
                        val ss = c.cellSignalStrength as CellSignalStrengthNr
                        sb.append("$ts,NR,${id.nci},${id.pci},${id.tac},${id.nrarfcn},${id.mccString},${id.mncString},${ss.ssRsrp},${ss.ssRsrq},${ss.ssSinr}\n")
                    }
                    is CellInfoLte -> {
                        val id = c.cellIdentity
                        val ss = c.cellSignalStrength
                        sb.append("$ts,LTE,${id.ci},${id.pci},${id.tac},${id.earfcn},${id.mccString},${id.mncString},${ss.rsrp},${ss.rsrq},${ss.rssnr}\n")
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) { /* ignore */ }
        return sb.toString()
    }

    private fun toggleLogging() {
        logging = !logging
        if (logging) {
            logBuffer.clear()
            logBuffer.add("timestamp,tech,cell_id,pci,tac,arfcn,mcc,mnc,rsrp,rsrq,sinr\n")
            btnToggleLog.text = "⏹ 로그 중지"
            Toast.makeText(this, "로그 기록 시작", Toast.LENGTH_SHORT).show()
        } else {
            btnToggleLog.text = "⏺ 로그 시작"
            Toast.makeText(this, "로그 ${logBuffer.size - 1}건 기록됨", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportCsv() {
        if (logBuffer.size <= 1) {
            Toast.makeText(this, "내보낼 로그가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val fileName = "cellinfo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val file = File(getExternalFilesDir(null), fileName)
            FileWriter(file).use { writer ->
                logBuffer.forEach { writer.write(it) }
            }
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "CSV 공유"))
        } catch (e: Exception) {
            Toast.makeText(this, "내보내기 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getNetworkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Unknown"
        else -> "Type $type"
    }

    private fun getPhoneTypeName(type: Int): String = when (type) {
        TelephonyManager.PHONE_TYPE_GSM -> "GSM"
        TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
        TelephonyManager.PHONE_TYPE_SIP -> "SIP"
        TelephonyManager.PHONE_TYPE_NONE -> "None"
        else -> "Unknown"
    }
}
