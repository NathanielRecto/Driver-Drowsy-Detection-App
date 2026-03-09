package com.example.DrowsyDriver.UserInterface

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.DrowsyDriver.session.SessionManager
import kotlinx.coroutines.delay

@Composable
fun DataCollectionScreen(
    onBack: () -> Unit
) {
    // ── Live state — refreshed every second so the screen updates in real time ──
    // Previously these were plain `val` read once at composition time, so
    // SessionManager changes (from the engine thread) were never reflected until
    // the screen was destroyed and recreated.
    var sessionDuration by remember { mutableLongStateOf(SessionManager.getSessionDurationMinutes()) }
    var eyeTime         by remember { mutableFloatStateOf(SessionManager.totalEyeClosedTime) }
    var tiltTime        by remember { mutableFloatStateOf(SessionManager.totalHeadTiltTime) }
    var yawns           by remember { mutableIntStateOf(SessionManager.yawnCount) }
    var drowsyEvents    by remember { mutableIntStateOf(SessionManager.drowsyEvents) }
    var frames          by remember { mutableIntStateOf(SessionManager.framesProcessed) }
    var events          by remember { mutableStateOf(SessionManager.events.toList()) }

    // Poll SessionManager every second so values stay current without
    // requiring SessionManager to be Compose-aware.
    LaunchedEffect(Unit) {
        while (true) {
            sessionDuration = SessionManager.getSessionDurationMinutes()
            eyeTime         = SessionManager.totalEyeClosedTime
            tiltTime        = SessionManager.totalHeadTiltTime
            yawns           = SessionManager.yawnCount
            drowsyEvents    = SessionManager.drowsyEvents
            frames          = SessionManager.framesProcessed
            events          = SessionManager.events.toList()
            delay(1_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    onClick = {
                        // Reset session data, then immediately refresh local
                        // state so the screen clears right away without waiting
                        // for the next poll tick.
                        SessionManager.resetSession()
                        sessionDuration = 0L
                        eyeTime         = 0f
                        tiltTime        = 0f
                        yawns           = 0
                        drowsyEvents    = 0
                        frames          = 0
                        events          = emptyList()
                    },
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