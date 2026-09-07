package com.doodlr.aeslocker

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object OpenSSLCrypto {
    private const val SALTED_PREFIX = "Salted__"
    private const val PBKDF2_ITERATIONS = 10000

    fun encrypt(
        inputStream: InputStream,
        outputStream: OutputStream,
        password: CharArray,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) {
        val salt = ByteArray(8)
        SecureRandom().nextBytes(salt)

        outputStream.write(SALTED_PREFIX.toByteArray(Charsets.UTF_8))
        outputStream.write(salt)

        val (key, iv) = deriveKeyAndIV(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var totalProcessed = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val output = cipher.update(buffer, 0, bytesRead)
            if (output != null) outputStream.write(output)
            totalProcessed += bytesRead
            if (totalBytes > 0) {
                onProgress(totalProcessed.toFloat() / totalBytes.toFloat())
            }
        }
        val finalBytes = cipher.doFinal()
        if (finalBytes != null) outputStream.write(finalBytes)
        onProgress(1.0f)
    }

    /**
     * Fully validates password against OpenSSL AES-256-CBC structure before launching save dialog.
     * Checks "Salted__" header and forces PKCS5 padding verification via doFinal().
     */
    fun validatePassword(inputStream: InputStream, password: CharArray): Boolean {
        return try {
            val header = ByteArray(8)
            val readHeader = inputStream.read(header)
            if (readHeader != 8 || String(header, Charsets.UTF_8) != SALTED_PREFIX) return false

            val salt = ByteArray(8)
            val readSalt = inputStream.read(salt)
            if (readSalt != 8) return false

            val (key, iv) = deriveKeyAndIV(password, salt)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                cipher.update(buffer, 0, bytesRead)
            }
            cipher.doFinal() // Will throw BadPaddingException if password/key is incorrect
            true
        } catch (e: BadPaddingException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun decrypt(
        inputStream: InputStream,
        outputStream: OutputStream,
        password: CharArray,
        totalBytes: Long,
        onProgress: (Float) -> Unit
    ) {
        val header = ByteArray(8)
        val readHeader = inputStream.read(header)
        check(readHeader == 8 && String(header, Charsets.UTF_8) == SALTED_PREFIX) {
            "Invalid file format."
        }

        val salt = ByteArray(8)
        inputStream.read(salt)

        val (key, iv) = deriveKeyAndIV(password, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        var totalProcessed = 0L

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val output = cipher.update(buffer, 0, bytesRead)
            if (output != null) outputStream.write(output)
            totalProcessed += bytesRead
            if (totalBytes > 0) {
                onProgress(totalProcessed.toFloat() / totalBytes.toFloat())
            }
        }
        val finalBytes = cipher.doFinal()
        if (finalBytes != null) outputStream.write(finalBytes)
        onProgress(1.0f)
    }

    private fun deriveKeyAndIV(password: CharArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 384)
        val derivedBytes = factory.generateSecret(spec).encoded

        val key = derivedBytes.copyOfRange(0, 32)
        val iv = derivedBytes.copyOfRange(32, 48)
        return Pair(key, iv)
    }
}