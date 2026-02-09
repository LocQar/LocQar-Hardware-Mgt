package com.locqar.locker.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.locqar.locker.LocQarApp
import com.locqar.locker.hardware.service.LockerDaemonService
import com.locqar.locker.ui.screens.admin.AdminDashboardScreen
import com.locqar.locker.ui.screens.admin.AdminLoginScreen
import com.locqar.locker.ui.screens.admin.AdminViewModel
import com.locqar.locker.ui.screens.commissioning.CommissioningScreen
import com.locqar.locker.ui.screens.commissioning.CommissioningViewModel
import com.locqar.locker.ui.screens.kiosk.dropoff.DropoffScreen
import com.locqar.locker.ui.screens.kiosk.dropoff.DropoffViewModel
import com.locqar.locker.ui.screens.kiosk.home.KioskHomeScreen
import com.locqar.locker.ui.screens.kiosk.pickup.PickupScreen
import com.locqar.locker.ui.screens.kiosk.pickup.PickupViewModel
import com.locqar.locker.ui.screens.kiosk.recall.RecallScreen
import com.locqar.locker.ui.screens.kiosk.recall.RecallViewModel
import com.locqar.locker.ui.screens.techtool.TechToolScreen
import com.locqar.locker.ui.screens.techtool.TechToolViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    navController: NavHostController,
    daemon: LockerDaemonService?
) {
    val repository = remember { LocQarApp.instance.repository }
    var lockerName by remember { mutableStateOf("LocQar Locker") }
    var helpPhone by remember { mutableStateOf("") }

    // Load settings
    LaunchedEffect(Unit) {
        lockerName = repository.getSetting(com.locqar.locker.data.db.entity.SettingsKeys.LOCKER_NAME)
        helpPhone = repository.getSetting(com.locqar.locker.data.db.entity.SettingsKeys.HELP_PHONE)
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.KIOSK_HOME
    ) {
        // Kiosk Home
        composable(NavRoutes.KIOSK_HOME) {
            KioskHomeScreen(
                lockerName = lockerName,
                helpPhone = helpPhone,
                onPickup = { navController.navigate(NavRoutes.PICKUP) },
                onDropoff = { navController.navigate(NavRoutes.DROPOFF) },
                onHelp = { navController.navigate(NavRoutes.RECALL) },
                onAdminAccess = { navController.navigate(NavRoutes.ADMIN_LOGIN) }
            )
        }

        // Pickup
        composable(NavRoutes.PICKUP) {
            val vm: PickupViewModel = viewModel()
            vm.daemon = daemon
            PickupScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        // Drop-off
        composable(NavRoutes.DROPOFF) {
            val vm: DropoffViewModel = viewModel()
            vm.daemon = daemon
            DropoffScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        // Recall
        composable(NavRoutes.RECALL) {
            val vm: RecallViewModel = viewModel()
            vm.daemon = daemon
            RecallScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        // Admin Login
        composable(NavRoutes.ADMIN_LOGIN) {
            AdminLoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavRoutes.ADMIN_DASHBOARD) {
                        popUpTo(NavRoutes.ADMIN_LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Admin Dashboard
        composable(NavRoutes.ADMIN_DASHBOARD) {
            val vm: AdminViewModel = viewModel()
            vm.daemon = daemon
            AdminDashboardScreen(
                viewModel = vm,
                onTechTool = { navController.navigate(NavRoutes.TECH_TOOL) },
                onCommissioning = { navController.navigate(NavRoutes.COMMISSIONING) },
                onBack = {
                    navController.navigate(NavRoutes.KIOSK_HOME) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Tech Tool
        composable(NavRoutes.TECH_TOOL) {
            val vm: TechToolViewModel = viewModel()
            vm.daemon = daemon
            TechToolScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        // Commissioning
        composable(NavRoutes.COMMISSIONING) {
            val vm: CommissioningViewModel = viewModel()
            vm.daemon = daemon
            CommissioningScreen(
                viewModel = vm,
                onFinished = {
                    navController.navigate(NavRoutes.ADMIN_DASHBOARD) {
                        popUpTo(NavRoutes.COMMISSIONING) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
