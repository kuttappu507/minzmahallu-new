package com.mms.minzmahallu.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-SHA256 matching Electron auth.service.ts (200_000 iterations). */
object Crypto {
    private const val ITER = 200_000
    private const val KEY_LEN = 32

    fun validatePassword(password: String) {
        require(password.length >= 8) { "Password must be at least 8 characters" }
        require(password.any { it.isUpperCase() }) { "Password must include uppercase, lowercase, digit, and special character" }
        require(password.any { it.isLowerCase() }) { "Password must include uppercase, lowercase, digit, and special character" }
        require(password.any { it.isDigit() }) { "Password must include uppercase, lowercase, digit, and special character" }
        require(password.any { !it.isLetterOrDigit() }) { "Password must include uppercase, lowercase, digit, and special character" }
    }

    fun hashPassword(password: String): Pair<String, String> {
        validatePassword(password)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password.toCharArray(), salt, ITER, KEY_LEN)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "pbkdf2_sha256\$$ITER\$$saltB64\$$hashB64" to saltB64
    }

    fun verifyPassword(plain: String, stored: String): Boolean {
        val p = stored.split("$")
        if (p.size != 4 || p[0] != "pbkdf2_sha256") return false
        val iter = p[1].toIntOrNull() ?: return false
        val salt = Base64.decode(p[2], Base64.NO_WRAP)
        val expected = Base64.decode(p[3], Base64.NO_WRAP)
        val actual = pbkdf2(plain.toCharArray(), salt, iter, expected.size)
        if (actual.size != expected.size) return false
        var diff = 0
        for (i in actual.indices) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        return diff == 0
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iter: Int, len: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iter, len * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun initials(name: String): String {
        val p = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (p.isEmpty()) return "?"
        return if (p.size == 1) p[0].take(1).uppercase()
        else (p.first().take(1) + p.last().take(1)).uppercase()
    }

    fun randomToken(bytes: Int = 16): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE).trimEnd('=')
    }
}
