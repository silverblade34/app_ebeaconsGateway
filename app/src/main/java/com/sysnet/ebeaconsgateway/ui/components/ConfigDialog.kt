package com.sysnet.ebeaconsgateway.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun ConfigDialog(
    onDismiss: () -> Unit,
    context: Context,
    snackbarHostState: SnackbarHostState // Agregar ScaffoldState para manejar el Snackbar
) {
    var rssi by remember { mutableStateOf("") }
    var intervalo by remember { mutableStateOf("") }
    var identificador by remember { mutableStateOf("") }

    // Cargar valores previos desde SharedPreferences
    val sharedPreferences = context.getSharedPreferences("ConfigPrefs", Context.MODE_PRIVATE)

    // Cargar valores iniciales
    LaunchedEffect(Unit) {
        rssi = sharedPreferences.getString("RSSI", "") ?: ""
        intervalo = sharedPreferences.getString("Intervalo", "") ?: ""
        identificador = sharedPreferences.getString("Identificador", "") ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración") },
        text = {
            Column {
                TextField(
                    value = rssi,
                    onValueChange = { rssi = it },
                    label = { Text("RSSI") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = intervalo,
                    onValueChange = { intervalo = it },
                    label = { Text("Intervalo") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = identificador,
                    onValueChange = { identificador = it },
                    label = { Text("Identificador") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Guardar valores en SharedPreferences
                    sharedPreferences.edit()
                        .putString("RSSI", rssi)
                        .putString("Intervalo", intervalo)
                        .putString("Identificador", identificador)
                        .apply()

                    CoroutineScope(Dispatchers.Main).launch {
                        snackbarHostState.showSnackbar("Configuración guardada")
                    }

                    // Mostrar Snackbar
                    onDismiss()
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
