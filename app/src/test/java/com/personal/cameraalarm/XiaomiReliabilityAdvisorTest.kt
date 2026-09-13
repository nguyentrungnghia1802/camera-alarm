package com.personal.cameraalarm

import com.personal.cameraalarm.reliability.XiaomiReliabilityAdvisor
import org.junit.Assert.*
import org.junit.Test

class XiaomiReliabilityAdvisorTest {

    @Test
    fun detectsXiaomiFamilyCorrectly() {
        assertTrue(XiaomiReliabilityAdvisor.isXiaomiFamily("Xiaomi", "Xiaomi"))
        assertTrue(XiaomiReliabilityAdvisor.isXiaomiFamily("xiaomi", "xiaomi"))
        assertTrue(XiaomiReliabilityAdvisor.isXiaomiFamily("Redmi", "Redmi"))
        assertTrue(XiaomiReliabilityAdvisor.isXiaomiFamily("redmi", "Xiaomi"))
        assertTrue(XiaomiReliabilityAdvisor.isXiaomiFamily("POCO", "POCO"))
        assertTrue(XiaomiReliabilityAdvisor.isXiaomiFamily("Poco", "Xiaomi"))
    }

    @Test
    fun nonXiaomiDevicesAreNotApplicable() {
        assertFalse(XiaomiReliabilityAdvisor.isXiaomiFamily("Google", "Pixel"))
        assertFalse(XiaomiReliabilityAdvisor.isXiaomiFamily("Samsung", "Galaxy"))
        assertFalse(XiaomiReliabilityAdvisor.isXiaomiFamily("Motorola", "Moto"))
        assertFalse(XiaomiReliabilityAdvisor.isXiaomiFamily("Sony", "Xperia"))
    }

    @Test
    fun nonXiaomiReportsGenericAdvisorName() {
        val nonXiaomi = XiaomiReliabilityAdvisor(
            manufacturer = "Google",
            brand = "google"
        )
        assertFalse(nonXiaomi.isApplicable)
        assertEquals("Generic Android", nonXiaomi.deviceFamilyName)
    }

    @Test
    fun xiaomiReportsXiaomiAdvisorName() {
        val xiaomi = XiaomiReliabilityAdvisor(
            manufacturer = "Xiaomi",
            brand = "Redmi"
        )
        assertTrue(xiaomi.isApplicable)
        assertEquals("Xiaomi / Redmi / POCO", xiaomi.deviceFamilyName)
    }
}
