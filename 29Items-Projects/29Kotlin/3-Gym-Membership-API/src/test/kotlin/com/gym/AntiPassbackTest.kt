package com.gym

import com.gym.domain.BadgeSharingDetectedException
import com.gym.security.InMemoryAntiPassbackLimiter
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AntiPassbackTest {

    @Test
    fun testFirstBadgeScanSucceeds() {
        val limiter = InMemoryAntiPassbackLimiter(cooldownMinutes = 15)
        limiter.validateAndRecordScan("BADGE-VIP-999")
    }

    @Test
    fun testImmediateSecondBadgeScanThrowsAntiPassbackException() {
        val limiter = InMemoryAntiPassbackLimiter(cooldownMinutes = 15)
        limiter.validateAndRecordScan("BADGE-VIP-888")

        assertFailsWith<BadgeSharingDetectedException> {
            limiter.validateAndRecordScan("BADGE-VIP-888")
        }
    }

    @Test
    fun testResetAllowsReScan() {
        val limiter = InMemoryAntiPassbackLimiter(cooldownMinutes = 15)
        limiter.validateAndRecordScan("BADGE-VIP-777")
        limiter.reset("BADGE-VIP-777")
        limiter.validateAndRecordScan("BADGE-VIP-777")
    }
}
