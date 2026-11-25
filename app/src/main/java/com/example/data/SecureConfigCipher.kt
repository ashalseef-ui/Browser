package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive WireGuard private keys & endpoint configurations at rest using an
 * AndroidKeyStore-backed 256-bit AES-GCM key. Throws on encryption failure and returns null
 * on decryption failure instead of failing silently.
 */
object SecureConfigCipher {

  private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
  private const val KEY_ALIAS = "volt_wg_config_aes_v1"
  private const val PREFIX = "enc:v1:"
  private const val GCM_IV_BYTES = 12
  private const val GCM_TAG_BITS = 128

  fun encrypt(plaintext: String): String {
    if (plaintext.isBlank()) return ""
    if (plaintext.startsWith(PREFIX)) return plaintext
    val key = getOrCreateKeystoreKey() ?: throw IllegalStateException("AndroidKeyStore unavailable or key generation failed")
    return encryptWithKey(plaintext, key)
  }

  fun decrypt(stored: String): String? {
    if (stored.isBlank()) return ""
    if (!stored.startsWith(PREFIX)) return stored
    val key = getOrCreateKeystoreKey() ?: return null
    return runCatching { decryptWithKey(stored, key) }.getOrNull()
  }

  internal fun encryptWithKey(plaintext: String, key: SecretKey): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv ?: ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
    val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
    val combined = ByteArray(iv.size + ciphertext.size)
    System.arraycopy(iv, 0, combined, 0, iv.size)
    System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
    return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
  }

  internal fun decryptWithKey(stored: String, key: SecretKey): String {
    if (!stored.startsWith(PREFIX)) return stored
    val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
    require(combined.size > GCM_IV_BYTES) { "Ciphertext too short" }
    val iv = combined.copyOfRange(0, GCM_IV_BYTES)
    val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
  }

  private fun getOrCreateKeystoreKey(): SecretKey? {
    return runCatching {
      val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
      val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
      if (existing != null) return@runCatching existing

      val keyGenerator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
      val spec =
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
          )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build()
      keyGenerator.init(spec)
      keyGenerator.generateKey()
    }.getOrNull()
  }
}
