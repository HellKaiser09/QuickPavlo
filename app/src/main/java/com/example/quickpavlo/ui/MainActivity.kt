package com.example.quickpavlo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import com.example.quickpavlo.ui.navigation.QuickPavloNavHost

// 1. LA ETIQUETA DE HILT:
// Esto convierte a tu Activity en un "Receptor". Le dice a Hilt que a partir de aquí hacia abajo,
// cualquier pantalla o ViewModel puede pedir dependencias (como nuestro motor P2P).
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Envolvemos toda la app en el tema base de Material Design 3
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 2. EL DIRECTOR DE TRÁFICO:
                    // Entregamos el control de la pantalla a nuestro NavHost
                    QuickPavloNavHost()
                }
            }
        }
    }
}