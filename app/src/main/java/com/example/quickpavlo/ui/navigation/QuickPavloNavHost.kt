package com.example.quickpavlo.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.ui.feature.chat.ChatScreen
import com.example.quickpavlo.ui.feature.permissions.PermissionScreen
import com.example.quickpavlo.ui.feature.scanner.CameraScannerScreen
import com.example.quickpavlo.ui.feature.scanner.ClientViewModel
import com.example.quickpavlo.ui.home.HomeScreen
import com.example.quickpavlo.ui.host.HostQrScreen

@Composable
fun QuickPavloNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    var pendingDestination by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize()
    ) {

        // 1. PANTALLA DE INICIO
        composable(Screen.Home.route) {
            HomeScreen(
                onHostClick = {
                    if (hasRequiredPermissions(context)) {
                        // Si ya tenemos permisos, vamos directo sin escalas (sin flash)
                        navController.navigate(Screen.HostQr.route)
                    } else {
                        // Si no, navegamos a la pantalla de permisos
                        pendingDestination = Screen.HostQr.route
                        navController.navigate(Screen.Permissions.route)
                    }
                },
                onClientClick = {
                    if (hasRequiredPermissions(context)) {
                        navController.navigate(Screen.ClientScanner.route)
                    } else {
                        pendingDestination = Screen.ClientScanner.route
                        navController.navigate(Screen.Permissions.route)
                    }
                }
            )
        }

        // 2. PANTALLA DE PERMISOS
        composable(Screen.Permissions.route) {
            PermissionScreen(
                onPermissionsGranted = {
                    pendingDestination?.let { dest ->
                        navController.navigate(dest) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }
                }
            )
        }

        // 3. RUTA A: EL ANFITRIÓN (Generador QR)
        composable(Screen.HostQr.route) {
            HostQrScreen(
                onConnected = {
                    navController.navigate(Screen.ChatSession.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // 4. RUTA B: EL CLIENTE (Escáner de Cámara)
        composable(Screen.ClientScanner.route) {
            val clientViewModel: ClientViewModel = hiltViewModel()
            val connectionState by clientViewModel.connectionState.collectAsState(initial = ConnectionState.IDLE)
            val isConnecting by clientViewModel.isConnecting.collectAsState()

            LaunchedEffect(connectionState) {
                if (connectionState == ConnectionState.CONNECTED) {
                    navController.navigate(Screen.ChatSession.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            }

            CameraScannerScreen(
                isConnecting = isConnecting,
                onQrScanned = { scannedToken ->
                    clientViewModel.startConnecting(scannedToken)
                }
            )
        }

        // 5. RUTA C: SALA DE CHAT P2P
        composable(Screen.ChatSession.route) {
            ChatScreen(
                onNavigateUp = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}

private fun hasRequiredPermissions(context: Context): Boolean {
    val permissions = mutableListOf(Manifest.permission.CAMERA)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}