package com.gym.service

import kotlin.test.*

class CapacityServiceTest {

    @Test
    fun testCapacityIncrementDecrement() {
        val capacityService = InMemoryCapacityService(maxLimit = 10)
        assertEquals(0, capacityService.currentOccupancy)
        assertEquals(10, capacityService.maxCapacity)
        assertFalse(capacityService.isAtCapacity())

        val afterInc1 = capacityService.incrementOccupancy()
        assertEquals(1, afterInc1)
        assertEquals(1, capacityService.currentOccupancy)

        val afterInc2 = capacityService.incrementOccupancy()
        assertEquals(2, afterInc2)

        val afterDec = capacityService.decrementOccupancy()
        assertEquals(1, afterDec)
        assertEquals(1, capacityService.currentOccupancy)
    }

    @Test
    fun testCapacityLimitReached() {
        val capacityService = InMemoryCapacityService(maxLimit = 2)
        assertFalse(capacityService.isAtCapacity())

        capacityService.incrementOccupancy()
        assertFalse(capacityService.isAtCapacity())

        capacityService.incrementOccupancy()
        assertTrue(capacityService.isAtCapacity())
    }

    @Test
    fun testDecrementNeverBelowZero() {
        val capacityService = InMemoryCapacityService(maxLimit = 5)
        assertEquals(0, capacityService.currentOccupancy)

        val dec = capacityService.decrementOccupancy()
        assertEquals(0, dec)
        assertEquals(0, capacityService.currentOccupancy)
    }

    @Test
    fun testSetMaxCapacity() {
        val capacityService = InMemoryCapacityService(maxLimit = 50)
        assertEquals(50, capacityService.maxCapacity)

        capacityService.setMaxCapacity(100)
        assertEquals(100, capacityService.maxCapacity)

        assertFailsWith<IllegalArgumentException> {
            capacityService.setMaxCapacity(0)
        }
        assertFailsWith<IllegalArgumentException> {
            capacityService.setMaxCapacity(-10)
        }
    }
}
