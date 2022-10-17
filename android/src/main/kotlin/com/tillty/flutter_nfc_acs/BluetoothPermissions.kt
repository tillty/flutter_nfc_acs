package com.tillty.flutter_nfc_acs

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener

abstract class BluetoothPermissions : RequestPermissionsResultListener {

    companion object {
        // A code we've defined, to identify the permission request.
        private const val PERMISSION_REQUEST = 548351319
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean {
        return when (requestCode) {
            PERMISSION_REQUEST -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    afterPermissionsGranted()
                } else {
                    afterPermissionsDenied()
                }
                true
            }
            else -> false
        }
    }

    fun requestPermissions() {
        val activity = this.activity ?: return

        if (Build.VERSION.SDK_INT < 31) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
                PERMISSION_REQUEST
            )
        } else {
            ActivityCompat.requestPermissions(
                activity, arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                ), PERMISSION_REQUEST
            )
        }
    }

    fun hasPermissions(): Boolean {
        val activity = this.activity ?: return false

        return if (Build.VERSION.SDK_INT < 31) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    protected abstract val activity: Activity?
    protected abstract fun afterPermissionsGranted()
    protected abstract fun afterPermissionsDenied()
}