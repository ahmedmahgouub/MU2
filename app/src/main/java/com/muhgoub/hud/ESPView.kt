package com.muhgoub.hud

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.*

class ESPView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isRunning = false
    private var scope: CoroutineScope? = null
    private var currentPid = -1
    private val supportedPackages = arrayOf(
        "com.tencent.ig",
        "com.vng.pubgmobile",
        "com.pubg.krmobile",
        "com.rekoo.pubg"
    )
    
    private var viewMatrix = FloatArray(16)
    private val playerList = mutableListOf<MemoryUtils.Vector3>()
    private var statusMessage = "WAITING FOR PUBG..."
    
    // متغيرات لتثبيت وتخزين المؤشرات مؤقتاً (Caching) لضمان الاستقرار وعدم الرفرفة
    private var cachedGWorld: Long = 0L

    private val textPaint = Paint().apply {
        color = android.graphics.Color.GREEN
        textSize = 36f
        isAntiAlias = true
    }

    private val enemyBoxPaint = Paint().apply {
        color = android.graphics.Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }

    private fun startLoop() {
        if (isRunning) return
        isRunning = true
        scope = CoroutineScope(Dispatchers.Default + Job())
        scope?.launch {
            while (isRunning) {
                currentPid = -1
                for (pkg in supportedPackages) {
                    val pid = MemoryUtils.findProcessId(pkg)
                    if (pid != -1) {
                        currentPid = pid
                        break
                    }
                }
                
                if (currentPid != -1) {
                    val libBase = MemoryUtils.getModuleBase(currentPid, "libUE4.so")
                    
                    if (libBase != 0L) {
                        // قراءة مصفوفة الإسقاط باستخدام الأوفسيت المستخرج من التيرماكس
                        val tempMatrix = MemoryUtils.readMatrix(currentPid, libBase + MemoryUtils.OFFSET_PROJECTION_MATRIX)
                        if (tempMatrix[0] != 0f) {
                            viewMatrix = tempMatrix
                        }
                        
                        // قراءة UWorld مع التحقق من صحته وتثبيته لمنع تذبذب الـ true/false
                        val gWorldPtr = MemoryUtils.readLong(currentPid, libBase + MemoryUtils.OFFSET_UWORLD)
                        if (gWorldPtr != 0L && gWorldPtr > 0x10000000L) {
                            cachedGWorld = gWorldPtr
                        }
                        
                        var count = 0
                        val tempPlayers = mutableListOf<MemoryUtils.Vector3>()

                        if (cachedGWorld != 0L && cachedGWorld > 0x10000000L) {
                            // قراءة PersistentLevel باستخدام الأوفسيت المستخرج من التيرماكس
                            val persistentLevel = MemoryUtils.readLong(currentPid, cachedGWorld + MemoryUtils.OFFSET_PERSISTENT_LEVEL)
                            if (persistentLevel != 0L && persistentLevel > 0x10000000L) {
                                val actorsPtr = MemoryUtils.readLong(currentPid, persistentLevel + 0x98L)
                                val actorsCount = MemoryUtils.readLong(currentPid, persistentLevel + 0xA0L).toInt()
                                
                                if (actorsPtr != 0L && actorsCount in 1..10000) {
                                    val maxCount = minOf(actorsCount, 800)
                                    for (i in 0 until maxCount) {
                                        val actor = MemoryUtils.readLong(currentPid, actorsPtr + (i * 8L))
                                        if (actor != 0L && actor > 0x10000000L) {
                                            val rootComponent = MemoryUtils.readLong(currentPid, actor + 0x208L)
                                            if (rootComponent != 0L && rootComponent > 0x10000000L) {
                                                val x = MemoryUtils.readFloat(currentPid, rootComponent + 0x1E4L)
                                                val y = MemoryUtils.readFloat(currentPid, rootComponent + 0x1E8L)
                                                val z = MemoryUtils.readFloat(currentPid, rootComponent + 0x1ECL)
                                                
                                                if (x != 0f && y != 0f) {
                                                    tempPlayers.add(MemoryUtils.Vector3(x, y, z))
                                                    count++
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        synchronized(playerList) {
                            playerList.clear()
                            playerList.addAll(tempPlayers)
                        }
                        
                        val isGWValid = (cachedGWorld != 0L && cachedGWorld > 0x10000000L)
                        statusMessage = "PID: $currentPid | GW: $isGWValid | Players: $count"
                    } else {
                        statusMessage = "PID: $currentPid | WAITING FOR LIB..."
                    }
                } else {
                    statusMessage = "WAITING FOR PUBG..."
                }

                withContext(Dispatchers.Main) {
                    invalidate()
                }
                delay(20L)
            }
        }
    }

    private fun stopLoop() {
        isRunning = false
        scope?.cancel()
        scope = null
        cachedGWorld = 0L
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawText(statusMessage, 30f, 120f, textPaint)

        synchronized(playerList) {
            for (player in playerList) {
                val pt = MemoryUtils.worldToScreen(player, viewMatrix, width, height)
                if (pt.isValid) {
                    val l = pt.x - 40f
                    val t = pt.y - 100f
                    val r = pt.x + 40f
                    val b = pt.y + 100f
                    canvas.drawRect(l, t, r, b, enemyBoxPaint)
                    canvas.drawText("Player", l, t - 8f, textPaint)
                }
            }
        }
    }
}
