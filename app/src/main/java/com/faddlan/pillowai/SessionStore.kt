package com.faddlan.pillowai

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Device(val id: String, val name: String, val photoPath: String?)

class SessionStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("pillow_session", Context.MODE_PRIVATE)

    val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    val deviceName: String
        get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    var email: String?
        get() = prefs.getString("email", null)
        set(value) {
            prefs.edit().putString("email", value).apply()
        }

    val accountKey: String
        get() = (email ?: "guest").lowercase().replace(Regex("[^a-z0-9]"), "_")

    fun devices(): List<Device> {
        val arr = JSONArray(prefs.getString("devices_$accountKey", "[]"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Device(o.getString("id"), o.getString("name"), o.optString("photo").ifEmpty { null })
        }
    }

    private fun saveDevices(list: List<Device>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("photo", it.photoPath ?: "")
            )
        }
        prefs.edit().putString("devices_$accountKey", arr.toString()).apply()
    }

    fun registerDevice() {
        val list = devices()
        if (list.none { it.id == deviceId }) {
            saveDevices(list + Device(deviceId, deviceName, null))
        }
    }

    fun photoPath(): String? =
        devices().firstOrNull { it.id == deviceId }?.photoPath?.takeIf { File(it).exists() }

    fun setPhoto(path: String?) {
        registerDevice()
        val updated = devices().map { d ->
            if (d.id == deviceId) {
                val old = d.photoPath
                if (old != null && old != path) File(old).delete()
                d.copy(photoPath = path)
            } else d
        }
        saveDevices(updated)
    }

    fun loadChat(): MutableList<Message> {
        val arr = JSONArray(prefs.getString("chat_$accountKey", "[]"))
        return MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Message(o.getString("role"), o.getString("text"))
        }
    }

    fun saveChat(list: List<Message>) {
        val arr = JSONArray()
        list.filter { !it.typing }.forEach {
            arr.put(JSONObject().put("role", it.role).put("text", it.text))
        }
        prefs.edit().putString("chat_$accountKey", arr.toString()).apply()
    }
}
