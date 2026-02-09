package com.locqar.locker.hardware.codec

/**
 * Winnsen RS485 lock control board protocol codec.
 *
 * Frame formats:
 *   Open lock TX (6 bytes): 90 06 05 <station> <lock> 03
 *   Open lock RX (7 bytes): 90 07 85 <station> <lock> <status> 03
 *   Poll state TX (7 bytes): 90 07 02 <station> <lowMask> <highMask> 03
 *   Poll state RX (7 bytes): 90 07 82 <station> <lowState> <highState> 03
 *
 * All values are unsigned bytes. Station and lock are 1-based.
 */
object WinnsenCodec {

    const val FRAME_START: Byte = 0x90.toByte()
    const val FRAME_END: Byte = 0x03

    // Command codes
    const val CMD_OPEN_TX: Byte = 0x05
    const val CMD_OPEN_RX: Byte = 0x85.toByte()
    const val CMD_POLL_TX: Byte = 0x02
    const val CMD_POLL_RX: Byte = 0x82.toByte()

    // Open status
    const val STATUS_SUCCESS: Byte = 0x01
    const val STATUS_FAILED: Byte = 0x00

    // Frame lengths
    const val OPEN_TX_LEN = 6
    const val OPEN_RX_LEN = 7
    const val POLL_TX_LEN = 7
    const val POLL_RX_LEN = 7

    const val MAX_DOORS = 12
    const val DEFAULT_POLL_MASK = 0x0FFF

    /**
     * Build the TX frame to open a lock.
     * @param station Station number (1-based)
     * @param lock Lock number (1-12)
     * @return 6-byte TX frame
     */
    fun buildOpenCommand(station: Int, lock: Int): ByteArray {
        require(station in 1..255) { "Station must be 1..255" }
        require(lock in 1..16) { "Lock must be 1..16" }
        return byteArrayOf(
            FRAME_START,
            0x06,
            CMD_OPEN_TX,
            station.toByte(),
            lock.toByte(),
            FRAME_END
        )
    }

    /**
     * Build the TX frame to poll lock open/closed states.
     * @param station Station number (1-based)
     * @param mask 16-bit mask where each bit represents a lock (bit 0 = lock 1)
     * @return 7-byte TX frame
     */
    fun buildPollCommand(station: Int, mask: Int = DEFAULT_POLL_MASK): ByteArray {
        require(station in 1..255) { "Station must be 1..255" }
        val lowMask = (mask and 0xFF).toByte()
        val highMask = ((mask shr 8) and 0xFF).toByte()
        return byteArrayOf(
            FRAME_START,
            0x07,
            CMD_POLL_TX,
            station.toByte(),
            lowMask,
            highMask,
            FRAME_END
        )
    }

    /**
     * Parse an Open response frame.
     * @return OpenResponse or null if frame is invalid
     */
    fun parseOpenResponse(data: ByteArray): OpenResponse? {
        if (data.size != OPEN_RX_LEN) return null
        if (data[0] != FRAME_START) return null
        if (data[1] != 0x07.toByte()) return null
        if (data[2] != CMD_OPEN_RX) return null
        if (data[6] != FRAME_END) return null

        return OpenResponse(
            station = data[3].toInt() and 0xFF,
            lock = data[4].toInt() and 0xFF,
            success = data[5] == STATUS_SUCCESS
        )
    }

    /**
     * Parse a Poll response frame.
     * @return PollResponse or null if frame is invalid
     */
    fun parsePollResponse(data: ByteArray): PollResponse? {
        if (data.size != POLL_RX_LEN) return null
        if (data[0] != FRAME_START) return null
        if (data[1] != 0x07.toByte()) return null
        if (data[2] != CMD_POLL_RX) return null
        if (data[6] != FRAME_END) return null

        val station = data[3].toInt() and 0xFF
        val lowState = data[4].toInt() and 0xFF
        val highState = data[5].toInt() and 0xFF
        val stateBits = lowState or (highState shl 8)

        return PollResponse(
            station = station,
            stateBits = stateBits,
            doorStates = decodeDoorStates(stateBits)
        )
    }

    /**
     * Decode 16-bit state into per-lock open/closed map.
     * bit=1 means OPEN, bit=0 means CLOSED.
     * Returns map of lockNumber (1-based) to isOpen.
     */
    fun decodeDoorStates(stateBits: Int, maxDoors: Int = MAX_DOORS): Map<Int, Boolean> {
        return (1..maxDoors).associateWith { lockNum ->
            (stateBits shr (lockNum - 1)) and 1 == 1
        }
    }

    /**
     * Check if a specific lock is open from state bits.
     */
    fun isLockOpen(stateBits: Int, lockNumber: Int): Boolean {
        require(lockNumber in 1..16) { "Lock must be 1..16" }
        return (stateBits shr (lockNumber - 1)) and 1 == 1
    }

    /**
     * Build a mask for specific locks.
     */
    fun buildMask(lockNumbers: List<Int>): Int {
        var mask = 0
        for (lock in lockNumbers) {
            require(lock in 1..16) { "Lock must be 1..16" }
            mask = mask or (1 shl (lock - 1))
        }
        return mask
    }

    /**
     * Format bytes as hex string for logging.
     */
    fun toHex(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }
}

data class OpenResponse(
    val station: Int,
    val lock: Int,
    val success: Boolean
)

data class PollResponse(
    val station: Int,
    val stateBits: Int,
    val doorStates: Map<Int, Boolean> // lockNumber -> isOpen
)
