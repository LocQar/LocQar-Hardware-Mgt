package com.locqar.locker.hardware.controller

import com.locqar.locker.hardware.codec.OpenResponse
import com.locqar.locker.hardware.codec.PollResponse

/**
 * Hardware-only interface for locker control.
 * No knowledge of users, payments, or business logic.
 */
interface LockerController {

    /**
     * Open a specific lock on a station.
     * @param station Station number (1-based)
     * @param lock Lock number (1-12)
     * @return OpenResult indicating success/failure with details
     */
    suspend fun openDoor(station: Int, lock: Int): OpenResult

    /**
     * Poll all door states on a station.
     * @param station Station number (1-based)
     * @param mask 16-bit mask (default 0x0FFF for locks 1-12)
     * @return PollResult with per-door states or error
     */
    suspend fun pollStation(station: Int, mask: Int = 0x0FFF): PollResult

    /**
     * Safe open: open door, wait for confirmation (polling), then return.
     * @param station Station number
     * @param lock Lock number
     * @param confirmTimeoutMs How long to wait for open confirmation via polling
     * @return SafeOpenResult with detailed status
     */
    suspend fun safeOpenDoor(
        station: Int,
        lock: Int,
        confirmTimeoutMs: Long = 5000L
    ): SafeOpenResult

    /**
     * Check if the controller (serial) is connected.
     */
    val isConnected: Boolean
}

sealed class OpenResult {
    data class Success(val response: OpenResponse) : OpenResult()
    data class Failed(val response: OpenResponse) : OpenResult()
    data class Error(val message: String, val exception: Throwable? = null) : OpenResult()
    data object NotConnected : OpenResult()
    data object Timeout : OpenResult()
}

sealed class PollResult {
    data class Success(val response: PollResponse) : PollResult()
    data class Error(val message: String, val exception: Throwable? = null) : PollResult()
    data object NotConnected : PollResult()
    data object Timeout : PollResult()
}

sealed class SafeOpenResult {
    /** Door opened and confirmed open via polling. */
    data class Confirmed(val lock: Int, val openResponse: OpenResponse) : SafeOpenResult()
    /** Door open command succeeded but open not confirmed within timeout. */
    data class OpenNotConfirmed(val lock: Int) : SafeOpenResult()
    /** Door open command itself failed. */
    data class OpenFailed(val lock: Int, val reason: String) : SafeOpenResult()
    /** Not connected to hardware. */
    data object NotConnected : SafeOpenResult()
}
