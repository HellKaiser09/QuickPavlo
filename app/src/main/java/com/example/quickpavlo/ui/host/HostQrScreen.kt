package com.example.quickpavlo.ui.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.quickpavlo.domain.network.ConnectionState
import com.example.quickpavlo.ui.feature.scanner.QrCodeImage
import com.example.quickpavlo.ui.theme.QuickPavloTheme

@Composable
fun HostQrScreen(
    modifier: Modifier = Modifier,
    onConnected: () -> Unit = {},
    viewModel: HostViewModel = hiltViewModel()
) {
    val qrToken by viewModel.qrToken.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState(initial = ConnectionState.IDLE)

    LaunchedEffect(Unit) {
        viewModel.startHosting()
    }

    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
            onConnected()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Modo Anfitrión",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pide a tu amigo que escanee este código para conectarse de forma segura.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            val token = qrToken
            if (token != null) {
                QrCodeImage(content = token)
            } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Generando código seguro...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            val stateText = when (connectionState) {
                ConnectionState.IDLE -> "Inicializando radar..."
                ConnectionState.ADVERTISING -> "Esperando conexión..."
                ConnectionState.DISCOVERING -> "Buscando dispositivos..."
                ConnectionState.CONNECTING -> "Estableciendo conexión..."
                ConnectionState.CONNECTED -> "¡Conectado con éxito!"
                ConnectionState.ERROR -> "Error de conexión"
            }

            Text(
                text = "Estado: $stateText",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when (connectionState) {
                    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                    ConnectionState.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HostQrScreenPreview() {
    QuickPavloTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Modo Anfitrión",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Pide a tu amigo que escanee este código para conectarse de forma segura.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                QrCodeImage(content = "SAMPLE_TOKEN")
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Estado: Esperando conexión...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}