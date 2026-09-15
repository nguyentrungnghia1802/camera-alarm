package com.personal.cameraalarm

import com.personal.cameraalarm.reliability.*
import org.junit.Assert.*
import org.junit.Test

class DeviceReliabilityRegistryTest {

    @Test
    fun detectsSamsungDevices() {
        val registry = DeviceReliabilityAdvisorRegistry(manufacturer = "Samsung", brand = "samsung")
        assertTrue(registry.activeAdvisor is SamsungAdvisor)
        assertEquals("samsung", registry.activeAdvisor.oemKey)
        assertTrue(registry.activeAdvisor.isApplicable)
    }

    @Test
    fun detectsXiaomiDevices() {
        val registry = DeviceReliabilityAdvisorRegistry(manufacturer = "Xiaomi", brand = "Redmi")
        assertTrue(registry.activeAdvisor is XiaomiReliabilityAdvisor)
        assertEquals("xiaomi", registry.activeAdvisor.oemKey)
        assertTrue(registry.activeAdvisor.isApplicable)
    }

    @Test
    fun detectsOppoDevices() {
        val registry1 = DeviceReliabilityAdvisorRegistry(manufacturer = "OPPO", brand = "oppo")
        assertTrue(registry1.activeAdvisor is OppoAdvisor)
        assertEquals("oppo", registry1.activeAdvisor.oemKey)

        val registry2 = DeviceReliabilityAdvisorRegistry(manufacturer = "realme", brand = "realme")
        assertTrue(registry2.activeAdvisor is OppoAdvisor)
    }

    @Test
    fun detectsVivoDevices() {
        val registry = DeviceReliabilityAdvisorRegistry(manufacturer = "vivo", brand = "vivo")
        assertTrue(registry.activeAdvisor is VivoAdvisor)
        assertEquals("vivo", registry.activeAdvisor.oemKey)

        val registryIqoo = DeviceReliabilityAdvisorRegistry(manufacturer = "vivo", brand = "iQOO")
        assertTrue(registryIqoo.activeAdvisor is VivoAdvisor)
    }

    @Test
    fun detectsHuaweiDevices() {
        val registry = DeviceReliabilityAdvisorRegistry(manufacturer = "HUAWEI", brand = "honor")
        assertTrue(registry.activeAdvisor is HuaweiAdvisor)
        assertEquals("huawei", registry.activeAdvisor.oemKey)
    }

    @Test
    fun fallsBackToDefaultAdvisorForPixelAndOthers() {
        val registry = DeviceReliabilityAdvisorRegistry(manufacturer = "Google", brand = "google")
        assertTrue(registry.activeAdvisor is AndroidDefaultAdvisor)
        assertEquals("generic", registry.activeAdvisor.oemKey)
    }

    @Test
    fun retrievesAdvisorByKeyCorrectly() {
        val registry = DeviceReliabilityAdvisorRegistry(manufacturer = "Samsung", brand = "samsung")
        val xiaomi = registry.getAdvisorByKey("xiaomi")
        assertTrue(xiaomi is XiaomiReliabilityAdvisor)

        val oppo = registry.getAdvisorByKey("oppo")
        assertTrue(oppo is OppoAdvisor)

        val samsung = registry.getAdvisorByKey("samsung")
        assertTrue(samsung is SamsungAdvisor)

        val vivo = registry.getAdvisorByKey("vivo")
        assertTrue(vivo is VivoAdvisor)

        val huawei = registry.getAdvisorByKey("huawei")
        assertTrue(huawei is HuaweiAdvisor)

        val generic = registry.getAdvisorByKey("generic")
        assertTrue(generic is AndroidDefaultAdvisor)
    }
}
