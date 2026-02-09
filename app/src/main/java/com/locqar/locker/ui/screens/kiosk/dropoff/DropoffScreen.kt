package com.locqar.locker.ui.screens.kiosk.dropoff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locqar.locker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropoffScreen(
    viewModel: DropoffViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val courierCode by viewModel.courierCode.collectAsState()
    val recipientName by viewModel.recipientName.collectAsState()
    val recipientPhone by viewModel.recipientPhone.collectAsState()
    val trackingNumber by viewModel.trackingNumber.collectAsState()
    val assignedDoorLabel by viewModel.assignedDoorLabel.collectAsState()
    val pickupCode by viewModel.pickupCode.collectAsState()
    val message by viewModel.message.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drop Off Parcel") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (state) {
                DropoffState.COURIER_LOGIN -> {
                    Icon(
                        Icons.Default.Badge,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = LocQarBlue
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Courier Login", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = courierCode,
                        onValueChange = { viewModel.setCourierCode(it) },
                        label = { Text("Courier Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage != null
                    )

                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = LocQarRed)
                    }

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.courierLogin() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LocQarBlue)
                    ) {
                        Text("Login", fontSize = 18.sp)
                    }
                }

                DropoffState.PARCEL_DETAILS -> {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Parcel Details", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = recipientName,
                            onValueChange = { viewModel.setRecipientName(it) },
                            label = { Text("Recipient Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = recipientPhone,
                            onValueChange = { viewModel.setRecipientPhone(it) },
                            label = { Text("Recipient Phone (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = trackingNumber,
                            onValueChange = { viewModel.setTrackingNumber(it) },
                            label = { Text("Tracking Number (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        errorMessage?.let {
                            Text(it, color = LocQarRed)
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.submitParcelDetails() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LocQarBlue)
                        ) {
                            Text("Assign Door", fontSize = 18.sp)
                        }
                    }
                }

                DropoffState.ASSIGNING_DOOR -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), color = LocQarBlue)
                    Spacer(Modifier.height(24.dp))
                    Text(message, fontSize = 18.sp, textAlign = TextAlign.Center)
                }

                DropoffState.OPENING_DOOR -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), color = LocQarBlue)
                    Spacer(Modifier.height(24.dp))
                    Text(message, fontSize = 18.sp, textAlign = TextAlign.Center)
                }

                DropoffState.DOOR_OPEN, DropoffState.WAITING_CLOSE -> {
                    Icon(
                        Icons.Default.MeetingRoom,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarOrange
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Door $assignedDoorLabel",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocQarOrange
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Place parcel inside and close the door",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(color = LocQarOrange)
                    Spacer(Modifier.height(8.dp))
                    Text("Waiting for door to close...", color = Color.Gray)
                }

                DropoffState.COMPLETE -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarGreen
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Parcel Stored!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = LocQarGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Door: $assignedDoorLabel", fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = LocQarBlue.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Pickup Code", fontSize = 14.sp, color = Color.Gray)
                            Text(
                                pickupCode,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = LocQarBlue,
                                letterSpacing = 6.sp
                            )
                            Text("Share this code with the recipient", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.dropoffAnother() },
                            colors = ButtonDefaults.buttonColors(containerColor = LocQarBlue)
                        ) {
                            Text("Drop Off Another")
                        }
                        OutlinedButton(onClick = {
                            viewModel.reset()
                            onBack()
                        }) {
                            Text("Done")
                        }
                    }
                }

                DropoffState.NO_DOORS_AVAILABLE -> {
                    Icon(
                        Icons.Default.DoNotDisturb,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarOrange
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("No Doors Available", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LocQarOrange)
                    Spacer(Modifier.height(16.dp))
                    Text("All locker doors are currently in use. Please try again later.", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { viewModel.reset(); onBack() }) {
                        Text("Back to Home")
                    }
                }

                DropoffState.ERROR -> {
                    Icon(
                        Icons.Default.Error,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarRed
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(errorMessage ?: "An error occurred", fontSize = 18.sp, color = LocQarRed, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { viewModel.reset() }) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}
