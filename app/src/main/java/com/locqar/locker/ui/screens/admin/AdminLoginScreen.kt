package com.locqar.locker.ui.screens.admin

import androidx.compose.foundation.layout.*
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
import com.locqar.locker.LocQarApp
import com.locqar.locker.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember { LocQarApp.instance.repository }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Login") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF212121),
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
            Icon(
                Icons.Default.AdminPanelSettings,
                null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF212121)
            )
            Spacer(Modifier.height(24.dp))
            Text("Admin Access", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = error != null
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = LocQarRed, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    loading = true
                    scope.launch {
                        val valid = repository.verifyAdminPassword(password)
                        loading = false
                        if (valid) {
                            repository.logEvent(
                                com.locqar.locker.data.db.entity.EventType.ADMIN_LOGIN,
                                "ADMIN", "Admin login successful"
                            )
                            onLoginSuccess()
                        } else {
                            error = "Invalid password"
                            repository.logEvent(
                                com.locqar.locker.data.db.entity.EventType.ADMIN_LOGIN,
                                "ADMIN", "Admin login failed",
                                severity = com.locqar.locker.data.db.entity.EventSeverity.WARNING
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text("Login", fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Demo mode toggle
            var demoMode by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = demoMode,
                    onCheckedChange = { demoMode = it }
                )
                Spacer(Modifier.width(8.dp))
                Text("Demo Mode", fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}
