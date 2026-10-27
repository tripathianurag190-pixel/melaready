package com.tripathitechnologies.melaready.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tripathitechnologies.melaready.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One encrypted, device-local workspace. No location or contacts are serialized here. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("melaready_private", Context.MODE_PRIVATE)
    private val alias = "melaready_workspace_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): Workspace {
        val payload = prefs.getString("workspace", null) ?: return Workspace()
        val parts = payload.split(':'); require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return decode(JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)))
    }
    fun save(w: Workspace) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(encode(w).toString().toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(prefs.edit().putString("workspace", value).commit()) { "Local save failed" }
    }
    fun clear() { check(prefs.edit().clear().commit()) { "Local clear failed" } }
    private fun encode(w: Workspace) = JSONObject().apply {
        put("version", 2); put("hindi", w.hindi)
        put("gatewayCompleted", w.gatewayCompleted); put("serverUrl", w.serverUrl)
        w.event?.let { e -> put("event", JSONObject().put("name", e.name).put("venue", e.venue).put("attendance", e.attendance).put("sample", e.sample)) }
        put("members", JSONArray().apply { w.members.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("role", it.role).put("zone", it.zone)) } })
        put("answers", JSONArray(w.answers)); put("checks", JSONArray(w.checks.toList()))
        put("incidents", JSONArray().apply { w.incidents.forEach { put(JSONObject().put("id", it.id).put("note", it.note).put("zone", it.zone).put("created", it.created).put("status", it.status.name)) } })
        put("learning", JSONObject().apply { w.learning.forEach { (k,v) -> put(k,v) } })
    }
    private fun decode(j: JSONObject): Workspace {
        require(j.getInt("version") in 1..2)
        val event = j.optJSONObject("event")?.let { Event(it.getString("name"), it.getString("venue"), it.getInt("attendance"), it.optBoolean("sample")) }
        fun array(name: String) = j.optJSONArray(name) ?: JSONArray()
        val members = array("members"); val incidents = array("incidents"); val answers = array("answers"); val checks = array("checks")
        return Workspace(event,
            (0 until members.length()).map { i -> members.getJSONObject(i).let { Member(it.getString("id"), it.getString("name"), it.getString("role"), it.getString("zone")) } },
            (0 until answers.length()).map { answers.getInt(it) }, (0 until checks.length()).map { checks.getInt(it) }.toSet(),
            (0 until incidents.length()).map { i -> incidents.getJSONObject(i).let { Incident(it.getString("id"), it.getString("note"), it.getString("zone"), it.getLong("created"), IncidentStatus.valueOf(it.getString("status"))) } }, j.optBoolean("hindi"),
            j.optJSONObject("learning")?.let { o -> o.keys().asSequence().associateWith { o.getInt(it) } } ?: emptyMap(),
            j.optBoolean("gatewayCompleted", false),
            j.optString("serverUrl", "http://10.0.2.2:8080"))
    }
}
