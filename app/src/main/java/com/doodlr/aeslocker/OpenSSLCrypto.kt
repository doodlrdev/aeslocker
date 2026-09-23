package com.doodlr.aeslocker

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AESLocker's own authenticated file format ("AESLOCK1") — replaces the
 * earlier plain-OpenSSL-CBC-compatible format now that OpenSSL CLI
 * compatibility is no longer required (no installed users yet).
 *
 * Layout:
 *    8 bytes  magic        "AESLOCK1"
 *   16 bytes  salt         (random, per file)
 *   16 bytes  IV           (random, per file)
 *    N bytes  ciphertext   (AES-256-CBC, PKCS7 padded)
 *   32 bytes  HMAC-SHA256  over (magic || salt || IV || ciphertext)
 *
 * Key derivation: PBKDF2-HMAC-SHA512, 600,000 iterations, 64-byte output,
 * split into a 32-byte AES key and a separate 32-byte HMAC key from one
 * password + one PBKDF2 call — two distinct keys for two distinct
 * cryptographic purposes.
 *
 * The HMAC covers header + ciphertext, so any bit-flip — accidental
 * corruption or deliberate tampering — is detected before any plaintext
 * is trusted. This replaces the previous approach of inferring "correct
 * password" purely from whether PKCS7 padding happened to validate after
 * decryption, which is a weak signal related to the padding-oracle attack
 * class against unauthenticated CBC.
 *
 * A companion CLI script (published in the project's GitHub repo, and
 * downloadable from within the app) implements this exact same format
 * using only `openssl` + `dd`, so files remain recoverable even if the
 * app itself becomes unavailable.
 */
object OpenSSLCrypto {

    private val MAGIC = "AESLOCK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 16
    private const val IV_LEN = 16
    private const val HMAC_LEN = 32
    private const val HEADER_LEN = 8 + SALT_LEN + IV_LEN // magic + salt + iv = 40
    private const val OVERHEAD = HEADER_LEN + HMAC_LEN    // 72

    // 600,000 matches current OWASP guidance for PBKDF2 (as of 2026).
    private const val PBKDF2_ITERATIONS = 600000
    private const val DERIVED_KEY_MATERIAL_BYTES = 64 // 32 (AES key) + 32 (HMAC key)

    private const val HMAC_ALGO = "HmacSHA256"

    fun encrypt(
        inputStream: InputStream,
        outputStream: OutputStream,
        password: CharArray,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val (aesKey, hmacKey) = deriveKeys(password, salt)

        val mac = Mac.getInstance(HMAC_ALGO).apply { init(SecretKeySpec(hmacKey, HMAC_ALGO)) }

        outputStream.write(MAGIC); mac.update(MAGIC)
        outputStream.write(salt); mac.update(salt)
        outputStream.write(iv); mac.update(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))

        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var totalProcessed = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val output = cipher.update(buffer, 0, bytesRead)
            if (output != null && output.isNotEmpty()) {
                outputStream.write(output)
                mac.update(output)
            }
            totalProcessed += bytesRead
            if (totalBytes > 0) onProgress(totalProcessed.toFloat() / totalBytes.toFloat())
        }
        val finalBytes = cipher.doFinal()
        if (finalBytes != null && finalBytes.isNotEmpty()) {
            outputStream.write(finalBytes)
            mac.update(finalBytes)
        }

        outputStream.write(mac.doFinal())
        onProgress(1.0f)
    }

    /**
     * Verifies the password AND full file integrity via the HMAC tag — a
     * real cryptographic check, not an inference from padding validity.
     *
     * NOTE: signature change from the previous version — this now needs
     * `totalBytes` (the exact encrypted file size) so it can locate the
     * ciphertext/tag boundary without seeking. Caller sites that invoke
     * validatePassword() will need updating to pass this through; happy to
     * do that once I can see where it's called from in MainActivity.kt.
     */
    fun validatePassword(inputStream: InputStream, password: CharArray, totalBytes: Long): Boolean {
        return try {
            if (totalBytes < OVERHEAD) return false
            val ciphertextLen = totalBytes - OVERHEAD

            val header = ByteArray(8)
            if (inputStream.read(header) != 8 || !header.contentEquals(MAGIC)) return false

            val salt = ByteArray(SALT_LEN)
            if (inputStream.read(salt) != SALT_LEN) return false

            val iv = ByteArray(IV_LEN)
            if (inputStream.read(iv) != IV_LEN) return false

            val (_, hmacKey) = deriveKeys(password, salt)
            val mac = Mac.getInstance(HMAC_ALGO).apply { init(SecretKeySpec(hmacKey, HMAC_ALGO)) }
            mac.update(MAGIC); mac.update(salt); mac.update(iv)

            var remaining = ciphertextLen
            val buffer = ByteArray(64 * 1024)
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val n = inputStream.read(buffer, 0, toRead)
                if (n == -1) return false
                mac.update(buffer, 0, n)
                remaining -= n
            }

            val expectedTag = ByteArray(HMAC_LEN)
            if (inputStream.read(expectedTag) != HMAC_LEN) return false

            MessageDigest.isEqual(mac.doFinal(), expectedTag)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Decrypts the file. Callers are expected to have already run
     * validatePassword() over an independent stream from the same file
     * before calling decrypt(), so the HMAC has already been verified by
     * the time this runs — this function trusts that and just decrypts.
     */
    fun decrypt(
        inputStream: InputStream,
        outputStream: OutputStream,
        password: CharArray,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) {
        check(totalBytes >= OVERHEAD) { "File too small to be a valid AESLocker file." }
        val ciphertextLen = totalBytes - OVERHEAD

        val header = ByteArray(8)
        check(inputStream.read(header) == 8 && header.contentEquals(MAGIC)) { "Invalid file format." }

        val salt = ByteArray(SALT_LEN)
        inputStream.read(salt)
        val iv = ByteArray(IV_LEN)
        inputStream.read(iv)

        val (aesKey, _) = deriveKeys(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))

        val buffer = ByteArray(64 * 1024)
        var remaining = ciphertextLen
        var totalProcessed = 0L

        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            val bytesRead = inputStream.read(buffer, 0, toRead)
            if (bytesRead == -1) break
            val output = cipher.update(buffer, 0, bytesRead)
            if (output != null && output.isNotEmpty()) outputStream.write(output)
            remaining -= bytesRead
            totalProcessed += bytesRead
            if (totalBytes > 0) onProgress(totalProcessed.toFloat() / totalBytes.toFloat())
        }
        val finalBytes = cipher.doFinal()
        if (finalBytes != null && finalBytes.isNotEmpty()) outputStream.write(finalBytes)
        onProgress(1.0f)
    }

    private fun deriveKeys(password: CharArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, DERIVED_KEY_MATERIAL_BYTES * 8)
        val derived = factory.generateSecret(spec).encoded
        val aesKey = derived.copyOfRange(0, 32)
        val hmacKey = derived.copyOfRange(32, 64)
        return Pair(aesKey, hmacKey)
    }
}