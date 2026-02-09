package com.locqar.locker.ui.screens.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locqar.locker.data.db.entity.*
import com.locqar.locker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onTechTool: () -> Unit,
    onCommissioning: () -> Unit,
    onBack: () -> Unit
) {
    val doors by viewModel.doors.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val incidents by viewModel.incidents.collectAsState()
    val openDoorBanner by viewModel.openDoorBanner.collectAsState()
    val openDoorFullscreen by viewModel.openDoorFullscreen.collectAsState()

    var selectedDoor by remember { mutableStateOf<DoorUiState?>(null) }
    var showIncidents by remember { mutableStateOf(false) }

    // Fullscreen door-left-open warning
    if (openDoorFullscreen != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFullscreen() },
            title = { Text("WARNING", color = LocQarRed, fontWeight = FontWeight.Bold) },
            text = { Text(openDoorFullscreen!!, fontSize = 18.sp) },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissFullscreen() },
                    colors = ButtonDefaults.buttonColors(containerColor = LocQarRed)
                ) {
                    Text("Acknowledge")
                }
            },
            containerColor = Color(0xFFFFEBEE)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF212121),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                actions = {
                    Icon(
                        Icons.Default.Circle, null,
                        tint = if (isOnline) DoorClosedColor else DoorOpenColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isOnline) "ONLINE" else "OFFLINE",
                        color = if (isOnline) DoorClosedColor else DoorOpenColor,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.pollNow() }) {
                        Icon(Icons.Default.Refresh, "Poll Now", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Open door banner
            openDoorBanner?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = LocQarOrange.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = LocQarOrange)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = LocQarOrange, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.dismissBanner() }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Status message
            if (statusMessage.isNotBlank()) {
                Text(
                    statusMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                    color = LocQarBlue
                )
            }

            // Doors Grid 3x4
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0 until 3) {
                                val idx = row * 3 + col
                                if (idx < doors.size) {
                                    AdminDoorTile(
                                        door = doors[idx],
                                        onClick = { selectedDoor = doors[idx] },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else if (idx < 12) {
                                    // Empty placeholder
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Bottom action bar
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { viewModel.pollNow() }) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Poll")
                }
                OutlinedButton(onClick = { viewModel.exportLogs() }) {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Logs")
                }
                OutlinedButton(onClick = { showIncidents = true }) {
                    Icon(Icons.Default.Warning, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Incidents (${incidents.size})")
                }
                OutlinedButton(onClick = onTechTool) {
                    Icon(Icons.Default.Build, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tech Tool")
                }
                OutlinedButton(onClick = onCommissioning) {
                    Icon(Icons.Default.Settings, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Commissioning")
                }
            }
        }
    }

    // Door detail dialog
    selectedDoor?.let { door ->
        DoorDetailDialog(
            door = door,
            onDismiss = { selectedDoor = null },
            onOpen = { viewModel.openDoor(door.id) },
            onToggleEnabled = { viewModel.toggleDoorEnabled(door.id) },
            onClearReservation = { viewModel.clearReservation(door.id) },
            onForceAvailable = { viewModel.forceSetAvailable(door.id) }
        )
    }

    // Incidents dialog
    if (showIncidents) {
        IncidentsDialog(
            incidents = incidents,
            onDismiss = { showIncidents = false },
            onResolve = { viewModel.resolveIncident(it) }
        )
    }
}

@Composable
private fun AdminDoorTile(
    door: DoorUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        !door.enabled -> DoorDisabledColor.copy(alpha = 0.2f)
        door.physicalState == PhysicalState.OPEN -> DoorOpenColor.copy(alpha = 0.15f)
        door.logicalState == LogicalState.OCCUPIED -> DoorOccupiedColor.copy(alpha = 0.15f)
        door.logicalState == LogicalState.RESERVED -> DoorReservedColor.copy(alpha = 0.15f)
        door.physicalState == PhysicalState.CLOSED -> DoorClosedColor.copy(alpha = 0.1f)
        else -> DoorUnknownColor.copy(alpha = 0.1f)
    }

    val borderColor = when {
        !door.enabled -> DoorDisabledColor
        door.physicalState == PhysicalState.OPEN -> DoorOpenColor
        door.logicalState == LogicalState.OCCUPIED -> DoorOccupiedColor
        door.logicalState == LogicalState.RESERVED -> DoorReservedColor
        door.physicalState == PhysicalState.CLOSED -> DoorClosedColor
        else -> DoorUnknownColor
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Door label
            Text(
                door.doorLabel,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            // Physical state
            Text(
                door.physicalState,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = borderColor
            )

            // Logical state
            Text(
                when (door.logicalState) {
                    LogicalState.AVAILABLE -> "AVAIL"
                    LogicalState.RESERVED -> "RSVD"
                    LogicalState.OCCUPIED -> "OCCUP"
                    LogicalState.OUT_OF_SERVICE -> "OOS"
                    else -> door.logicalState
                },
                fontSize = 9.sp,
                color = Color.Gray
            )

            // Open duration warning
            door.openSinceMs?.let { ms ->
                if (ms > 5000) {
                    Text(
                        "${ms / 1000}s",
                        fontSize = 10.sp,
                        color = LocQarRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Disabled indicator
            if (!door.enabled) {
                Text("DISABLED", fontSize = 8.sp, color = LocQarRed)
            }
        }
    }
}

@Composable
private fun DoorDetailDialog(
    door: DoorUiState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleEnabled: () -> Unit,
    onClearReservation: () -> Unit,
    onForceAvailable: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Door ${door.doorLabel} (Lock ${door.lockNumber})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Physical: ${door.physicalState}", fontWeight = FontWeight.Bold)
                Text("Logical: ${door.logicalState}")
                Text("Enabled: ${door.enabled}")
                if (door.lastUsedAt > 0) {
                    Text("Last used: ${dateFormat.format(Date(door.lastUsedAt))}")
                }
                door.lastError?.let {
                    Text("Last error: $it", color = LocQarRed, fontSize = 12.sp)
                    if (door.lastErrorAt > 0) {
                        Text("Error at: ${dateFormat.format(Date(door.lastErrorAt))}", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { onOpen(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = LocQarGreen)) {
                        Text("Open", fontSize = 12.sp)
                    }
                    Button(onClick = { onToggleEnabled(); onDismiss() }) {
                        Text(if (door.enabled) "Disable" else "Enable", fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { onClearReservation(); onDismiss() }) {
                        Text("Clear Rsv", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = { onForceAvailable(); onDismiss() }) {
                        Text("Force Avail", fontSize = 11.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun IncidentsDialog(
    incidents: List<IncidentEntity>,
    onDismiss: () -> Unit,
    onResolve: (Long) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unresolved Incidents (${incidents.size})") },
        text = {
            if (incidents.isEmpty()) {
                Text("No unresolved incidents.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(incidents) { incident ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = LocQarRed.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(incident.type, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(incident.message, fontSize = 12.sp)
                                Text(dateFormat.format(Date(incident.timestamp)), fontSize = 10.sp, color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = { onResolve(incident.id) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Resolve", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
