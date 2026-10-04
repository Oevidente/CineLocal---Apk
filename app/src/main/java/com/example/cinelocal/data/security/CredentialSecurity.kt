package com.example.cinelocal.data.security

import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CredentialSecurity {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "CineLocalCredentialsKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PREFIX = "enc:v1:"

    @Synchronized
    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return plainText
        if (plainText.startsWith(PREFIX)) return plainText
        return try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (_: Exception) {
            val encoded = Base64.getEncoder().encodeToString(plainText.toByteArray(Charsets.UTF_8))
            "obf:$encoded"
        }
    }

    @Synchronized
    fun decrypt(cipherText: String): String {
        if (cipherText.isBlank()) return cipherText
        if (cipherText.startsWith(PREFIX)) {
            return try {
                val raw = cipherText.removePrefix(PREFIX)
                val combined = Base64.getDecoder().decode(raw)
                if (combined.size < GCM_IV_LENGTH) return cipherText
                val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
                val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
                val key = getOrCreateSecretKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            } catch (_: Exception) {
                cipherText
            }
        }
        if (cipherText.startsWith("obf:")) {
            return try {
                val raw = cipherText.removePrefix("obf:")
                String(Base64.getDecoder().decode(raw), Charsets.UTF_8)
            } catch (_: Exception) {
                cipherText
            }
        }
        return cipherText
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGen = KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGen.init(spec)
            return keyGen.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }
}
