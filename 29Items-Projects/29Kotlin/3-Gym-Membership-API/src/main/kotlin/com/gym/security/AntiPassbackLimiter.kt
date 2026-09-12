package com.gym.security

import com.gym.domain.BadgeSharingDetectedException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface AntiPassbackLimiter {
    /**
     * Checks if a badge was scanned recently. If valid, records the scan timestamp.
     * If scanned within the anti-passback cooldown, throws BadgeSharingDetectedException.
     */
    fun validateAndRecordScan(badgeCode: String)
    fun reset(badgeCode: String)
}

class InMemoryAntiPassbackLimiter(
    private val cooldownMinutes: Long = 15
) : AntiPassbackLimiter {

    private val scanHistory = ConcurrentHashMap<String, Long>()

    override fun validateAndRecordScan(badgeCode: String) {
        val now = System.currentTimeMillis()
        val cooldownMs = TimeUnit.MINUTES.toMillis(cooldownMinutes)

        scanHistory.compute(badgeCode) { _, lastScanTime ->
            if (lastScanTime != null && (now - lastScanTime) < cooldownMs) {
                val remainingMs = cooldownMs - (now - lastScanTime)
                val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) + 1
                throw BadgeSharingDetectedException(badgeCode, remainingMinutes)
            }
            now
        }
    }

    override fun reset(badgeCode: String) {
        scanHistory.remove(badgeCode)
    }
}
