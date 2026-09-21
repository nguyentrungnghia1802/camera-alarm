package com.personal.cameraalarm

import android.content.Intent
import com.personal.cameraalarm.boot.BootActionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootActionPolicyTest {
    @Test
    fun acceptsOnlyDocumentedBootAndPackageReplacementActions() {
        assertTrue(BootActionPolicy.isSupported(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(BootActionPolicy.isSupported(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(BootActionPolicy.isSupported("android.intent.action.QUICKBOOT_POWERON"))
        assertTrue(BootActionPolicy.isSupported("com.htc.intent.action.QUICKBOOT_POWERON"))
        assertFalse(BootActionPolicy.isSupported("com.attacker.RECONCILE"))
        assertFalse(BootActionPolicy.isSupported(null))
    }
}
