package com.personal.cameraalarm

import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.personal.cameraalarm.app.CameraAlarmApp
import com.personal.cameraalarm.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class SelectedLanguageInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun notificationActionsFollowLanguageEvenWhileAlarmIsActive() = runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val container = (context.applicationContext as CameraAlarmApp).container
        val original = container.settingsRepository.current().language
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText() }
        shell("cmd appops set --uid ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        if (android.os.Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val token = "test-language"
        try {
            androidx.core.content.ContextCompat.startForegroundService(context,
                android.content.Intent(context, com.personal.cameraalarm.alarm.CameraAlarmService::class.java).apply {
                    action = com.personal.cameraalarm.alarm.CameraAlarmService.ACTION_START
                    putExtra(com.personal.cameraalarm.alarm.AlarmReceiver.EXTRA_TOKEN, token)
                    putExtra(com.personal.cameraalarm.alarm.CameraAlarmService.EXTRA_IS_TEST, true)
                })
            for (language in listOf("en", "vi")) {
                container.settingsRepository.setLanguage(language)
                compose.waitUntil(10_000) {
                    container.language.value == language && manager.activeNotifications.any { notification ->
                        notification.id == 1 && notification.notification.actions.map { it.title.toString() }.toSet() ==
                            setOf(container.getString(R.string.btn_stop_alarm), container.getString(R.string.btn_open_camera))
                    }
                }
            }
        } finally {
            context.sendBroadcast(com.personal.cameraalarm.alarm.StopAlarmReceiver.intent(context, token))
            compose.waitUntil(10_000) { manager.activeNotifications.none { it.id == 1 } }
            container.settingsRepository.setLanguage(original)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun selectedLanguageReachesActivityDialogAndMessages() = runBlocking {
        val container = (compose.activity.application as CameraAlarmApp).container
        val original = container.settingsRepository.current().language
        try {
            for (language in listOf("en", "vi")) {
                val before = compose.activity
                val previousLanguage = container.language.value
                container.settingsRepository.setLanguage(language)
                compose.waitUntil(5_000) { container.language.value == language }
                if (previousLanguage != language) compose.waitUntil(5_000) { compose.activity !== before }
                val activity = compose.activity
                val expected = container.localizedContext.getString(R.string.btn_stop_alarm)
                for (surface in listOf("dialog", "sheet", "snackbar")) {
                    compose.runOnUiThread {
                        activity.setContent {
                            MaterialTheme {
                                androidx.compose.runtime.key(surface) {
                                    when (surface) {
                                        "dialog" -> AlertDialog(onDismissRequest = {},
                                            title = { Text(stringResource(R.string.btn_stop_alarm)) },
                                            confirmButton = { Text(stringResource(R.string.btn_open_camera)) })
                                        "sheet" -> ModalBottomSheet(onDismissRequest = {}) {
                                            Text(stringResource(R.string.btn_stop_alarm))
                                            Text(stringResource(R.string.btn_open_camera))
                                        }
                                        else -> Snackbar(action = { Text(stringResource(R.string.btn_open_camera)) }) {
                                            Text(stringResource(R.string.btn_stop_alarm))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    compose.onNodeWithText(expected).assertIsDisplayed()
                    compose.onNodeWithText(container.getString(R.string.btn_open_camera)).assertIsDisplayed()
                }
                assertEquals(expected, activity.getString(R.string.btn_stop_alarm))
                assertEquals(container.localizedContext.getString(R.string.message_history_cleared),
                    container.getString(R.string.message_history_cleared))
            }
        } finally { container.settingsRepository.setLanguage(original) }
    }
}
