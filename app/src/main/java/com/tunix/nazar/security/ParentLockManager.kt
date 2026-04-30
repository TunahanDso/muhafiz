package com.tunix.nazar.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest

class ParentLockManager(context: Context) {

    /*
     * PIN bilgisi küçük ve lokal bir veri olduğu için SharedPreferences yeterli.
     * Bu veri dışarıya açık değildir; sadece uygulama içinden okunur.
     */
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /*
     * Daha önce ebeveyn PIN'i oluşturulmuş mu kontrol eder.
     */
    fun hasPin(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()
    }

    /*
     * Yeni PIN kaydeder.
     *
     * PIN doğrudan saklanmaz.
     * PIN'in hash değeri saklanır.
     */
    fun savePin(pin: String): Boolean {
        if (!isPinFormatValid(pin)) return false

        return prefs.edit()
            .putString(KEY_PIN_HASH, hashPin(pin))
            .commit()
    }

    /*
     * Kullanıcının girdiği PIN ile kayıtlı PIN hash'ini karşılaştırır.
     */
    fun verifyPin(pin: String): Boolean {
        if (!isPinFormatValid(pin)) return false

        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return savedHash == hashPin(pin)
    }

    /*
     * Kayıtlı ebeveyn PIN'ini temizler.
     *
     * Bu işlemden sonra uygulama yeniden PIN kurulumu isteyebilir.
     */
    fun clearPin(): Boolean {
        return prefs.edit()
            .remove(KEY_PIN_HASH)
            .commit()
    }

    /*
     * PIN format kuralı:
     * - Sadece rakam
     * - En az 4, en fazla 6 hane
     */
    fun isPinFormatValid(pin: String): Boolean {
        return pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }
    }

    private fun hashPin(pin: String): String {
        /*
         * Not:
         * Buradaki salt, basit tersine arama riskini azaltmak için eklenmiştir.
         * Daha ileri güvenlik istenirse Android Keystore + rastgele salt yapısına geçilebilir.
         */
        val saltedPin = "$PIN_HASH_SALT:$pin"
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val hashedBytes = digest.digest(saltedPin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hashedBytes, Base64.NO_WRAP)
    }

    companion object {
        private const val PREFS_NAME = "muhafiz_parent_lock_prefs"
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val HASH_ALGORITHM = "SHA-256"

        /*
         * Eski sürümde çıplak PIN hash yerine ürün adına bağlı sabit salt kullanıyoruz.
         */
        private const val PIN_HASH_SALT = "MuhafizParentLockV1"

        private const val MIN_PIN_LENGTH = 4
        private const val MAX_PIN_LENGTH = 6
    }
}