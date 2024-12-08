package com.sysnet.ebeaconsgateway.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sysnet.ebeaconsgateway.R
import com.sysnet.ebeaconsgateway.ui.components.ConfigDialog
import com.sysnet.ebeaconsgateway.ui.components.StartStopButton
import com.sysnet.ebeaconsgateway.ui.theme.EbeaconsGatewayTheme

@Composable
fun SplashScreen() {
    // Estado para controlar la visibilidad del diálogo de configuración
    var showConfigDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // Botón de configuración en la esquina superior derecha
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(onClick = { showConfigDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Configuración",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { innerPadding ->
        // Contenido principal de la pantalla
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Imagen del splash (reemplaza R.drawable.your_splash_image con tu imagen)
                Image(
                    painter = painterResource(id = R.drawable.bluetooth),
                    contentDescription = "Splash Image",
                    modifier = Modifier
                        .size(250.dp)
                        .padding(bottom = 32.dp),
                    contentScale = ContentScale.Fit
                )

                // Botón de iniciar
                StartStopButton(
                    context = context,
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope
                )
            }
        }

        // Diálogo de configuración
        if (showConfigDialog) {
            ConfigDialog (
                onDismiss = { showConfigDialog = false },
                context = context,
                snackbarHostState = snackbarHostState
            )
        }
    }
}


// Preview para vista previa en Android Studio
@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    EbeaconsGatewayTheme {
        SplashScreen()
    }
}