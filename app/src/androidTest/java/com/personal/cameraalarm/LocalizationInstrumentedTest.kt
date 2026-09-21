package com.personal.cameraalarm

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.reliability.SamsungAdvisor
import com.personal.cameraalarm.reliability.XiaomiReliabilityAdvisor
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizationInstrumentedTest {
    private val baseContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ruleSettingsAndOemGuidanceResolveInEnglishAndVietnamese() {
        val english = localizedContext(Locale.ENGLISH)
        val vietnamese = localizedContext(Locale.forLanguageTag("vi"))
        val resourceIds = listOf(
            R.string.rule_error_name_blank,
            R.string.rule_error_keyword_duplicate,
            R.string.message_sound_preview_failed,
            R.string.message_history_cleared,
            R.string.reliability_battery_desc,
            R.string.reliability_settings_unavailable
        )

        resourceIds.forEach { resourceId ->
            val en = english.getString(resourceId)
            val vi = vietnamese.getString(resourceId)
            assertFalse(en.isBlank())
            assertFalse(vi.isBlank())
            assertNotEquals("Resource $resourceId fell back instead of localizing", en, vi)
        }

        val samsungEnglish = SamsungAdvisor(manufacturer = "Samsung", brand = "samsung")
            .getOemItems(english).first().title
        val samsungVietnamese = SamsungAdvisor(manufacturer = "Samsung", brand = "samsung")
            .getOemItems(vietnamese).first().title
        assertNotEquals(samsungEnglish, samsungVietnamese)

        val xiaomiEnglish = XiaomiReliabilityAdvisor(manufacturer = "Xiaomi", brand = "Redmi")
            .getOemItems(english).first().title
        val xiaomiVietnamese = XiaomiReliabilityAdvisor(manufacturer = "Xiaomi", brand = "Redmi")
            .getOemItems(vietnamese).first().title
        assertNotEquals(xiaomiEnglish, xiaomiVietnamese)
    }

    private fun localizedContext(locale: Locale): Context {
        val configuration = Configuration(baseContext.resources.configuration)
        configuration.setLocale(locale)
        return baseContext.createConfigurationContext(configuration)
    }
}
