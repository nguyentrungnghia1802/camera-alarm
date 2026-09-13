package com.personal.cameraalarm

import com.personal.cameraalarm.permission.AlarmVolumeStatus
import org.junit.Assert.*
import org.junit.Test

class AlarmVolumeStatusTest {
    @Test fun zeroIsWarnedWhenDeviceAllowsIt() {
        val status = AlarmVolumeStatus(current = 0, minimum = 0, maximum = 7)
        assertFalse(status.isNonZero)
        assertTrue(status.canReachZero)
        assertEquals("Alarm volume is zero", status.warning)
    }
    @Test fun emulatorMinimumOneIsReportedWithoutFakingZero() {
        val status = AlarmVolumeStatus(current = 1, minimum = 1, maximum = 7)
        assertTrue(status.isNonZero)
        assertFalse(status.canReachZero)
        assertNull(status.warning)
    }
}
