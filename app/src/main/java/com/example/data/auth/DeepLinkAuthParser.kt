package com.example.data.auth

import android.net.Uri

sealed interface DeepLinkAuthResult {
    data class CodeExchange(val code: String) : DeepLinkAuthResult
    data class EmailOtp(val tokenHash: String, val type: String) : DeepLinkAuthResult
    data class FragmentSession(val fragment: String) : DeepLinkAuthResult
    data class AuthError(val errorCode: String?, val errorDescription: String) : DeepLinkAuthResult
    data object Ignored : DeepLinkAuthResult
}

object DeepLinkAuthParser {

    /**
     * Parses an incoming intent URI against studentmanager://login-callback
     * and categorizes it into CodeExchange, EmailOtp, FragmentSession, AuthError, or Ignored.
     */
    fun parse(uri: Uri?): DeepLinkAuthResult {
        if (uri == null) return DeepLinkAuthResult.Ignored

        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()

        // Verify scheme and host match studentmanager://login-callback
        if (scheme != "studentmanager" || host != "login-callback") {
            return DeepLinkAuthResult.Ignored
        }

        // 1. Check for error or error_description query params
        val error = uri.getQueryParameter("error")
        val errorDescription = uri.getQueryParameter("error_description")
        if (!error.isNullOrBlank() || !errorDescription.isNullOrBlank()) {
            val description = errorDescription ?: error ?: "فشل التحقق من الرابط"
            return DeepLinkAuthResult.AuthError(
                errorCode = error,
                errorDescription = description
            )
        }

        // 2. Check for PKCE 'code' query param
        val code = uri.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            return DeepLinkAuthResult.CodeExchange(code = code)
        }

        // 3. Check for Email OTP confirmation tokens (token_hash or token)
        val tokenHash = uri.getQueryParameter("token_hash") ?: uri.getQueryParameter("token")
        val type = uri.getQueryParameter("type") ?: "signup"
        if (!tokenHash.isNullOrBlank()) {
            return DeepLinkAuthResult.EmailOtp(
                tokenHash = tokenHash,
                type = type
            )
        }

        // 4. Check for implicit fragment (#access_token=...&refresh_token=...)
        val fragment = uri.fragment
        if (!fragment.isNullOrBlank() && fragment.contains("access_token")) {
            return DeepLinkAuthResult.FragmentSession(fragment = fragment)
        }

        return DeepLinkAuthResult.Ignored
    }
}
