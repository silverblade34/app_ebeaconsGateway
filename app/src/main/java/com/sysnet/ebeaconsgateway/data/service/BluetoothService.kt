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
import com.google.gson.Gson
import com.sysnet.ebeaconsgateway.R
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BluetoothService : Service() {
    private val TAG = "BluetoothService"
    private lateinit var mqttClient: MqttClient
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private val scannedDevices = mutableSetOf<String>()
    private var isScanning = false
    private lateinit var wakeLock: PowerManager.WakeLock

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::WakeLock")
        wakeLock.acquire()
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
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
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

        startContinuousScanning()

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
        val sharedPreferences = getSharedPreferences("ConfigPrefs", Context.MODE_PRIVATE)
        val rssiThreshold = sharedPreferences.getString("RSSI", "-100")?.toIntOrNull() ?: -100

        synchronized(scannedDevices) {
            if (scannedDevices.isNotEmpty()) {
                // Filtrar dispositivos basados en el RSSI
                val filteredDevices = scannedDevices.filter { deviceInfo ->
                    val (_, _, rssi) = parseDeviceInfo(deviceInfo)
                    rssi >= rssiThreshold // Solo incluir dispositivos con RSSI mayor o igual al umbral
                }

                if (filteredDevices.isNotEmpty()) {
                    val message = formatScannedDevicesMessage(filteredDevices, identificador)
                    val topic = "/gw/scanpub/$identificador"

                    publishToMqtt(topic, message)
                }

                scannedDevices.clear()
            }
        }
    }

    private fun formatScannedDevicesMessage(devices: List<String>, identificador: String): String {
        val timeStamp = getIso8601Timestamp()
        val gatewayMac = identificador
        val message = mutableListOf<Map<String, Any>>()

        // Mensaje inicial con detalles generales
        message.add(
            mapOf(
                "TimeStamp" to timeStamp,
                "Format" to "Movil",
                "GatewayMAC" to gatewayMac
            )
        )

        // Agregar información de los dispositivos escaneados
        devices.forEach { deviceInfo ->
            val (name, address, rssi) = parseDeviceInfo(deviceInfo)
            message.add(
                mapOf(
                    "TimeStamp" to timeStamp,
                    "Format" to "RawData",
                    "BLEMAC" to formatMacAddress(address),
                    "RSSI" to rssi,
                    "AdvType" to "Legacy-res",
                    "BLEName" to name,
                    "RawData" to "181600EA00000010003CFFE0015C0058000BEEECE20D4A2FB8020A00"
                )
            )
        }
        return Gson().toJson(message)
    }

    private fun getIso8601Timestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun formatMacAddress(macAddress: String): String {
        return macAddress.replace(":", "")
    }

    private fun parseDeviceInfo(deviceInfo: String): Triple<String, String, Int> {
        // Asumimos que el formato es "Nombre - Dirección - RSSI: ValorRSSI"
        val parts = deviceInfo.split(" - ")
        val name = parts.getOrNull(0) ?: "Unknown"
        val address = parts.getOrNull(1) ?: "00:00:00:00:00:00"
        val rssi = parts.getOrNull(2)?.replace("RSSI: ", "")?.toIntOrNull() ?: 0
        return Triple(name, address, rssi)
    }

    private fun publishToMqtt(topic: String, message: String) {
        try {
            mqttClient.publish(topic, message.toByteArray(), 0, false)
            Log.d(TAG, "Published message to topic: $topic")
        } catch (e: MqttException) {
            Log.e(TAG, "Error publishing message: ${e.message}")
        }
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
            .setContentText("El escaneo Bluetooth está ejecutándose")
            .setSmallIcon(R.drawable.baseline_settings_input_antenna_24)
            .build()
    }
}
