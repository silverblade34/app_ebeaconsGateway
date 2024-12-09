package com.sysnet.ebeaconsgateway.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sysnet.ebeaconsgateway.R
import com.sysnet.ebeaconsgateway.data.service.BluetoothService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun StartStopButton(
    context: Context,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    // Estado para controlar si el servicio está iniciado
    val sharedPreferences = context.getSharedPreferences("ConfigPrefs", Context.MODE_PRIVATE)
    val isServiceRunning = remember { mutableStateOf(sharedPreferences.getBoolean("ServiceRunning", false)) }
    val showDialog = remember { mutableStateOf(false) } // Estado para el diálogo de inicialización

    Button(
        onClick = {
            val rssi = sharedPreferences.getString("RSSI", "") ?: ""
            val intervalo = sharedPreferences.getString("Intervalo", "") ?: ""
            val identificador = sharedPreferences.getString("Identificador", "") ?: ""

            if (rssi.isNotEmpty() && intervalo.isNotEmpty() && identificador.isNotEmpty()) {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    // Mostrar Toast si Bluetooth no está activado
                    Toast.makeText(context, "Debe activar el Bluetooth", Toast.LENGTH_SHORT).show()
                } else {
                    if (!isServiceRunning.value) {
                        // Iniciar el servicio
                        val intent = Intent(context, BluetoothService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }

                        // Mostrar diálogo de inicialización
                        showDialog.value = true
                        coroutineScope.launch {
                            // Simular inicialización con un retraso de 4 segundos
                            kotlinx.coroutines.delay(6000)
                            showDialog.value = false

                            // Guardar el estado como iniciado
                            sharedPreferences.edit().putBoolean("ServiceRunning", true).apply()
                            isServiceRunning.value = true
                        }
                    } else {
                        // Detener el servicio
                        val intent = Intent(context, BluetoothService::class.java)
                        context.stopService(intent)

                        // Guardar el estado como detenido
                        sharedPreferences.edit().putBoolean("ServiceRunning", false).apply()
                        isServiceRunning.value = false
                    }
                }
            } else {
                // Mostrar Snackbar si no está configurado
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Por favor, realice la configuración inicial",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isServiceRunning.value) Color.Red else Color(0xFF438ADE),
            contentColor = Color.White
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(
                    id = if (isServiceRunning.value) R.drawable.baseline_stop_24 else R.drawable.baseline_play_arrow_24
                ),
                contentDescription = if (isServiceRunning.value) "Detener" else "Iniciar",
                modifier = Modifier.size(30.dp),
                tint = Color.Unspecified
            )

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isServiceRunning.value) "Detener escaneo" else "Iniciar escaneo",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // Mostrar diálogo de inicialización
    if (showDialog.value) {
        Dialog(onDismissRequest = { }) {
            Box(
                modifier = Modifier
                    .size(150.dp) // Tamaño reducido del diálogo
                    .background(Color.White, shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF438ADE)) // Azul oscuro
                    Spacer(modifier = Modifier.height(12.dp)) // Espaciado más compacto
                    Text(
                        text = "Inicializando...",
                        fontSize = 14.sp, // Tamaño ligeramente más pequeño
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF002366) // Azul oscuro profundo
                    )
                }
            }
        }
    }

}
