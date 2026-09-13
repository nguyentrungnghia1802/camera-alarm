package com.personal.cameraalarm.reliability

import android.content.Context
import android.content.Intent

enum class ReliabilityStatus {
    READY,
    MISSING,
    UNKNOWN,
    USER_CONFIRMATION_REQUIRED,
    NOT_APPLICABLE
}

data class ReliabilityItem(
    val id: String,
    val title: String,
    val description: String,
    val status: ReliabilityStatus,
    val isOemSpecific: Boolean = false,
    val actionLabel: String? = null,
    val actionIntent: Intent? = null,
    val userInstruction: String? = null
)

interface DeviceReliabilityAdvisor {
    val isApplicable: Boolean
    val deviceFamilyName: String
    fun getReliabilityItems(context: Context): List<ReliabilityItem>
}
