package com.locqar.locker.ui.screens.kiosk.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locqar.locker.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KioskHomeScreen(
    lockerName: String,
    helpPhone: String,
    onPickup: () -> Unit,
    onDropoff: () -> Unit,
    onHelp: () -> Unit,
    onAdminAccess: () -> Unit
) {
    var hiddenTapCount by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header with hidden gesture for admin access
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        hiddenTapCount++
                        if (hiddenTapCount >= 5) {
                            hiddenTapCount = 0
                            onAdminAccess()
                        }
                    },
                    onLongClick = {
                        hiddenTapCount = 0
                        onAdminAccess()
                    }
                )
            ) {
                Spacer(Modifier.height(24.dp))
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = LocQarBlue
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    lockerName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocQarBlue
                )
                Text(
                    "Smart Parcel Locker",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
            }

            // Main action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                KioskActionButton(
                    icon = Icons.Default.LocalShipping,
                    title = "Drop Off Parcel",
                    subtitle = "Courier delivery",
                    color = LocQarBlue,
                    onClick = onDropoff
                )

                KioskActionButton(
                    icon = Icons.Default.Inventory2,
                    title = "Pickup Parcel",
                    subtitle = "Enter your pickup code",
                    color = LocQarGreen,
                    onClick = onPickup
                )

                KioskActionButton(
                    icon = Icons.Default.Help,
                    title = "Help / Courier Login",
                    subtitle = if (helpPhone.isNotBlank()) "Call $helpPhone" else "Need assistance?",
                    color = LocQarOrange,
                    onClick = onHelp
                )
            }

            // Footer
            Text(
                "Powered by LocQar",
                fontSize = 12.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun KioskActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    subtitle,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}
