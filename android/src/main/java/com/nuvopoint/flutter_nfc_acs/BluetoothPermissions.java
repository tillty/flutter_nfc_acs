package com.nuvopoint.flutter_nfc_acs;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;

abstract class BluetoothPermissions implements RequestPermissionsResultListener {
  // A code we've defined, to identify the permission request.
  private static final int REQUEST_FINE_LOCATION_PERMISSIONS = 548351319;

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (requestCode == REQUEST_FINE_LOCATION_PERMISSIONS) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        afterPermissionsGranted();
      } else {
        afterPermissionsDenied();
      }
      return true;
    }

    return false;
  }

  void requestPermissions() {
    ActivityCompat.requestPermissions(
        getActivity(),
        new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION
        },
        REQUEST_FINE_LOCATION_PERMISSIONS);
  }

  boolean hasPermissions() {
    return ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  protected abstract Activity getActivity();

  protected abstract void afterPermissionsGranted();

  protected abstract void afterPermissionsDenied();
}
