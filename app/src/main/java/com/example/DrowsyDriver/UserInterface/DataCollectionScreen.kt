package com.example.DrowsyDriver.UserInterface

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.DrowsyDriver.session.SessionManager

@Composable
fun DataCollectionScreen(
    onBack: () -> Unit
) {
    val sessionDuration = SessionManager.getSessionDurationMinutes()
    val eyeTime         = SessionManager.totalEyeClosedTime
    val tiltTime        = SessionManager.totalHeadTiltTime
    val yawns           = SessionManager.yawnCount
    val drowsyEvents    = SessionManager.drowsyEvents
    val frames          = SessionManager.framesProcessed
    val events          = SessionManager.events

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column {

                // ── Session analytics card ─────────────────────────────────────
                Card(
                    shape    = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {

                        Text(
                            "Session Analytics",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(12.dp))

                        Text("Session Duration: $sessionDuration min")
                        Text("Frames Processed: $frames")
                        Text("Total Eye Closed Time: ${"%.2f".format(eyeTime)} sec")
                        Text("Total Head Tilt Time: ${"%.2f".format(tiltTime)} sec")
                        Text("Yawns Detected: $yawns")
                        Text("Drowsy Events: $drowsyEvents")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Event log card ─────────────────────────────────────────────
                Card(
                    shape    = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {

                        Text(
                            "Event Log",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(Modifier.height(8.dp))

                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(events.takeLast(10).reversed()) { event ->
                                Text("• $event")
                            }
                        }
                    }
                }
            }

            // ── Buttons ────────────────────────────────────────────────────────
            Column {
                Button(
                    onClick  = { SessionManager.resetSession() },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp)
                ) {
                    Text("Reset Session")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick  = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp)
                ) {
                    Text("Back to Camera")
                }
            }
        }
    }
}