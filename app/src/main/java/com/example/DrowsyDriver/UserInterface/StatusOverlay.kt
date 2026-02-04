package com.example.DrowsyDriver.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border

data class ExtractionUiState(
    val headTiltDeg: Float = 0f,
    val yawnDeg: Float = 0f,
    val eyesClosedSec: Float = 0f,
    val mar: Float = 0f,
    val ear: Float = 0f,
    val status: String = "Normal"
)

@Composable
fun StatusPanel(
    state: ExtractionUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Panel background (translucent)
            .background(
                color = Color.Black.copy(alpha = 0.38f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {

        // Row 1: Head Tilt (left) + MAR (right)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Head Tilt: ${state.headTiltDeg}°",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = "MAR: ${state.mar}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }

        Spacer(Modifier.height(6.dp))

        // Row 2: Yawn (left)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Yawn: ${state.yawnDeg}°",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(1.dp))
        }

        Spacer(Modifier.height(6.dp))

        // Row 3: Eyes Closed (left) + EAR (right)   same line
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Eyes Closed: ${state.eyesClosedSec} sec",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = "EAR: ${state.ear}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }

        Spacer(Modifier.height(12.dp))

        // STATUS centred with its own bubble
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val isDrowsy = state.status.equals("Drowsy", ignoreCase = true)

            val bubbleBg =
                if (isDrowsy) Color.Red.copy(alpha = 0.45f)
                else Color.DarkGray.copy(alpha = 0.45f)

            val bubbleBorder =
                if (isDrowsy) Color.Red.copy(alpha = 0.95f)
                else Color.Transparent

            Box(
                modifier = Modifier
                    .border(
                        width = if (isDrowsy) 2.dp else 0.dp,
                        color = bubbleBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .background(
                        color = bubbleBg,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "STATUS: ${state.status}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

        }
    }
}
