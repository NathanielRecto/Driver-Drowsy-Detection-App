package com.example.DrowsyDriver.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DataCollectionScreen(
    onBack: () -> Unit
) {
    // Fake session values for now
    val sessionDurationMin = 12
    val totalEyeClosedSec = 34f
    val yawnCount = 5
    val avgEAR = 0.71f
    val avgMAR = 0.23f
    val drowsyEvents = 3
    val sessionStatus = "MODERATE RISK"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.Black.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(16.dp)
                .systemBarsPadding()
        ) {

            Text(
                text = "Session Summary",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(Modifier.height(12.dp))

            Text("Session Duration: $sessionDurationMin min")
            Text("Total Eye Closure: $totalEyeClosedSec sec")
            Text("Yawns Detected: $yawnCount")
            Text("Average EAR: $avgEAR")
            Text("Average MAR: $avgMAR")
            Text("Drowsy Events: $drowsyEvents")

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.DarkGray.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "SESSION STATUS: $sessionStatus",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().systemBarsPadding(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Back to Camera")
        }
    }
}
