package com.muhgoub.hud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlin.math.abs

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "muhgoub_hud_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.muhgoub.hud.action.STOP"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var panelView: View? = null
    private var espView: ESPView? = null // طبقة الرسم الجديدة الخاصة بالكاشف
    
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val toggleButtons = arrayOfNulls<Button>(12)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PrefsManager(this)

        startForeground(NOTIFICATION_ID, buildNotification())

        addBubbleView()
        addPanelView()
        addEspView() // إضافة طبقة الكاشف للشاشة

        applyCoreAlign(prefs.getCoreAlign())
        setPanelVisible(prefs.isOverlayExpanded())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        espView?.let { runCatching { windowManager.removeView(it) } } // إزالة طبقة الكاشف عند الإيقاف
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.panel_running))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(0, getString(R.string.close_panel), stopPendingIntent)
            .build()
    }

    private fun addBubbleView() {
        val view = View.inflate(this, R.layout.overlay_bubble, null)
        val (savedX, savedY) = prefs.getOverlayPosition()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        view.setOnTouchListener(DragTapListener(params) { tapped ->
            if (tapped) togglePanelVisibility()
        })

        windowManager.addView(view, params)
        bubbleView = view
        bubbleParams = params
    }

    // دالة إنشاء وإضافة طبقة الكاشف (ESPView) الشفافة فوق اللعبة
    private fun addEspView() {
        espView = ESPView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(espView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun togglePanelVisibility() {
        setPanelVisible(panelView?.visibility != View.VISIBLE)
    }

    private fun setPanelVisible(visible: Boolean) {
        prefs.setOverlayExpanded(visible)
        panelView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun addPanelView() {
        val view = View.inflate(this, R.layout.overlay_panel, null)

        val panelWidthPx = cmToPx(6f)
        val panelHeightPx = cmToPx(6f)

        val params = WindowManager.LayoutParams(
            panelWidthPx,
            panelHeightPx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val (savedX, savedY) = prefs.getOverlayPosition()
            x = savedX
            y = savedY
        }

        val dragHandle = view.findViewById<View>(R.id.dragHandle)
        dragHandle.setOnTouchListener(DragTapListener(params) { })

        view.findViewById<ImageButton>(R.id.btnMinimize).setOnClickListener {
            setPanelVisible(false)
        }

        bindToolButtons(view)
        bindRadioGroups(view)
        applyStatusMode(view, prefs.getStatusMode())

        windowManager.addView(view, params)
        panelView = view
        panelParams = params
    }

    private fun bindToolButtons(root: View) {
        val ids = intArrayOf(
            R.id.btnTool0, R.id.btnTool1, R.id.btnTool2, R.id.btnTool3,
            R.id.btnTool4, R.id.btnTool5, R.id.btnTool6, R.id.btnTool7,
            R.id.btnTool8, R.id.btnTool9, R.id.btnTool10, R.id.btnTool11
        )

        for (i in ids.indices) {
            val button = root.findViewById<Button>(ids[i])
            toggleButtons[i] = button
            applyToggleStyle(button, prefs.isToolOn(i))

            button.setOnClickListener {
                val newState = !prefs.isToolOn(i)
                prefs.setToolOn(i, newState)
                applyToggleStyle(button, newState)
                performToolAction(i, newState)
            }
        }
    }

    private fun applyToggleStyle(button: Button, on: Boolean) {
        val checkIcon = if (on) R.drawable.ic_check_on else R.drawable.ic_check_off
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, checkIcon, 0)
        button.setBackgroundResource(if (on) R.drawable.bg_toggle_on else R.drawable.bg_toggle_off)
        val textColorRes = if (on) R.color.bg_dark else R.color.text_primary
        button.setTextColor(resources.getColor(textColorRes, theme))
    }

    private fun bindRadioGroups(root: View) {
        val boxButtons = mapOf(
            root.findViewById<android.widget.RadioButton>(R.id.rbBoxPrecise) to "precise",
            root.findViewById<android.widget.RadioButton>(R.id.rbBoxFilled) to "filled",
            root.findViewById<android.widget.RadioButton>(R.id.rbBoxOff) to "off"
        )
        fun refreshBoxChecks(selected: String) {
            boxButtons.forEach { (btn, value) -> btn?.isChecked = (value == selected) }
        }
        refreshBoxChecks(prefs.getCoreAlign())
        boxButtons.forEach { (btn, value) ->
            btn?.setOnClickListener {
                prefs.setCoreAlign(value)
                refreshBoxChecks(value)
                applyCoreAlign(value)
            }
        }

        val radarGroup = root.findViewById<RadioGroup>(R.id.radioRadarLine)
        val idToRadar = mapOf(
            R.id.rbRadarBottom to "bottom",
            R.id.rbRadarCenter to "center",
            R.id.rbRadarTop to "top",
            R.id.rbRadarOff to "off"
        )
        val radarToId = idToRadar.entries.associate { (k, v) -> v to k }
        radarGroup?.check(radarToId[prefs.getStatusMode()] ?: R.id.rbRadarCenter)
        radarGroup?.setOnCheckedChangeListener { _, checkedId ->
            val value = idToRadar[checkedId] ?: return@setOnCheckedChangeListener
            prefs.setStatusMode(value)
            applyStatusMode(root, value)
        }
    }

    private fun applyCoreAlign(value: String) {
        if (value == "off") {
            bubbleView?.visibility = View.GONE
            panelView?.visibility = View.GONE
            return
        }

        val gravity = when (value) {
            "filled" -> Gravity.CENTER_VERTICAL or Gravity.START
            "precise" -> Gravity.TOP or Gravity.START
            else -> Gravity.BOTTOM or Gravity.START
        }

        bubbleParams?.let {
            it.gravity = gravity
            bubbleView?.let { v -> runCatching { windowManager.updateViewLayout(v, it) } }
            bubbleView?.visibility = View.VISIBLE
        }
        panelParams?.let {
            it.gravity = gravity
            panelView?.let { v -> runCatching { windowManager.updateViewLayout(v, it) } }
        }
        setPanelVisible(prefs.isOverlayExpanded())
    }

    private fun applyStatusMode(root: View, value: String) {
        val dot = root.findViewById<View>(R.id.statusDot) ?: return
        when (value) {
            "bottom" -> { dot.visibility = View.VISIBLE; dot.setBackgroundResource(R.drawable.bg_icon_circle_gray) }
            "center", "top" -> { dot.visibility = View.VISIBLE; dot.setBackgroundResource(R.drawable.bg_icon_circle_accent) }
            "off" -> dot.visibility = View.GONE
        }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun cmToPx(cm: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, cm * 10f, resources.displayMetrics).toInt()

    private fun performToolAction(index: Int, on: Boolean) {
        try {
            when (index) {
                0 -> checkPing()
                1 -> showWifiSignal()
                2 -> openSystemScreen(dataUsageSettingsAction())
                3 -> showBandwidthUsage()
                4 -> showPacketStats()
                5 -> showCoreCount()
                6 -> toggleWifi()
                7 -> toggleBluetooth(on)
                8 -> openSystemScreen(Settings.ACTION_WIRELESS_SETTINGS)
                9 -> toggleFlashlight(on)
                10 -> toggleDarkMode(on)
                11 -> toast(if (on) "الـ HUD شغال دلوقتي" else "الـ HUD متوقف")
            }
        } catch (e: SecurityException) {
            toast(e.message ?: "Permission error")
        }
    }

    private fun checkPing() {
        toast("جارِ قياس الـ Ping...")
        thread {
            val start = System.currentTimeMillis()
            val reachable = runCatching { InetAddress.getByName("8.8.8.8").isReachable(1500) }.getOrDefault(false)
            val elapsed = System.currentTimeMillis() - start
            mainHandler.post {
                toast(if (reachable) "Ping: ${elapsed}ms" else "لا يوجد اتصال بالإنترنت")
            }
        }
    }

    private fun showWifiSignal() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val rssi = wifiManager.connectionInfo?.rssi
        if (rssi == null || rssi == -127) {
            toast("مش متصل بشبكة Wi-Fi")
        } else {
            val level = WifiManager.calculateSignalLevel(rssi, 5)
            toast("قوة الإشارة: $rssi dBm ($level/4)")
        }
    }

    private fun showBandwidthUsage() {
        val rx = TrafficStats.getTotalRxBytes() / (1024 * 1024)
        val tx = TrafficStats.getTotalTxBytes() / (1024 * 1024)
        toast("تحميل: ${rx}MB | رفع: ${tx}MB")
    }

    private fun showPacketStats() {
        val rxP = TrafficStats.getTotalRxPackets()
        val txP = TrafficStats.getTotalTxPackets()
        toast("الحزم: $rxP استقبال / $txP إرسال")
    }

    private fun showCoreCount() {
        toast("عدد أنوية المعالج: ${Runtime.getRuntime().availableProcessors()}")
    }

    private fun dataUsageSettingsAction(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.ACTION_DATA_USAGE_SETTINGS
        } else {
            Settings.ACTION_WIRELESS_SETTINGS
        }
    }

    private fun toggleWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openSystemScreen(Settings.ACTION_WIFI_SETTINGS)
        } else {
            @Suppress("DEPRECATION")
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = !wifiManager.isWifiEnabled
        }
    }

    private fun toggleBluetooth(on: Boolean) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { toast("Bluetooth not supported"); return }
        if (on) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(enableIntent)
        } else {
            toast(getString(R.string.bluetooth_open_settings))
            openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
    }

    private fun toggleFlashlight(on: Boolean) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cameraManager.setTorchMode(cameraId, on)
        } catch (e: CameraAccessException) {
            toast("Flashlight unavailable: ${e.message}")
        }
    }

    private fun toggleDarkMode(on: Boolean) {
        prefs.setPanelTheme(if (on) "dark" else "light")
        AppCompatDelegate.setDefaultNightMode(
            if (on) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun openSystemScreen(action: String) {
        val intent = Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        startActivity(intent)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private inner class DragTapListener(
        private val params: WindowManager.LayoutParams,
        private val onTap: (Boolean) -> Unit
    ) : View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > 5 || abs(dy) > 5) {
                        moved = true
                    }

                    val verticalSign = if ((params.gravity and Gravity.BOTTOM) == Gravity.BOTTOM) -1 else 1

                    params.x = initialX + dx
                    params.y = initialY + (dy * verticalSign)

                    bubbleParams?.let {
                        it.x = params.x
                        it.y = params.y
                        bubbleView?.let { bv -> runCatching { windowManager.updateViewLayout(bv, it) } }
                    }
                    panelParams?.let {
                        it.x = params.x
                        it.y = params.y
                        panelView?.let { pv -> runCatching { windowManager.updateViewLayout(pv, it) } }
                    }
                    return typeTrue()
                }
                MotionEvent.ACTION_UP -> {
                    prefs.setOverlayPosition(params.x, params.y)
                    onTap(!moved)
                    return typeTrue()
                }
            }
            return false
        }
        
        private fun typeTrue(): Boolean = true
    }
}
