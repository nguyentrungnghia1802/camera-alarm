package com.personal.cameraalarm.ui.alarm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.cameraalarm.R
import com.personal.cameraalarm.alarm.AlarmReceiver
import com.personal.cameraalarm.alarm.StopAlarmReceiver
import com.personal.cameraalarm.app.CameraAlarmApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = intent?.getStringExtra(AlarmReceiver.EXTRA_TOKEN)
        Log.i("CameraAlarm", "ALARMACTIVITY_CREATED: token=$token")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(android.app.KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val sourcePackage = intent?.getStringExtra(EXTRA_SOURCE)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: ""
        val preview = intent?.getStringExtra(EXTRA_PREVIEW) ?: ""
        val time = intent?.getLongExtra(EXTRA_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis()

        val app = application as? CameraAlarmApp
        val targetPackage = sourcePackage ?: app?.container?.triggerConfiguration?.sourcePackage

        val cameraLabel = targetPackage?.let { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg
            }
        } ?: "Camera"

        setContent {
            MaterialTheme {
                AlarmScreen(
                    cameraLabel = cameraLabel,
                    title = title,
                    preview = preview,
                    timestamp = time,
                    onOpenCamera = {
                        // 1. Stop audio, vibration, foreground service and clear alarm state
                        sendStopBroadcast(token)

                        // 2. Open source camera app
                        var launched = false
                        if (!targetPackage.isNullOrBlank()) {
                            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                try {
                                    startActivity(launchIntent)
                                    launched = true
                                } catch (e: Exception) {
                                    Log.w("CameraAlarm", "Failed to launch camera app $targetPackage: ${e.message}")
                                }
                            }
                        }

                        // Fallback: If open fails, open camera App Info and record diagnostic
                        if (!launched) {
                            val diag = "Failed to launch camera app package: $targetPackage"
                            Log.w("CameraAlarm", diag)
                            app?.container?.runtimeDiagnostics?.record(diag)
                            if (!targetPackage.isNullOrBlank()) {
                                try {
                                    val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", targetPackage, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    startActivity(appInfoIntent)
                                } catch (e: Exception) {
                                    Log.e("CameraAlarm", "Failed to open App Info for $targetPackage", e)
                                }
                            }
                        }

                        finish()
                    },
                    onStop = {
                        sendStopBroadcast(token)
                        finish()
                    }
                )
            }
        }
    }

    private fun sendStopBroadcast(token: String?) {
        val stopIntent = Intent(this, StopAlarmReceiver::class.java).apply {
            action = StopAlarmReceiver.ACTION_STOP
            if (token != null) putExtra(AlarmReceiver.EXTRA_TOKEN, token)
        }
        sendBroadcast(stopIntent)
    }

    companion object {
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PREVIEW = "extra_preview"
        const val EXTRA_TIME = "extra_time"
    }
}

@Composable
fun AlarmScreen(
    cameraLabel: String,
    title: String,
    preview: String,
    timestamp: Long,
    onOpenCamera: () -> Unit,
    onStop: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(timestamp) { timeFormat.format(Date(timestamp)) }

    val displayHeadline = when {
        title.isNotBlank() -> title
        preview.isNotBlank() -> preview
        else -> "Human detected"
    }

    val displaySubtitle = when {
        preview.isNotBlank() && preview != displayHeadline -> preview
        cameraLabel.isNotBlank() -> cameraLabel
        else -> "Front Door Camera"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: 🚨 CAMERA ALERT
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFC62828), RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.alarm_screen_badge),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(72.dp)
                )
            }

            // Body Card: Human detected & Camera name
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = displayHeadline,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp
                    )

                    Text(
                        text = displaySubtitle,
                        color = Color(0xFFB0BEC5),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.alarm_screen_triggered_at, formattedTime),
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Actions: [ MỞ CAMERA ] (Primary, Larger) & [ TẮT CẢNH BÁO ] (Red)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Primary: MỞ CAMERA
                Button(
                    onClick = onOpenCamera,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.btn_open_camera),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Secondary: TẮT CẢNH BÁO (Red)
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_stop_alarm),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
