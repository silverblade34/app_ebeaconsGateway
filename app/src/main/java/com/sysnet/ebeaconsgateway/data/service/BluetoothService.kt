package com.sysnet.ebeaconsgateway.data.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sysnet.ebeaconsgateway.R
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class BluetoothService : Service() {
    private val TAG = "BluetoothService"
    private lateinit var mqttClient: MqttClient
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private val scannedDevices = mutableSetOf<String>()
    private var isScanning = false

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Crear notificación
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Conectar a MQTT y escanear Bluetooth periódicamente
        val sharedPreferences = getSharedPreferences("ConfigPrefs", Context.MODE_PRIVATE)
        val intervalo = sharedPreferences.getString("Intervalo", "10")?.toLong() ?: 10L
        val identificador = sharedPreferences.getString("Identificador", "") ?: ""

        // Inicializar MQTT y Bluetooth
        connectToMqttBroker()
        startBluetoothScanning(intervalo * 1000, identificador)

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        handler.removeCallbacks(runnable)
        disconnectMqttBroker()
        super.onDestroy()
    }

    private fun connectToMqttBroker() {
        try {
            val broker = "tcp://204.48.17.106:1883"
            val clientId = MqttClient.generateClientId()
            mqttClient = MqttClient(broker, clientId, MemoryPersistence())

            val connOpts = MqttConnectOptions().apply {
                isCleanSession = true
                userName = "sysnet"
                password = "sysnet4".toCharArray()
            }

            mqttClient.connect(connOpts)
            Log.d(TAG, "Connected to MQTT Broker")
        } catch (e: MqttException) {
            Log.e(TAG, "Error connecting to MQTT Broker: ${e.message}")
        }
    }

    private fun disconnectMqttBroker() {
        try {
            if (mqttClient.isConnected) {
                mqttClient.disconnect()
                Log.d(TAG, "Disconnected from MQTT Broker")
            }
        } catch (e: MqttException) {
            Log.e(TAG, "Error disconnecting MQTT Broker: ${e.message}")
        }
    }

    private fun startBluetoothScanning(interval: Long, identificador: String) {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not available or disabled")
            return
        }

        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // Start continuous scanning
        startContinuousScanning()

        // Setup periodic MQTT publishing
        runnable = object : Runnable {
            override fun run() {
                publishScannedDevices(identificador)
                handler.postDelayed(this, interval)
            }
        }
        handler.post(runnable)
    }

    @SuppressLint("MissingPermission")
    private fun startContinuousScanning() {
        if (isScanning) return

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceInfo = "${result.device.name ?: "Unknown"} - ${result.device.address} - RSSI: ${result.rssi}"
                synchronized(scannedDevices) {
                    scannedDevices.add(deviceInfo)
                }
            }

            @SuppressLint("MissingPermission")
            override fun onBatchScanResults(results: List<ScanResult>) {
                synchronized(scannedDevices) {
                    results.forEach { result ->
                        val deviceInfo = "${result.device.name ?: "Unknown"} - ${result.device.address} - RSSI: ${result.rssi}"
                        scannedDevices.add(deviceInfo)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                isScanning = false
            }
        }

        try {
            bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
            Log.d(TAG, "Continuous Bluetooth scanning started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting continuous scan: ${e.message}")
            isScanning = false
        }
    }

    private fun publishScannedDevices(identificador: String) {
        synchronized(scannedDevices) {
            if (scannedDevices.isNotEmpty()) {
                val formattedMessage = formatBluetoothDevices(scannedDevices.toList())
                val topic = "/gw/scanpub/$identificador"

                //Log.w(TAG, "--------------------------------------")
                //Log.w(TAG, formattedMessage)

                publishToMqtt(topic, formattedMessage)

                // Optional: Clear devices after publishing
                scannedDevices.clear()
            }
        }
    }

    private fun publishToMqtt(topic: String, message: String) {
        try {
            mqttClient.publish(topic, message.toByteArray(), 0, false)
            Log.d(TAG, "Published message to topic: $topic")
        } catch (e: MqttException) {
            Log.e(TAG, "Error publishing message: ${e.message}")
        }
    }

    private fun formatBluetoothDevices(devices: List<String>): String {
        return devices.joinToString("\n") { it }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Servicio de eBeacons")
            .setContentText("El escaneo Bluetooth ejecutandose")
            .setSmallIcon(R.drawable.baseline_settings_input_antenna_24)
            .build()
    }
}
