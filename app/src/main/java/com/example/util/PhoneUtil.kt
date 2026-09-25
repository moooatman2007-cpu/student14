package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object PhoneUtil {

    /**
     * Normalizes Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) to ASCII digits (0123456789).
     */
    fun normalizeDigits(input: String?): String {
        if (input == null) return ""
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        val builder = StringBuilder()
        for (ch in input) {
            val index = arabicDigits.indexOf(ch)
            if (index >= 0) {
                builder.append(index)
            } else {
                builder.append(ch)
            }
        }
        return builder.toString()
    }

    /**
     * Checks if a phone number string contains a valid phone number.
     */
    fun hasValidPhoneNumber(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        val digits = normalizeDigits(phoneNumber.trim()).replace(Regex("[^0-9]"), "")
        return digits.length >= 8
    }

    /**
     * Formats phone number for ACTION_DIAL intent (e.g. 01012345678 or +201012345678).
     */
    fun formatForDial(phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) return ""
        val normalized = normalizeDigits(phoneNumber.trim())
        return normalized.replace(Regex("[^0-9+]"), "")
    }

    /**
     * Formats phone number for WhatsApp intent (international format without leading + or 00).
     * Converts Egyptian local numbers starting with 01 (11 digits) to 201xxxxxxxxx.
     */
    fun formatForWhatsApp(phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) return ""
        var digits = normalizeDigits(phoneNumber.trim()).replace(Regex("[^0-9]"), "")
        if (digits.startsWith("01") && digits.length == 11) {
            digits = "2" + digits
        } else if (digits.startsWith("0020")) {
            digits = digits.substring(2)
        }
        return digits
    }

    /**
     * Launches Android Dialer app via Intent.ACTION_DIAL safely.
     */
    fun launchCallIntent(context: Context, phoneNumber: String?): Boolean {
        if (!hasValidPhoneNumber(phoneNumber)) return false
        return try {
            val formatted = formatForDial(phoneNumber)
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$formatted")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches WhatsApp conversation via Intent safely without crashing.
     */
    fun launchWhatsAppIntent(
        context: Context,
        phoneNumber: String?,
        onAppNotFound: () -> Unit
    ): Boolean {
        if (!hasValidPhoneNumber(phoneNumber)) return false
        val formatted = formatForWhatsApp(phoneNumber)
        val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$formatted")
        
        return try {
            val primaryIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(primaryIntent)
            true
        } catch (_: ActivityNotFoundException) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (_: Exception) {
                onAppNotFound()
                false
            }
        } catch (_: Exception) {
            onAppNotFound()
            false
        }
    }
}
