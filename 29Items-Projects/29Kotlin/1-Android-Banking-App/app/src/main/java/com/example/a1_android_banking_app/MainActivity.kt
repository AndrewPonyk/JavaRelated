package com.example.a1_android_banking_app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.a1_android_banking_app.ui.BankApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw edge-to-edge on every API level (forced anyway at targetSdk 35+);
        // BankApp pads its content with safe-drawing insets.
        enableEdgeToEdge()
        setContent {
            BankApp()
        }
    }
}
