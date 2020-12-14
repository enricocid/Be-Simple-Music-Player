package com.iven.besimple.helpers

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.iven.besimple.BeSimpleConstants

object PermissionsHelper {

    @JvmStatic
    fun hasToAskForReadStoragePermission(activity: Activity) =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED

    @TargetApi(Build.VERSION_CODES.M)
    fun askForReadStoragePermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            BeSimpleConstants.PERMISSION_REQUEST_READ_EXTERNAL_STORAGE
        )
    }
}
