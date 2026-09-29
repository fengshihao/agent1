package com.agent1.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.agent1.android.productivity.ui.view.ProductivityNavHost
import com.agent1.android.productivity.ui.view.ProductivityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProductivityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ProductivityNavHost()
                }
            }
        }
    }
}
