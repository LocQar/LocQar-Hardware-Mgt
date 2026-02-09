package com.locqar.locker.ui.screens.techtool

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locqar.locker.hardware.codec.WinnsenCodec
import com.locqar.locker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechToolScreen(
    viewModel: TechToolViewModel,
    onBack: () -> Unit
) {
    val usbConnected by viewModel.usbConnected.collectAsState()
    val stationNumber by viewModel.stationNumber.collectAsState()
    val doorStates by viewModel.doorStates.collectAsState()
    val stationOnline by viewModel.stationOnline.collectAsState()
    val livePolling by viewModel.livePolling.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val testRunning by viewModel.testRunning.collectAsState()
    val lastPollHex by viewModel.lastPollHex.collectAsState()

    var stationInput by remember { mutableStateOf(stationNumber.toString()) }
    var showTestAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tech Tool", fontWeight = FontWeight.Bold) },
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
                    // Connection indicator
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = null,
                        tint = if (stationOnline) DoorClosedColor else DoorOpenColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (stationOnline) "ONLINE" else "OFFLINE",
                        color = if (stationOnline) DoorClosedColor else DoorOpenColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(16.dp))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status bar
            if (statusMessage.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                ) {
                    Text(
                        statusMessage,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            }

            // Connection Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("USB Serial Connection", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.connectUsb() },
                            enabled = !usbConnected,
                            colors = ButtonDefaults.buttonColors(containerColor = LocQarGreen)
                        ) {
                            Icon(Icons.Default.UsbOff, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Connect")
                        }
                        OutlinedButton(
                            onClick = { viewModel.disconnectUsb() },
                            enabled = usbConnected
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            // Station Config
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Station", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = stationInput,
                            onValueChange = {
                                stationInput = it
                                it.toIntOrNull()?.let { n -> viewModel.setStationNumber(n) }
                            },
                            label = { Text("Station #") },
                            modifier = Modifier.width(120.dp),
                            singleLine = true
                        )
                        Button(onClick = { viewModel.pollNow() }) {
                            Text("Poll Now")
                        }
                        Button(
                            onClick = { viewModel.toggleLivePoll() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (livePolling) LocQarOrange else LocQarBlue
                            )
                        ) {
                            Text(if (livePolling) "Stop Live" else "Live Poll 2s")
                        }
                    }
                    if (lastPollHex.isNotBlank()) {
                        Text(
                            lastPollHex,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Door States Grid
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Door States (Locks 1-12)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))

                    // 3x4 grid
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0 until 3) {
                                val lock = row * 3 + col + 1
                                if (lock <= WinnsenCodec.MAX_DOORS) {
                                    val isOpen = doorStates[lock]
                                    LockTile(
                                        lockNumber = lock,
                                        isOpen = isOpen,
                                        onOpen = { viewModel.openLock(lock) },
                                        onSafeOpen = { viewModel.safeOpenLock(lock) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Test All Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Test All Locks", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showTestAllDialog = true },
                            enabled = !testRunning
                        ) {
                            Text(if (testRunning) "Testing..." else "Test All 1-12")
                        }
                    }

                    // Test results
                    if (testResults.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        testResults.forEach { result ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Lock ${result.lockNumber}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                                Text(
                                    buildString {
                                        if (result.openSuccess) append("OK") else append("FAIL")
                                        if (result.openConfirmed) append(" [Confirmed]")
                                        if (result.closedAfterTest) append(" [Closed]")
                                        result.errorMessage?.let { append(" $it") }
                                    },
                                    color = if (result.openSuccess) DoorClosedColor else DoorOpenColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Export Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Export", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val uri = viewModel.exportTestReport()
                                // Status updated in VM
                            },
                            enabled = testResults.isNotEmpty()
                        ) {
                            Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test Report JSON")
                        }
                        OutlinedButton(onClick = { viewModel.exportLogs() }) {
                            Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Logs CSV")
                        }
                    }
                }
            }
        }
    }

    // Test All Dialog
    if (showTestAllDialog) {
        var confirmOpen by remember { mutableStateOf(true) }
        var waitClose by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTestAllDialog = false },
            title = { Text("Test All Locks") },
            text = {
                Column {
                    Text("This will open locks 1-12 sequentially.")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(confirmOpen, { confirmOpen = it })
                        Text("Confirm Open (poll to verify)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(waitClose, { waitClose = it })
                        Text("Wait for Close after each")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showTestAllDialog = false
                    viewModel.testAllLocks(confirmOpen, waitClose)
                }) {
                    Text("Start Test")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LockTile(
    lockNumber: Int,
    isOpen: Boolean?,
    onOpen: () -> Unit,
    onSafeOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .aspectRatio(1.2f)
            .combinedClickable(
                onClick = { showMenu = true },
                onLongClick = onSafeOpen
            ),
        colors = CardDefaults.cardColors(
            containerColor = when (isOpen) {
                true -> DoorOpenColor.copy(alpha = 0.15f)
                false -> DoorClosedColor.copy(alpha = 0.15f)
                null -> DoorUnknownColor.copy(alpha = 0.1f)
            }
        ),
        border = BorderStroke(
            2.dp,
            when (isOpen) {
                true -> DoorOpenColor
                false -> DoorClosedColor
                null -> DoorUnknownColor
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "$lockNumber",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                when (isOpen) {
                    true -> "OPEN"
                    false -> "CLOSED"
                    null -> "?"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when (isOpen) {
                    true -> DoorOpenColor
                    false -> DoorClosedColor
                    null -> DoorUnknownColor
                }
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Open") },
                onClick = { showMenu = false; onOpen() }
            )
            DropdownMenuItem(
                text = { Text("Safe Open") },
                onClick = { showMenu = false; onSafeOpen() }
            )
        }
    }
}
