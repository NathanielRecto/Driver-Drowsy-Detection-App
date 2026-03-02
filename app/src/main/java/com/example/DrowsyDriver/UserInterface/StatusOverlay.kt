package com.example.DrowsyDriver.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ── Shared UI state ────────────────────────────────────────────────────────────
// headTiltDeg / ear / mar  → filled by FaceExtractionEngine (feature extraction)
// eyeProb / yawnProb       → filled by the ML inference loop (model predictions)
// status                   → "Normal" or "Drowsy", set by the ML inference loop
data class ExtractionUiState(
    val headTiltDeg: Float = 0f,
    val ear:         Float = 0f,   // Eye Aspect Ratio from landmark geometry
    val mar:         Float = 0f,   // Mouth Aspect Ratio from landmark geometry
    val eyeProb:     Float = 0f,   // Model P(eyes closed),  range [0, 1]
    val yawnProb:    Float = 0f,   // Model P(yawning),      range [0, 1]
    val status:      String = "Normal"
)

// ── Status panel ───────────────────────────────────────────────────────────────
@Composable
fun StatusPanel(
    state:    ExtractionUiState,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    val isDrowsy = state.status.equals("Drowsy", ignoreCase = true)

    // The outer container always renders with the same padding so the STATUS
    // bubble never shifts. Only the background colour toggles.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (expanded) Color.Black.copy(alpha = 0.38f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ── Metrics rows — hidden when collapsed ───────────────────────────────
        if (expanded) {

            // Row 1: Head Tilt  |  EAR
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text  = "Head Tilt: ${"%.1f".format(state.headTiltDeg)}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text  = "EAR: ${"%.2f".format(state.ear)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.ear < 0.10f) Color(0xFFFF6B6B) else Color.White
                )
            }

            Spacer(Modifier.height(6.dp))

            // Row 2: Eyes (model)  |  MAR
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text  = "Eyes: ${"%.0f".format(state.eyeProb * 100)}% closed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.eyeProb > 0.85f) Color(0xFFFF6B6B) else Color.White
                )
                Text(
                    text  = "MAR: ${"%.2f".format(state.mar)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.mar > 0.50f) Color(0xFFFF6B6B) else Color.White
                )
            }

            Spacer(Modifier.height(6.dp))

            // Row 3: Mouth (model)
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text  = "Mouth: ${"%.0f".format(state.yawnProb * 100)}% yawn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.yawnProb > 0.85f) Color(0xFFFF6B6B) else Color.White
                )
                Spacer(Modifier.width(1.dp))
            }

            Spacer(Modifier.height(12.dp))
        }

        // ── STATUS bubble — always in the same position ────────────────────────
        Box(
            modifier         = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .border(
                        width = if (isDrowsy) 2.dp else 0.dp,
                        color = if (isDrowsy) Color.Red.copy(alpha = 0.95f) else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .background(
                        color = if (isDrowsy) Color.Red.copy(alpha = 0.45f)
                        else          Color.DarkGray.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text  = "STATUS: ${state.status}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}