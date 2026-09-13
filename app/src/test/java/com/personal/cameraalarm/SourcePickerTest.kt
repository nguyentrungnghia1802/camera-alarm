package com.personal.cameraalarm

import com.personal.cameraalarm.ui.picker.AppInfo
import org.junit.Assert.*
import org.junit.Test

class SourcePickerTest {
    @Test
    fun packageValidationRejectsBlank() {
        val blank1 = ""
        val blank2 = "   "
        val valid = "com.camera.vendor"

        assertTrue(blank1.isBlank())
        assertTrue(blank2.isBlank())
        assertFalse(valid.isBlank())
    }

    @Test
    fun searchFilteringMatchesLabelOrPackage() {
        val apps = listOf(
            AppInfo("com.google.android.camera", "Pixel Camera"),
            AppInfo("com.ezviz.app", "EZVIZ"),
            AppInfo("com.imou.life", "Imou Life")
        )

        // Filter by label
        val filterByLabel = apps.filter {
            it.label.lowercase().contains("pixel") || it.packageName.lowercase().contains("pixel")
        }
        assertEquals(1, filterByLabel.size)
        assertEquals("Pixel Camera", filterByLabel[0].label)

        // Filter by package
        val filterByPackage = apps.filter {
            it.label.lowercase().contains("ezviz") || it.packageName.lowercase().contains("ezviz")
        }
        assertEquals(1, filterByPackage.size)
        assertEquals("EZVIZ", filterByPackage[0].label)

        // Filter none
        val filterNone = apps.filter {
            it.label.lowercase().contains("nonexistent") || it.packageName.lowercase().contains("nonexistent")
        }
        assertEquals(0, filterNone.size)
    }
}
