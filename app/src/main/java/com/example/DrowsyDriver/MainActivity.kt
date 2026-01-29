package com.example.DrowsyDriver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.DrowsyDriver.UserInterface.CameraScreen
import com.example.DrowsyDriver.UserInterface.DataCollectionScreen
import com.example.DrowsyDriver.UserInterface.StartScreen
import com.example.DrowsyDriver.ui.theme.FirstAppTheme
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FirstAppTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "start"
                ){
                    composable("start"){
                        StartScreen(
                            onStartClicked = { navController.navigate("camera") }
                        )
                    }
                    composable("camera"){
                        CameraScreen(navController)
                    }
                    composable("data_collection"){
                        DataCollectionScreen {navController}
                    }

                }
//                var showCamera by remember { mutableStateOf(false) }
//
//                if (showCamera) {
//                    CameraScreen(navController)
//                } else {
//                    StartScreen(
//                        onStartClicked = { showCamera = true }
//                    )
//                }
            }
        }
    }
}
