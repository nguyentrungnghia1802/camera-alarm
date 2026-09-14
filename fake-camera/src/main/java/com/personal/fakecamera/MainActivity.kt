package com.personal.fakecamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.personal.fakecamera.ui.FakeCameraScreen

class MainActivity : ComponentActivity() {

    private lateinit var notificationHelper: FakeNotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationHelper = FakeNotificationHelper(this)

        setContent {
            var hasNotificationPermission by remember {
                mutableStateOf(checkNotificationPermission())
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasNotificationPermission = isGranted
                if (!isGranted) {
                    Toast.makeText(
                        this,
                        "Notification permission is required to post alerts",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            FakeCameraScreen(
                hasNotificationPermission = hasNotificationPermission,
                onRequestPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onSendAlert = { payload, useUniqueId ->
                    val customId = if (useUniqueId) null else 1000
                    val log = notificationHelper.postNotification(payload, customId)
                    Toast.makeText(
                        this,
                        "Posted: ${payload.title} (ID: ${log.id})",
                        Toast.LENGTH_SHORT
                    ).show()
                    log
                }
            )
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
