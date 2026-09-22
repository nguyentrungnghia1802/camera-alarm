package com.personal.cameraalarm.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.personal.cameraalarm.app.CameraAlarmApp
import java.util.Locale

/** Dialog windows also read Activity resources, outside Main's composition locals. */
open class LocalizedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CameraAlarmApp).container
        val createdLanguage = container.language.value
        lifecycleScope.launch {
            container.language.collect { language ->
                // Dialog ContextThemeWrappers cache Resources. Recreate the host so
                // existing windows and saved Compose state receive the new locale too.
                if (language != createdLanguage && !isFinishing && !isDestroyed) recreate()
            }
        }
    }

    override fun getResources(): Resources {
        val base = baseContext ?: return super.getResources()
        val language = (application as? CameraAlarmApp)?.container?.language?.value ?: "vi"
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
    }

    @Composable
    protected fun LocalizedContent(content: @Composable () -> Unit) {
        val app = application as CameraAlarmApp
        val language by app.container.language.collectAsState()
        val deviceConfiguration = LocalConfiguration.current
        val configuration = remember(language, deviceConfiguration) {
            Configuration(deviceConfiguration).apply { setLocale(Locale.forLanguageTag(language)) }
        }
        CompositionLocalProvider(
            LocalConfiguration provides configuration,
            LocalContext provides this,
            content = content
        )
    }
}
