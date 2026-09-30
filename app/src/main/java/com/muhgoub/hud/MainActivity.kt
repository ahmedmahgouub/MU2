package com.muhgoub.hud

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var btnLaunchPanel: Button
    private lateinit var btnStopPanel: Button
    private lateinit var switchPermission: Switch
    private lateinit var btnModeNormal: Button
    private lateinit var btnModeTurbo: Button
    private lateinit var tvKernelDisplay: TextView
    private lateinit var prefs: PrefsManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissionUi()
            if (hasOverlayPermission()) {
                startOverlayServiceWithRoot()
            } else {
                Toast.makeText(this, "اخفاء الهاك عند تصوير الشاشه", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)

        btnLaunchPanel = findViewById(R.id.btnLaunchPanel)
        btnStopPanel = findViewById(R.id.btnStopPanel)
        switchPermission = findViewById(R.id.switchPermission)
        btnModeNormal = findViewById(R.id.btnModeNormal)
        btnModeTurbo = findViewById(R.id.btnModeTurbo)
        tvKernelDisplay = findViewById(R.id.tvKernelDisplay)

        btnLaunchPanel.setOnClickListener { onLaunchPanelClicked() }
        btnStopPanel.setOnClickListener { onStopPanelClicked() }
        
        // عند الضغط على زر Normal: تفعيل الوضع، إظهار رسالة تأكيد، وإعادة كلمة MUHGOUB بيضاء
        btnModeNormal.setOnClickListener { 
            setAppMode("normal")
            tvKernelDisplay.text = "MUHGOUB"
            tvKernelDisplay.setTextColor(Color.WHITE)
            Toast.makeText(this, "تم تفعيل الوضع Normal", Toast.LENGTH_SHORT).show()
        }
        
        // عند الضغط على زر Kernel: تفعيل الوضع وجلب الكيرنال الحقيقي وتلوينه بالبرتقالي الغامق
        btnModeTurbo.setOnClickListener { 
            setAppMode("turbo")
            requestRootAndFetchKernel()
        }

        applyModeUi(prefs.getAppMode())

        if (!hasOverlayPermission()) {
            requestOverlayPermission()
        }
        
        requestRootPrivileges(false)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionUi()
    }

    private fun onLaunchPanelClicked() {
        if (hasOverlayPermission()) {
            startOverlayServiceWithRoot()
        } else {
            requestOverlayPermission()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun refreshPermissionUi() {
        switchPermission.isChecked = hasOverlayPermission()
    }

    private fun requestRootPrivileges(showToast: Boolean) {
        Thread {
            var isRooted = false
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = process.outputStream
                os.write("id\n".toByteArray())
                os.flush()
                os.write("exit\n".toByteArray())
                os.flush()
                val exitCode = process.waitFor()
                isRooted = (exitCode == 0)
            } catch (e: Exception) {
                isRooted = false
            }

            if (showToast) {
                mainHandler.post {
                    if (isRooted) {
                        Toast.makeText(this, "تم منح صلاحيات الروت بنجاح ✅", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "فشل الحصول على صلاحيات الروت ❌", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    // جلب كيرنال الهاتف الحقيقي وتلوين الرقم فقط باللون البرتقالي الغامق (#FF8C00)
    private fun requestRootAndFetchKernel() {
        Thread {
            var kernelResult: String? = null
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "uname -r"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val line = reader.readLine()
                if (!line.isNullOrEmpty()) {
                    kernelResult = if (line.length >= 5) line.substring(0, 5) else line
                }
                process.waitFor()
            } catch (e: Exception) {
                kernelResult = null
            }

            mainHandler.post {
                if (!kernelResult.isNullOrEmpty()) {
                    tvKernelDisplay.text = kernelResult
                    tvKernelDisplay.setTextColor(Color.parseColor("#FF8C00")) // برتقالي غامق لرقم الكيرنال فقط
                    Toast.makeText(this, "تم تفعيل وضع Kernel وجلب الكيرنال بنجاح: $kernelResult", Toast.LENGTH_SHORT).show()
                } else {
                    tvKernelDisplay.text = "MUHGOUB"
                    tvKernelDisplay.setTextColor(Color.WHITE)
                    Toast.makeText(this, "يرجى منح صلاحيات الروت من تطبيق الإدارة أولاً ⚠️", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun startOverlayServiceWithRoot() {
        requestRootPrivileges(false)

        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "تم تشغيل الرادار", Toast.LENGTH_SHORT).show()
    }

    private fun onStopPanelClicked() {
        if (!isOverlayServiceRunning()) {
            Toast.makeText(this, "الرادار متوقف بالفعل", Toast.LENGTH_SHORT).show()
            return
        }
        btnStopPanel.setBackgroundResource(R.drawable.bg_stop_button_active)
        stopOverlayService()
        mainHandler.postDelayed({
            btnStopPanel.setBackgroundResource(R.drawable.bg_main_button)
        }, 2000)
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "تم إيقاف الرادار", Toast.LENGTH_SHORT).show()
    }

    private fun isOverlayServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Integer.MAX_VALUE).any {
            it.service.className == OverlayService::class.java.name
        }
    }

    private fun setAppMode(mode: String) {
        prefs.setAppMode(mode)
        applyModeUi(mode)
    }

    private fun applyModeUi(mode: String) {
        val isTurbo = mode == "turbo"
        btnModeNormal.setBackgroundResource(if (isTurbo) R.drawable.bg_segment_outline else R.drawable.bg_segment_filled)
        btnModeTurbo.setBackgroundResource(if (isTurbo) R.drawable.bg_segment_filled else R.drawable.bg_segment_outline)
    }
}
