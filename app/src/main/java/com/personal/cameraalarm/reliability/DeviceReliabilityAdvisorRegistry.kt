package com.personal.cameraalarm.reliability

import android.os.Build
import com.personal.cameraalarm.permission.ReadinessRepository

class DeviceReliabilityAdvisorRegistry(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) {
    val defaultAdvisor = AndroidDefaultAdvisor(readinessRepo, manufacturer, brand)
    val samsungAdvisor = SamsungAdvisor(readinessRepo, manufacturer, brand)
    val xiaomiAdvisor = XiaomiReliabilityAdvisor(readinessRepo, manufacturer, brand)
    val oppoAdvisor = OppoAdvisor(readinessRepo, manufacturer, brand)
    val vivoAdvisor = VivoAdvisor(readinessRepo, manufacturer, brand)
    val huaweiAdvisor = HuaweiAdvisor(readinessRepo, manufacturer, brand)

    val allAdvisors: List<DeviceReliabilityAdvisor> = listOf(
        samsungAdvisor,
        xiaomiAdvisor,
        oppoAdvisor,
        vivoAdvisor,
        huaweiAdvisor,
        defaultAdvisor
    )

    val activeAdvisor: DeviceReliabilityAdvisor = when {
        samsungAdvisor.isApplicable -> samsungAdvisor
        xiaomiAdvisor.isApplicable -> xiaomiAdvisor
        oppoAdvisor.isApplicable -> oppoAdvisor
        vivoAdvisor.isApplicable -> vivoAdvisor
        huaweiAdvisor.isApplicable -> huaweiAdvisor
        else -> defaultAdvisor
    }

    fun getAdvisorByKey(key: String): DeviceReliabilityAdvisor {
        return allAdvisors.find { it.oemKey == key } ?: activeAdvisor
    }
}
