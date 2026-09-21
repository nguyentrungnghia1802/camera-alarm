package com.personal.cameraalarm

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.boot.BootReceiver
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestSecurityInstrumentedTest {
    @Test
    fun bootReceiverIsNotExportedToThirdPartyApps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getReceiverInfo(
            ComponentName(context, BootReceiver::class.java),
            0
        )
        assertFalse(info.exported)
    }
}
