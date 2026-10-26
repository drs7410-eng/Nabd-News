package com.nabd.news

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

class SecureSettings(context: Context) {
    private val prefs = context.getSharedPreferences("nabd_settings", Context.MODE_PRIVATE)
    private val secrets = context.getSharedPreferences("nabd_vault", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("nabd.credentials.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("nabd.credentials.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveKey(provider: String, value: String) {
        require(provider == "OpenAI" || provider == "Gemini")
        require(value.trim().isNotBlank()) { "أدخل المفتاح أولًا" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        check(secrets.edit().putString(provider, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).commit())
        set("tested_$provider", "")
        set("testState_$provider", "not_tested")
    }
    fun getKey(provider: String): String? {
        val value = secrets.getString(provider, null) ?: return null
        return try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { throw IllegalStateException("تعذر فك المفتاح المحلي. احذفه وأدخله مجددًا.") }
    }
    fun hasKey(provider: String) = secrets.contains(provider)
    fun deleteKey(provider: String) {
        check(secrets.edit().remove(provider).commit())
        set("tested_$provider", ""); set("testState_$provider", "not_tested")
    }
    fun get(name: String, default: String = "") = prefs.getString(name, default) ?: default
    fun set(name: String, value: String) { prefs.edit().putString(name, value).apply() }
    fun enabled(provider: String) = get("enabled_$provider", "true") == "true"
    fun model(provider: String, op: String = "") = get("model_${provider}_$op").ifBlank { get("model_$provider") }
    fun defaultProvider() = get("defaultProvider", if (hasKey("OpenAI")) "OpenAI" else "Gemini")
}
