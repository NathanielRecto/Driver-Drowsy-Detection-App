package com.example.DrowsyDriver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.DrowsyDriver.UserInterface.CameraScreen
import com.example.DrowsyDriver.ui.theme.FirstAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FirstAppTheme {
                CameraScreen()
            }
        }
    }
}
