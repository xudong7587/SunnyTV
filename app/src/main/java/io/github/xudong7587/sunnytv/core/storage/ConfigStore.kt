package io.github.xudong7587.sunnytv.core.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.xudong7587.sunnytv.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Account tokens and CD2 passwords use Android Keystore AES/GCM; no backup or plaintext exports. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("sunny-config", Context.MODE_PRIVATE)
    private val alias = "sunnytv-source-key-v1"
    val deviceId: String = prefs.getString("device", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("device", it).apply() }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val raw = Base64.decode(value, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return String(c.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }
    fun sources(): List<SourceConfig> {
        val a = JSONArray(prefs.getString("sources", "[]"))
        return (0 until a.length()).map { i ->
            val j = a.getJSONObject(i)
            SourceConfig(j.getString("id"), SourceKind.valueOf(j.getString("kind")), j.getString("name"),
                j.getString("base"), j.optString("user"), j.optString("username"),
                decrypt(j.getString("secret")))
        }
    }
    fun saveSources(sources: List<SourceConfig>) {
        val a = JSONArray()
        sources.forEach { s -> a.put(JSONObject().put("id",s.id).put("kind",s.kind.name)
            .put("name",s.name).put("base",s.baseUrl).put("user",s.userId).put("username",s.username)
            .put("secret",encrypt(s.secret))) }
        prefs.edit().putString("sources",a.toString()).apply()
    }
    fun resetSources() {
        val editor = prefs.edit().remove("sources")
        prefs.all.keys.filter { it.startsWith("pos:") }.forEach { editor.remove(it) }
        editor.apply()
    }
    fun removePositions(sourceId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("pos:$sourceId:") }.forEach { editor.remove(it) }
        editor.apply()
    }
    fun settings(): AppSettings = AppSettings(
        prefs.getBoolean("motion",false), prefs.getBoolean("hq",false), prefs.getBoolean("backdrop",true),
        prefs.getBoolean("resume",true), prefs.getBoolean("next",true), prefs.getBoolean("diag",false),
        prefs.getInt("seek",10), prefs.getBoolean("dark",true), prefs.getInt("accent",2).coerceIn(0,9),
        prefs.getString("artworkMode","Poster") ?: "Poster", prefs.getString("subtitle","default") ?: "default",
        prefs.getString("heroMode","random") ?: "random", prefs.getStringSet("heroLibraries",emptySet())?.toSet() ?: emptySet(),
        prefs.getBoolean("heroAll",true),prefs.getInt("heroInterval",8).coerceIn(3,60),prefs.getInt("uiScale",2).coerceIn(0,4),
        prefs.all.filterKeys {it.startsWith("libraryArtwork:")}.mapNotNull {(key,value)->
            (value as? String)?.takeIf {it in setOf("Poster","Thumb","Banner")}?.let {key.removePrefix("libraryArtwork:") to it}
        }.toMap()
    )
    fun saveSettings(s: AppSettings) {
        val libraryEditor=prefs.edit()
        s.libraryArtworkModes.forEach {(key,value)->libraryEditor.putString("libraryArtwork:$key",value)}
        libraryEditor.apply()
        prefs.edit().putBoolean("motion",s.reduceMotion).putBoolean("hq",s.highQualityArtwork)
        .putBoolean("backdrop",s.backdropEnabled).putBoolean("resume",s.showResume).putBoolean("next",s.showNextUp)
        .putBoolean("diag",s.diagnostics).putInt("seek",s.seekStepSeconds)
        .putBoolean("dark",s.darkTheme).putInt("accent",s.accentIndex).putString("artworkMode",s.artworkMode)
        .putString("subtitle",s.subtitlePreference).putString("heroMode",s.heroMode)
        .putStringSet("heroLibraries",s.heroLibraryKeys).putBoolean("heroAll",s.heroAllLibraries)
        .putInt("heroInterval",s.heroIntervalSeconds.coerceIn(3,60)).putInt("uiScale",s.uiScaleLevel.coerceIn(0,4)).apply() }
    fun position(key: String): Long = prefs.getLong("pos:$key",0)
    fun savePosition(key: String, ms: Long) { if(key.isNotEmpty()) prefs.edit().putLong("pos:$key",ms.coerceAtLeast(0)).apply() }
}
