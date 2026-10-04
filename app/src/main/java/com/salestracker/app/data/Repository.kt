package com.salestracker.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Stores everything in one JSON file in the app's private storage.
 * The data set for one salesperson is small, so a single file is simple and fast.
 */
class Repository(context: Context) {
    private val file = File(context.filesDir, "sales_data.json")
    private val lock = Any()
    private var lastId = 0L

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private fun load(): AppData {
        if (!file.exists()) return AppData()
        return try {
            AppData.fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            // Keep the unreadable file so nothing is silently lost, then start fresh.
            file.renameTo(File(file.parentFile, "sales_data.corrupt-${System.currentTimeMillis()}.json"))
            AppData()
        }
    }

    fun update(transform: (AppData) -> AppData) {
        synchronized(lock) {
            val next = transform(_data.value)
            _data.value = next
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(next.toJson().toString())
            tmp.renameTo(file)
        }
    }

    companion object {
        /**
         * Read-only snapshot of the saved data, for background work such as reminders
         * where the app's screens aren't running. Never modifies the file.
         */
        fun readSnapshot(context: Context): AppData {
            val f = File(context.filesDir, "sales_data.json")
            return try {
                if (f.exists()) AppData.fromJson(JSONObject(f.readText())) else AppData()
            } catch (e: Exception) {
                AppData()
            }
        }
    }

    fun newId(): Long = synchronized(lock) {
        val now = System.currentTimeMillis()
        lastId = if (now > lastId) now else lastId + 1
        lastId
    }
}
