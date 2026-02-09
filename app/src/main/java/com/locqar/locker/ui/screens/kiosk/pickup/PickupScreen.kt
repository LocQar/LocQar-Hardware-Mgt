package com.locqar.locker.ui.screens.kiosk.pickup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locqar.locker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupScreen(
    viewModel: PickupViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val code by viewModel.code.collectAsState()
    val doorLabel by viewModel.doorLabel.collectAsState()
    val message by viewModel.message.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pickup Parcel") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LocQarGreen,
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
                PickupState.ENTER_CODE -> {
                    Icon(
                        Icons.Default.Dialpad,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = LocQarGreen
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Enter Pickup Code",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = { viewModel.setCode(it) },
                        label = { Text("Pickup Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = { viewModel.submitCode() }),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 28.sp,
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        ),
                        isError = errorMessage != null
                    )

                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = LocQarRed, fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.submitCode() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LocQarGreen)
                    ) {
                        Text("Submit", fontSize = 18.sp)
                    }
                }

                PickupState.OPENING_DOOR -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = LocQarGreen
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(message, fontSize = 18.sp, textAlign = TextAlign.Center)
                }

                PickupState.DOOR_OPEN, PickupState.WAITING_CLOSE -> {
                    Icon(
                        Icons.Default.MeetingRoom,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarOrange
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Door ${doorLabel}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocQarOrange
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        message,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(color = LocQarOrange)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Waiting for door to close...",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }

                PickupState.COMPLETE -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarGreen
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Pickup Complete!",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocQarGreen
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Thank you!", fontSize = 18.sp)
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = {
                            viewModel.reset()
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Done", fontSize = 18.sp)
                    }
                }

                PickupState.ERROR -> {
                    Icon(
                        Icons.Default.Error,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = LocQarRed
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        errorMessage ?: "An error occurred",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        color = LocQarRed
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text("Try Again", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
