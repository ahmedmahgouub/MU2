package com.muhgoub.hud

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DURATION_MS = 3000L
    private val handler = Handler(Looper.getMainLooper())

    private val goToMain = Runnable {
        if (!isFinishing) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        handler.postDelayed(goToMain, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(goToMain)
        super.onDestroy()
    }
}
