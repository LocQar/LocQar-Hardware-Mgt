package com.locqar.locker.hardware.controller

import com.locqar.locker.hardware.codec.WinnsenCodec
import com.locqar.locker.hardware.serial.SerialManager
import com.locqar.locker.hardware.serial.SerialNotConnectedException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * Real hardware implementation using RS485 serial.
 * All commands are serialized through a Mutex to prevent interleaving on the bus.
 */
class LockerControllerImpl(
    private val serialManager: SerialManager
) : LockerController {

    /** Single-command mutex: only one RS485 transaction at a time. */
    private val commandMutex = Mutex()

    /** Minimum delay between consecutive commands to avoid bus contention. */
    private val interCommandDelayMs = 50L

    private var lastCommandTime = 0L

    override val isConnected: Boolean
        get() = serialManager.isConnected

    override suspend fun openDoor(station: Int, lock: Int): OpenResult {
        if (!serialManager.isConnected) return OpenResult.NotConnected

        return commandMutex.withLock {
            enforceInterCommandDelay()
            try {
                val txData = WinnsenCodec.buildOpenCommand(station, lock)
                val rxData = serialManager.sendAndReceive(txData, WinnsenCodec.OPEN_RX_LEN)

                if (rxData == null) {
                    return@withLock OpenResult.Timeout
                }

                val response = WinnsenCodec.parseOpenResponse(rxData)
                    ?: return@withLock OpenResult.Error("Invalid response: ${WinnsenCodec.toHex(rxData)}")

                if (response.success) {
                    OpenResult.Success(response)
                } else {
                    OpenResult.Failed(response)
                }
            } catch (e: SerialNotConnectedException) {
                OpenResult.NotConnected
            } catch (e: IOException) {
                OpenResult.Error("IO error: ${e.message}", e)
            } catch (e: Exception) {
                OpenResult.Error("Unexpected error: ${e.message}", e)
            } finally {
                lastCommandTime = System.currentTimeMillis()
            }
        }
    }

    override suspend fun pollStation(station: Int, mask: Int): PollResult {
        if (!serialManager.isConnected) return PollResult.NotConnected

        return commandMutex.withLock {
            enforceInterCommandDelay()
            try {
                val txData = WinnsenCodec.buildPollCommand(station, mask)
                val rxData = serialManager.sendAndReceive(txData, WinnsenCodec.POLL_RX_LEN)

                if (rxData == null) {
                    return@withLock PollResult.Timeout
                }

                val response = WinnsenCodec.parsePollResponse(rxData)
                    ?: return@withLock PollResult.Error("Invalid response: ${WinnsenCodec.toHex(rxData)}")

                PollResult.Success(response)
            } catch (e: SerialNotConnectedException) {
                PollResult.NotConnected
            } catch (e: IOException) {
                PollResult.Error("IO error: ${e.message}", e)
            } catch (e: Exception) {
                PollResult.Error("Unexpected error: ${e.message}", e)
            } finally {
                lastCommandTime = System.currentTimeMillis()
            }
        }
    }

    override suspend fun safeOpenDoor(
        station: Int,
        lock: Int,
        confirmTimeoutMs: Long
    ): SafeOpenResult {
        if (!serialManager.isConnected) return SafeOpenResult.NotConnected

        // Step 1: Send open command
        val openResult = openDoor(station, lock)
        when (openResult) {
            is OpenResult.Success -> { /* continue to confirm */ }
            is OpenResult.Failed -> return SafeOpenResult.OpenFailed(lock, "Board returned status=00")
            is OpenResult.NotConnected -> return SafeOpenResult.NotConnected
            is OpenResult.Timeout -> return SafeOpenResult.OpenFailed(lock, "Open command timed out")
            is OpenResult.Error -> return SafeOpenResult.OpenFailed(lock, openResult.message)
        }

        // Step 2: Poll to confirm the door is physically open
        val confirmed = withTimeoutOrNull(confirmTimeoutMs) {
            while (true) {
                delay(500)
                val pollResult = pollStation(station)
                if (pollResult is PollResult.Success) {
                    if (WinnsenCodec.isLockOpen(pollResult.response.stateBits, lock)) {
                        return@withTimeoutOrNull true
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }

        return if (confirmed == true) {
            SafeOpenResult.Confirmed(lock, (openResult as OpenResult.Success).response)
        } else {
            SafeOpenResult.OpenNotConfirmed(lock)
        }
    }

    private suspend fun enforceInterCommandDelay() {
        val elapsed = System.currentTimeMillis() - lastCommandTime
        if (elapsed < interCommandDelayMs) {
            delay(interCommandDelayMs - elapsed)
        }
    }
}
