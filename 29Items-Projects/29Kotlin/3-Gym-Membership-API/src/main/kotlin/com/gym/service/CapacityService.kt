package com.gym.service

import java.util.concurrent.atomic.AtomicInteger

interface CapacityService {
    val currentOccupancy: Int
    val maxCapacity: Int
    fun isAtCapacity(): Boolean
    fun incrementOccupancy(): Int
    fun decrementOccupancy(): Int
    fun setMaxCapacity(limit: Int)
}

class InMemoryCapacityService(
    private var maxLimit: Int = 250
) : CapacityService {

    private val current = AtomicInteger(0)

    override val currentOccupancy: Int
        get() = current.get()

    override val maxCapacity: Int
        get() = maxLimit

    override fun isAtCapacity(): Boolean = current.get() >= maxLimit

    override fun incrementOccupancy(): Int = current.incrementAndGet()

    override fun decrementOccupancy(): Int = current.updateAndGet { if (it > 0) it - 1 else 0 }

    override fun setMaxCapacity(limit: Int) {
        require(limit > 0) { "Capacity limit must be positive" }
        maxLimit = limit
    }
}
