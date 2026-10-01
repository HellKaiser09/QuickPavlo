package com.example.quickpavlo.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home_screen")
    object Permissions : Screen("permissions_screen")

    //  El Anfitrión (Generador de QR)
    object HostQr : Screen("host_qr_screen")

    // El Cliente (Escáner de Cámara)
    object ClientScanner : Screen("client_scanner_screen")

    //La sala de chat
    object ChatSession : Screen("chat_session_screen")
}