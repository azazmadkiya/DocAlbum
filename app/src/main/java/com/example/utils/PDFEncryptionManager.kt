package com.example.utils

import android.content.Context
import android.widget.Toast
import java.security.MessageDigest

/**
 * Manager class to handle password protection, user/owner passwords,
 * and security parameters when exporting A4 documents as secure PDFs.
 */
class PDFEncryptionManager(private val context: Context) {

    data class EncryptionConfig(
        val userPassword: String,
        val ownerPassword: String = userPassword,
        val allowPrinting: Boolean = true,
        val allowCopying: Boolean = false
    )

    var currentConfig: EncryptionConfig? = null
        private set

    /**
     * Sets user and owner passwords for exported A4 files.
     */
    fun setPasswords(userPass: String, ownerPass: String = userPass): Boolean {
        if (userPass.isBlank()) {
            Toast.makeText(context, "Password cannot be blank", Toast.LENGTH_SHORT).show()
            return false
        }
        currentConfig = EncryptionConfig(
            userPassword = userPass,
            ownerPassword = ownerPass
        )
        Toast.makeText(context, "Password protection enabled for export", Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * Clears password protection (removes encryption config).
     */
    fun clearProtection() {
        currentConfig = null
        Toast.makeText(context, "Password protection removed", Toast.LENGTH_SHORT).show()
    }

    /**
     * Verifies if a given password matches the configured user password.
     */
    fun verifyPassword(inputPassword: String): Boolean {
        val config = currentConfig ?: return true
        return config.userPassword == inputPassword || config.ownerPassword == inputPassword
    }

    /**
     * Hashes password for secure storage or checking if needed.
     */
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
