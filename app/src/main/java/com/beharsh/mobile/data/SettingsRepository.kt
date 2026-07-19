package com.beharsh.mobile.data

import android.content.Context
import com.beharsh.mobile.model.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file: File get() = File(context.filesDir, "settings.json")

    fun load(): Settings {
        if (!file.exists()) return Settings()
        return try {
            json.decodeFromString(file.readText())
        } catch (_: Exception) {
            Settings()
        }
    }

    fun save(settings: Settings) {
        val tmp = File(context.filesDir, "settings.json.tmp")
        tmp.writeText(json.encodeToString(settings))
        tmp.renameTo(file)
    }
}
