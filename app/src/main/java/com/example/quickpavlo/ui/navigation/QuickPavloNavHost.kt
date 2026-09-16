package com.example.quickpavlo.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.quickpavlo.ui.home.HomeScreen
import com.example.quickpavlo.ui.feature.permissions.PermissionScreen
import com.example.quickpavlo.ui.feature.scanner.CameraScannerScreen

@Composable
fun QuickPavloNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // Variable táctica temporal para recordar a dónde queríamos ir después de dar los permisos
    var pendingDestination by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize()
    ) {

        // 1. PANTALLA DE INICIO
        composable(Screen.Home.route) {
            HomeScreen(
                onHostClick = {
                    pendingDestination = Screen.HostQr.route
                    navController.navigate(Screen.Permissions.route)
                },
                onClientClick = {
                    pendingDestination = Screen.ClientScanner.route
                    navController.navigate(Screen.Permissions.route)
                }
            )
        }

        // 2. PANTALLA DE PERMISOS
        composable(Screen.Permissions.route) {
            PermissionScreen(
                onPermissionsGranted = {
                    // Luz verde de los permisos. Viajamos al destino pendiente y limpiamos el historial
                    // para que si presionan "Atrás", no regresen a la pantalla de permisos.
                    pendingDestination?.let { dest ->
                        navController.navigate(dest) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                }
            )
        }

        // 3. RUTA A: EL ANFITRIÓN (Generador QR)
        composable(Screen.HostQr.route) {
            // Reemplazaremos esto con tu pantalla real del QR en el siguiente paso
            Text("Aquí irá la pantalla del QR Host")
        }

        // 4. RUTA B: EL CLIENTE (Escáner de Cámara)
        composable(Screen.ClientScanner.route) {
            CameraScannerScreen(
                onQrScanned = { scannedToken ->
                    // Cuando ML Kit detecte un QR, vendremos aquí.
                    // TODO: Pasarle este token al ViewModel para que inicie la conexión
                    println("¡Código detectado en navegación!: $scannedToken")
                }
            )
        }
    }
}