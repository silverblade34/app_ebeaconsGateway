package com.sysnet.ebeaconsgateway

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sysnet.ebeaconsgateway.ui.screens.SplashScreen
import com.sysnet.ebeaconsgateway.ui.theme.EbeaconsGatewayTheme
import com.sysnet.ebeaconsgateway.utils.PermissionUtils

class MainActivity : ComponentActivity() {
    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            EbeaconsGatewayTheme {
                SplashScreen()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = PermissionUtils.checkAndRequestBluetoothPermissions(this)

        if (permissionsToRequest.isNotEmpty()) {
            PermissionUtils.requestPermissions(
                this,
                permissionsToRequest,
                BLUETOOTH_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST_CODE) {
            val permissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (permissionsGranted) {
                Log.d("Bluetooth", "All permissions granted")
            } else {
                Log.w("Bluetooth", "Some permissions were denied")
            }
        }
    }
}
