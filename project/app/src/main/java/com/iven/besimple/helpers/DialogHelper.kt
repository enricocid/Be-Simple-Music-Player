package com.iven.besimple.helpers

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.core.app.ActivityCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iven.besimple.R
import com.iven.besimple.player.MediaPlayerHolder
import com.iven.besimple.ui.UIControlInterface

object DialogHelper {

    @JvmStatic
    fun stopPlaybackDialog(
            context: Context,
            mediaPlayerHolder: MediaPlayerHolder
    ) {
        context.run {
            MaterialAlertDialogBuilder(this)
                    .setTitle(resources.getString(R.string.app_name))
                    .setMessage(resources.getString(R.string.on_close_activity))

                    .setNegativeButton(resources.getString(R.string.no)) { _, _ ->
                        mediaPlayerHolder.stopPlaybackService(false)

                    }
                    .setPositiveButton(resources.getString(R.string.yes)) { _, _ ->
                        mediaPlayerHolder.stopPlaybackService(true)
                    }
                    .show()
        }
    }

    @JvmStatic
    fun manageAskForReadStoragePermission(
            activity: Activity,
            uiControlInterface: UIControlInterface
    ) {
        activity.run {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                    )
            ) {

                MaterialAlertDialogBuilder(this)
                        .setTitle(resources.getString(R.string.app_name))
                        .setMessage(resources.getString(R.string.perm_rationale))

                        .setNegativeButton(resources.getString(android.R.string.cancel)) { _, _ ->
                            uiControlInterface.onDenyPermission()

                        }
                        .setPositiveButton(resources.getString(android.R.string.ok)) { _, _ ->
                            PermissionsHelper.askForReadStoragePermission(
                                    activity
                            )
                        }
                        .setCancelable(false)
                        .show()
            } else {
                PermissionsHelper.askForReadStoragePermission(
                        activity
                )
            }
        }
    }
}
