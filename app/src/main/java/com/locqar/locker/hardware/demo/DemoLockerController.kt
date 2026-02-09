package com.locqar.locker.hardware.demo

import com.locqar.locker.hardware.codec.OpenResponse
import com.locqar.locker.hardware.codec.PollResponse
import com.locqar.locker.hardware.codec.WinnsenCodec
import com.locqar.locker.hardware.controller.*
import kotlinx.coroutines.delay

/**
 * Simulated hardware controller for demo/testing without RS485 hardware.
 * Simulates open/close behavior with realistic timing.
 */
class DemoLockerController : LockerController {

    /** Simulated door states: lockNumber -> isOpen */
    private val doorStates = mutableMapOf<Int, Boolean>().apply {
        for (i in 1..WinnsenCodec.MAX_DOORS) put(i, false)
    }

    /** Tracks which doors have been opened to auto-close after a delay */
    private val openTimestamps = mutableMapOf<Int, Long>()

    override val isConnected: Boolean = true

    override suspend fun openDoor(station: Int, lock: Int): OpenResult {
        delay(80) // Simulate RS485 round-trip
        if (lock !in 1..WinnsenCodec.MAX_DOORS) {
            return OpenResult.Failed(OpenResponse(station, lock, false))
        }
        doorStates[lock] = true
        openTimestamps[lock] = System.currentTimeMillis()
        return OpenResult.Success(OpenResponse(station, lock, true))
    }

    override suspend fun pollStation(station: Int, mask: Int): PollResult {
        delay(60) // Simulate RS485 round-trip

        // Auto-close doors that have been open for > 8 seconds (simulate user closing)
        val now = System.currentTimeMillis()
        openTimestamps.entries.removeAll { (lock, openTime) ->
            if (now - openTime > 8000) {
                doorStates[lock] = false
                true
            } else {
                false
            }
        }

        var stateBits = 0
        for ((lock, isOpen) in doorStates) {
            if (isOpen) {
                stateBits = stateBits or (1 shl (lock - 1))
            }
        }

        return PollResult.Success(
            PollResponse(
                station = station,
                stateBits = stateBits,
                doorStates = doorStates.toMap()
            )
        )
    }

    override suspend fun safeOpenDoor(
        station: Int,
        lock: Int,
        confirmTimeoutMs: Long
    ): SafeOpenResult {
        val openResult = openDoor(station, lock)
        if (openResult !is OpenResult.Success) {
            return SafeOpenResult.OpenFailed(lock, "Demo open failed")
        }
        // In demo, the door is immediately "open"
        delay(200) // small delay to simulate poll confirm
        return SafeOpenResult.Confirmed(lock, openResult.response)
    }

    /** Manually set a door state (for testing UI). */
    fun setDoorState(lock: Int, isOpen: Boolean) {
        doorStates[lock] = isOpen
        if (isOpen) {
            openTimestamps[lock] = System.currentTimeMillis()
        } else {
            openTimestamps.remove(lock)
        }
    }

    /** Manually close a door (simulate user action). */
    fun closeDoor(lock: Int) {
        doorStates[lock] = false
        openTimestamps.remove(lock)
    }
}
