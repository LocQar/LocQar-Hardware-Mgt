package com.locqar.locker.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.locqar.locker.data.db.entity.*
import com.locqar.locker.data.repository.LockerRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Export utilities for test reports, logs CSV, and configuration JSON.
 * Uses MediaStore for Downloads/LocQarLocker directory.
 */
object ExportUtil {

    private const val SUBFOLDER = "LocQarLocker"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Export test report as JSON to Downloads.
     */
    fun exportTestReport(
        context: Context,
        stationNumber: Int,
        maxDoors: Int,
        results: List<TestLockResult>
    ): Uri? {
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("timestamp", dateFormat.format(Date()))
            put("station", stationNumber)
            put("maxDoors", maxDoors)
            put("results", JSONArray().apply {
                results.forEach { r ->
                    put(JSONObject().apply {
                        put("lockNumber", r.lockNumber)
                        put("openCommandSent", r.openCommandSent)
                        put("openSuccess", r.openSuccess)
                        put("openConfirmed", r.openConfirmed)
                        put("closedAfterTest", r.closedAfterTest)
                        put("errorMessage", r.errorMessage ?: JSONObject.NULL)
                        put("durationMs", r.durationMs)
                    })
                }
            })
        }

        val filename = "test_report_${fileDateFormat.format(Date())}.json"
        return writeToDownloads(context, filename, "application/json", json.toString(2))
    }

    /**
     * Export event logs as CSV to Downloads.
     */
    fun exportLogsCsv(
        context: Context,
        events: List<EventLogEntity>
    ): Uri? {
        val sb = StringBuilder()
        sb.appendLine("id,timestamp,eventType,severity,source,doorId,lockNumber,stationNumber,message,details,synced")
        events.forEach { e ->
            sb.appendLine(
                "${e.id},${dateFormat.format(Date(e.timestamp))},${csvEscape(e.eventType)},${csvEscape(e.severity)}," +
                "${csvEscape(e.source)},${e.doorId ?: ""},${e.lockNumber ?: ""},${e.stationNumber ?: ""}," +
                "${csvEscape(e.message)},${csvEscape(e.details ?: "")},${e.synced}"
            )
        }

        val filename = "logs_${fileDateFormat.format(Date())}.csv"
        return writeToDownloads(context, filename, "text/csv", sb.toString())
    }

    /**
     * Export configuration JSON (door mappings + settings, no admin password).
     */
    suspend fun exportConfig(
        context: Context,
        repository: LockerRepository
    ): Uri? {
        val board = repository.getFirstBoard()
        val doors = repository.getAllDoors()
        val settings = repository.getAllSettings()

        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", dateFormat.format(Date()))

            put("locker", JSONObject().apply {
                put("name", settings[SettingsKeys.LOCKER_NAME] ?: "")
                put("helpPhone", settings[SettingsKeys.HELP_PHONE] ?: "")
            })

            if (board != null) {
                put("board", JSONObject().apply {
                    put("stationNumber", board.stationNumber)
                    put("maxDoors", board.maxDoors)
                })
            }

            put("doors", JSONArray().apply {
                doors.forEach { d ->
                    put(JSONObject().apply {
                        put("lockNumber", d.lockNumber)
                        put("doorLabel", d.doorLabel)
                        put("enabled", d.enabled)
                    })
                }
            })

            put("settings", JSONObject().apply {
                settings.forEach { (key, value) ->
                    // Never export admin password hash
                    if (key != SettingsKeys.ADMIN_PASSWORD_HASH && key != SettingsKeys.ADMIN_PASSWORD_CHANGED) {
                        put(key, value)
                    }
                }
            })
        }

        val filename = "config_${fileDateFormat.format(Date())}.json"
        return writeToDownloads(context, filename, "application/json", json.toString(2))
    }

    /**
     * Import configuration from JSON string.
     * Returns a summary of what was imported.
     */
    suspend fun importConfig(
        repository: LockerRepository,
        jsonStr: String
    ): ImportResult {
        return try {
            val json = JSONObject(jsonStr)
            val version = json.optInt("schemaVersion", 1)
            var doorsImported = 0
            var settingsImported = 0

            // Import board
            json.optJSONObject("board")?.let { boardJson ->
                val station = boardJson.getInt("stationNumber")
                val maxDoors = boardJson.optInt("maxDoors", 12)
                repository.saveBoard(
                    BoardEntity(stationNumber = station, maxDoors = maxDoors)
                )
            }

            // Import doors
            json.optJSONArray("doors")?.let { doorsArray ->
                val board = repository.getFirstBoard()
                if (board != null) {
                    repository.deleteAllDoors()
                    for (i in 0 until doorsArray.length()) {
                        val doorJson = doorsArray.getJSONObject(i)
                        repository.saveDoor(
                            DoorEntity(
                                boardId = board.id,
                                lockNumber = doorJson.getInt("lockNumber"),
                                doorLabel = doorJson.getString("doorLabel"),
                                enabled = doorJson.optBoolean("enabled", true)
                            )
                        )
                        doorsImported++
                    }
                }
            }

            // Import settings (skip password-related)
            json.optJSONObject("settings")?.let { settingsJson ->
                val keys = settingsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key != SettingsKeys.ADMIN_PASSWORD_HASH && key != SettingsKeys.ADMIN_PASSWORD_CHANGED) {
                        repository.setSetting(key, settingsJson.getString(key))
                        settingsImported++
                    }
                }
            }

            // Import locker details
            json.optJSONObject("locker")?.let { lockerJson ->
                lockerJson.optString("name").takeIf { it.isNotBlank() }?.let {
                    repository.setSetting(SettingsKeys.LOCKER_NAME, it)
                }
                lockerJson.optString("helpPhone").takeIf { it.isNotBlank() }?.let {
                    repository.setSetting(SettingsKeys.HELP_PHONE, it)
                }
            }

            ImportResult.Success(doorsImported, settingsImported)
        } catch (e: Exception) {
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    /**
     * Write content to a file using SAF (user-chosen folder).
     */
    fun writeToSafUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray())
                os.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeToDownloads(context: Context, filename: String, mimeType: String, content: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

        resolver.openOutputStream(uri)?.use { os ->
            os.write(content.toByteArray())
            os.flush()
        }

        return uri
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}

data class TestLockResult(
    val lockNumber: Int,
    val openCommandSent: Boolean = false,
    val openSuccess: Boolean = false,
    val openConfirmed: Boolean = false,
    val closedAfterTest: Boolean = false,
    val errorMessage: String? = null,
    val durationMs: Long = 0
)

sealed class ImportResult {
    data class Success(val doorsImported: Int, val settingsImported: Int) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
