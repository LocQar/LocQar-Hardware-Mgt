package com.locqar.locker.ui.screens.kiosk.recall

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locqar.locker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecallScreen(
    viewModel: RecallViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val parcels by viewModel.parcels.collectAsState()
    val message by viewModel.message.collectAsState()
    val selectedDoorLabel by viewModel.selectedDoorLabel.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadParcels()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recall Parcel") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocQarOrange,
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                RecallState.LIST_PARCELS -> {
                    Text("Stored Parcels", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))

                    if (parcels.isEmpty()) {
                        Spacer(Modifier.height(48.dp))
                        Icon(
                            Icons.Default.Inbox,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.LightGray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No parcels currently stored", color = Color.Gray, fontSize = 18.sp)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(parcels) { parcel ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = LocQarOccupiedColor.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Door ${parcel.doorLabel}",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Lock ${parcel.lockNumber}",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Button(
                                            onClick = { viewModel.recallParcel(parcel.doorId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = LocQarOrange)
                                        ) {
                                            Text("Recall")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                RecallState.OPENING_DOOR -> {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), color = LocQarOrange)
                    Spacer(Modifier.height(24.dp))
                    Text(message, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.weight(1f))
                }

                RecallState.DOOR_OPEN, RecallState.WAITING_CLOSE -> {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.MeetingRoom,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarOrange
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Door $selectedDoorLabel",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocQarOrange
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(message, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(color = LocQarOrange)
                    Text("Waiting for door to close...", color = Color.Gray)
                    Spacer(Modifier.weight(1f))
                }

                RecallState.COMPLETE -> {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarGreen
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Parcel Recalled", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = LocQarGreen)
                    Spacer(Modifier.height(16.dp))
                    Text(message, fontSize = 16.sp)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { viewModel.reset() }) {
                        Text("Recall Another")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Text("Done")
                    }
                    Spacer(Modifier.weight(1f))
                }

                RecallState.ERROR -> {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.Error,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarRed
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(message, fontSize = 18.sp, color = LocQarRed, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { viewModel.reset() }) {
                        Text("Try Again")
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private val LocQarOccupiedColor = DoorOccupiedColor
