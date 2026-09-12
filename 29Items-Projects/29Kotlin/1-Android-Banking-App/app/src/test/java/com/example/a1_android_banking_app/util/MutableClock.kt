package com.example.a1_android_banking_app.util

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Clock whose time only moves when the test advances it (TECH-NOTES 3.6 #10). */
class MutableClock(private var nowMs: Long) : Clock() {

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(nowMs)

    fun advanceMs(ms: Long) {
        nowMs += ms
    }
}
