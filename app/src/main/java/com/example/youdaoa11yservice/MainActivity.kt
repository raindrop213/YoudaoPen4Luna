package com.example.youdaoa11yservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvContent: TextView
    private val textReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == YoudaoA11yService.ACTION_UPDATE_TEXT) {
                val payload = intent.getStringExtra("payload") ?: return
                tvContent.text = payload
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvContent = findViewById(R.id.tv_content)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(textReceiver, IntentFilter(YoudaoA11yService.ACTION_UPDATE_TEXT))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(textReceiver)
    }
}
