package com.locqar.locker.ui.screens.commissioning

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locqar.locker.hardware.codec.WinnsenCodec
import com.locqar.locker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissioningScreen(
    viewModel: CommissioningViewModel,
    onFinished: () -> Unit,
    onBack: () -> Unit
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Commissioning Wizard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocQarBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Step indicator
            StepIndicator(currentStep)

            // Status
            if (statusMessage.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                ) {
                    Text(statusMessage, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }

            // Step content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (currentStep) {
                    WizardStep.STATION_SETUP -> StationSetupStep(viewModel)
                    WizardStep.DOOR_MAPPING -> DoorMappingStep(viewModel)
                    WizardStep.DOOR_VERIFICATION -> DoorVerificationStep(viewModel)
                    WizardStep.KIOSK_SETTINGS -> KioskSettingsStep(viewModel)
                    WizardStep.ADMIN_SECURITY -> AdminSecurityStep(viewModel)
                    WizardStep.SUMMARY -> SummaryStep(viewModel, onFinished)
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep != WizardStep.STATION_SETUP) {
                    OutlinedButton(onClick = { viewModel.previousStep() }) {
                        Icon(Icons.Default.ArrowBack, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (currentStep != WizardStep.SUMMARY) {
                    Button(onClick = {
                        if (currentStep == WizardStep.ADMIN_SECURITY) {
                            if (viewModel.validateAdminPassword()) {
                                viewModel.nextStep()
                            }
                        } else {
                            viewModel.nextStep()
                        }
                    }) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: WizardStep) {
    val steps = WizardStep.entries
    val labels = listOf("Station", "Mapping", "Verify", "Settings", "Security", "Summary")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        steps.forEachIndexed { idx, step ->
            val isActive = step == currentStep
            val isDone = steps.indexOf(currentStep) > idx

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            when {
                                isActive -> LocQarBlue
                                isDone -> LocQarGreen
                                else -> Color.LightGray
                            },
                            shape = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("${idx + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(labels[idx], fontSize = 10.sp, color = if (isActive) LocQarBlue else Color.Gray)
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun StationSetupStep(viewModel: CommissioningViewModel) {
    val stationNumber by viewModel.stationNumber.collectAsState()
    val stationOnline by viewModel.stationOnline.collectAsState()
    var stationInput by remember { mutableStateOf(stationNumber.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Station Setup", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Connect the USB-RS485 adapter and configure the station number.")

        OutlinedTextField(
            value = stationInput,
            onValueChange = {
                stationInput = it
                it.toIntOrNull()?.let { n -> viewModel.setStationNumber(n) }
            },
            label = { Text("Station Number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { viewModel.testPoll() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Poll")
        }

        if (stationOnline) {
            Card(colors = CardDefaults.cardColors(containerColor = DoorClosedColor.copy(alpha = 0.1f))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = DoorClosedColor)
                    Spacer(Modifier.width(8.dp))
                    Text("Station $stationNumber is online!", color = DoorClosedColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DoorMappingStep(viewModel: CommissioningViewModel) {
    val currentLock by viewModel.currentMappingLock.collectAsState()
    val mappings by viewModel.mappings.collectAsState()
    val assignedLabels by viewModel.assignedLabels.collectAsState()
    val waitingForClose by viewModel.waitingForDoorClose.collectAsState()
    val doorIsOpen by viewModel.doorIsOpen.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Door Mapping", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        if (currentLock <= WinnsenCodec.MAX_DOORS) {
            Text("Lock $currentLock of ${WinnsenCodec.MAX_DOORS}", fontSize = 16.sp)

            Button(
                onClick = { viewModel.safeOpenCurrentLock() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !waitingForClose
            ) {
                Text("Open Lock $currentLock")
            }

            if (doorIsOpen && !waitingForClose) {
                Text("Which door opened? Tap a label:", fontWeight = FontWeight.Bold)
                // Door label buttons (1-12)
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until 3) {
                            val label = (row * 3 + col + 1).toString()
                            val isUsed = label in assignedLabels
                            OutlinedButton(
                                onClick = { viewModel.assignLabel(label) },
                                enabled = !isUsed,
                                modifier = Modifier.weight(1f),
                                colors = if (isUsed) ButtonDefaults.outlinedButtonColors(
                                    disabledContentColor = Color.Gray
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            if (waitingForClose) {
                Card(colors = CardDefaults.cardColors(containerColor = LocQarOrange.copy(alpha = 0.1f))) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Waiting for door to close...")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.skipCurrentLock() }) {
                    Text("Skip (disable)")
                }
                OutlinedButton(
                    onClick = { viewModel.undoLastMapping() },
                    enabled = mappings.isNotEmpty()
                ) {
                    Text("Undo")
                }
            }
        }

        // Current mappings
        if (mappings.isNotEmpty()) {
            HorizontalDivider()
            Text("Mappings:", fontWeight = FontWeight.Bold)
            mappings.sortedBy { it.lockNumber }.forEach { m ->
                Text(
                    "Lock ${m.lockNumber} -> ${m.doorLabel ?: "SKIPPED"}",
                    fontSize = 13.sp,
                    color = if (m.doorLabel != null) Color.Black else Color.Gray
                )
            }
        }
    }
}

@Composable
private fun DoorVerificationStep(viewModel: CommissioningViewModel) {
    val mappings by viewModel.mappings.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Door Verification", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Verify that door labels match physical doors.")

        val testLabels = listOf("1", "6", "12")
        testLabels.forEach { label ->
            val mapping = mappings.find { it.doorLabel == label }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Door '$label'")
                if (mapping != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (mapping.verified) {
                            Icon(Icons.Default.CheckCircle, null, tint = DoorClosedColor)
                        }
                        Button(onClick = { viewModel.verifyDoorByLabel(label) }) {
                            Text("Test")
                        }
                    }
                } else {
                    Text("Not mapped", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun KioskSettingsStep(viewModel: CommissioningViewModel) {
    val lockerName by viewModel.lockerName.collectAsState()
    val helpPhone by viewModel.helpPhone.collectAsState()
    val idlePollInterval by viewModel.idlePollInterval.collectAsState()
    val openConfirmSeconds by viewModel.openConfirmSeconds.collectAsState()
    val openTooLongSeconds by viewModel.openTooLongSeconds.collectAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kiosk Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = lockerName,
            onValueChange = { viewModel.setLockerName(it) },
            label = { Text("Locker Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = helpPhone,
            onValueChange = { viewModel.setHelpPhone(it) },
            label = { Text("Help Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = idlePollInterval.toString(),
            onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setIdlePollInterval(v) } },
            label = { Text("Idle Poll Interval (ms)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = openConfirmSeconds.toString(),
            onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setOpenConfirmSeconds(v) } },
            label = { Text("Open Confirm Timeout (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = openTooLongSeconds.toString(),
            onValueChange = { it.toIntOrNull()?.let { v -> viewModel.setOpenTooLongSeconds(v) } },
            label = { Text("Open Too Long Alert (seconds)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun AdminSecurityStep(viewModel: CommissioningViewModel) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val passwordError by viewModel.passwordError.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Admin Security", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Set an admin password. This is required for access to admin functions. Do not use 'admin'.")

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.setAdminPassword(it)
            },
            label = { Text("Admin Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = passwordError != null
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                viewModel.setAdminPasswordConfirm(it)
            },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = passwordError != null
        )

        passwordError?.let {
            Text(it, color = LocQarRed, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SummaryStep(
    viewModel: CommissioningViewModel,
    onFinished: () -> Unit
) {
    val mappings by viewModel.mappings.collectAsState()
    val stationNumber by viewModel.stationNumber.collectAsState()
    val lockerName by viewModel.lockerName.collectAsState()

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Summary", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Locker: $lockerName", fontWeight = FontWeight.Bold)
                Text("Station: $stationNumber")
                Text("Mapped doors: ${mappings.count { it.doorLabel != null }}")
                Text("Skipped: ${mappings.count { it.doorLabel == null }}")
            }
        }

        Text("Door Mappings:", fontWeight = FontWeight.Bold)
        mappings.sortedBy { it.lockNumber }.forEach { m ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Lock ${m.lockNumber}")
                Text(
                    m.doorLabel ?: "DISABLED",
                    color = if (m.doorLabel != null) Color.Black else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                if (m.verified) {
                    Icon(Icons.Default.CheckCircle, null, tint = DoorClosedColor, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    viewModel.finishCommissioning()
                    onFinished()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = LocQarGreen)
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Complete Commissioning")
            }

            OutlinedButton(onClick = { viewModel.exportConfig() }) {
                Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export")
            }
        }
    }
}
