package com.tunix.nazar.security

import android.content.Context
import java.security.MessageDigest

class DeveloperAccessManager(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Geliştirici / Google Play inceleme erişimi açık mı?
     */
    fun isUnlocked(): Boolean {
        return preferences.getBoolean(KEY_ACCESS_ENABLED, false)
    }

    /**
     * Kullanıcının girdiği erişim kodunu doğrular.
     *
     * Doğruysa erişimi kalıcı olarak açar ve true döndürür.
     * Yanlışsa hiçbir değişiklik yapmaz ve false döndürür.
     */
    fun unlock(code: String): Boolean {
        val normalizedCode = code.trim()

        if (normalizedCode.isEmpty()) {
            return false
        }

        val enteredHash = sha256(normalizedCode)

        val expectedHashBytes = hexToByteArray(REVIEW_ACCESS_CODE_SHA256)
        val enteredHashBytes = hexToByteArray(enteredHash)

        val matches = MessageDigest.isEqual(
            enteredHashBytes,
            expectedHashBytes
        )

        if (matches) {
            preferences.edit()
                .putBoolean(KEY_ACCESS_ENABLED, true)
                .apply()
        }

        return matches
    }

    /**
     * Geliştirici / inceleme erişimini tekrar kapatır.
     */
    fun lock() {
        preferences.edit()
            .putBoolean(KEY_ACCESS_ENABLED, false)
            .apply()
    }

    /**
     * SHA-256 hash üretir.
     */
    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(value.toByteArray(Charsets.UTF_8))

        return hash.joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }

    /**
     * Constant-time karşılaştırmada kullanılmak üzere
     * hexadecimal String'i ByteArray'e dönüştürür.
     */
    private fun hexToByteArray(value: String): ByteArray {
        require(value.length % 2 == 0) {
            "Hexadecimal değer çift uzunlukta olmalıdır."
        }

        return ByteArray(value.length / 2) { index ->
            val first = Character.digit(value[index * 2], 16)
            val second = Character.digit(value[index * 2 + 1], 16)

            require(first != -1 && second != -1) {
                "Geçersiz hexadecimal değer."
            }

            ((first shl 4) + second).toByte()
        }
    }

    companion object {
        private const val PREFS_NAME = "muhafiz_developer_access"
        private const val KEY_ACCESS_ENABLED = "developer_access_enabled"

        /**
         * Gerçek erişim kodunun kendisi APK içinde tutulmaz.
         * Burada yalnızca SHA-256 hash'i bulunur.
         */
        private const val REVIEW_ACCESS_CODE_SHA256 =
            "7a0a6411e9ad81e2a7680c2db76623ec1270e3e714805c3d96ae23210c2a6a12"
    }
}