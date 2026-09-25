package com.example.data.auth

import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.SupabaseClientProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

object DeepLinkAuthHandler {
    private const val TAG = "DeepLinkAuthHandler"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastProcessedUri = AtomicReference<String?>(null)

    /**
     * Handles an incoming deep link URI safely.
     * Guarantees idempotent execution: the same URI will not be processed twice
     * across onCreate and onNewIntent.
     */
    fun handleUri(uri: Uri?) {
        if (uri == null) return

        val uriString = uri.toString()
        if (lastProcessedUri.get() == uriString) {
            return
        }

        val parsed = DeepLinkAuthParser.parse(uri)
        if (parsed is DeepLinkAuthResult.Ignored) {
            return
        }

        // Atomically set last processed URI to prevent duplicate execution
        if (!lastProcessedUri.compareAndSet(lastProcessedUri.get(), uriString)) {
            // Check again in case of race
            if (lastProcessedUri.get() == uriString) return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Processing deep link of type: ${parsed.javaClass.simpleName}")
        }

        when (parsed) {
            is DeepLinkAuthResult.AuthError -> {
                val raw = "${parsed.errorCode.orEmpty()} ${parsed.errorDescription}"
                val errorMsg = SafeAuthErrorMapper.mapAuthErrorToArabic(raw, AuthActionContext.EMAIL_CONFIRM)
                AuthFeedbackBus.emitError(errorMsg)
            }

            is DeepLinkAuthResult.CodeExchange -> {
                scope.launch {
                    try {
                        // 1. Exchange PKCE code for session using supabase-kt 3.1.1 API
                        SupabaseClientProvider.client.auth.exchangeCodeForSession(
                            code = parsed.code,
                            saveSession = false
                        )
                        // 2. Clear any lingering session to prevent auto-login
                        try {
                            SupabaseClientProvider.client.auth.signOut()
                        } catch (_: Exception) {}

                        AuthFeedbackBus.emitSuccess("تم تأكيد البريد الإلكتروني بنجاح، يمكنك الآن تسجيل الدخول.")
                    } catch (e: Exception) {
                        SafeAuthLogger.logAuthError(TAG, "CodeExchange", e)
                        val msg = SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.EMAIL_CONFIRM)
                        AuthFeedbackBus.emitError(msg)
                    }
                }
            }

            is DeepLinkAuthResult.EmailOtp -> {
                scope.launch {
                    try {
                        // Determine OTP email type
                        val otpType = when (parsed.type.lowercase()) {
                            "signup" -> OtpType.Email.SIGNUP
                            "recovery" -> OtpType.Email.RECOVERY
                            "invite" -> OtpType.Email.INVITE
                            "email_change" -> OtpType.Email.EMAIL_CHANGE
                            else -> OtpType.Email.SIGNUP
                        }

                        // Verify Email OTP via tokenHash using supabase-kt 3.1.1 API
                        SupabaseClientProvider.client.auth.verifyEmailOtp(
                            type = otpType,
                            tokenHash = parsed.tokenHash
                        )

                        // Clear any lingering session to respect the policy:
                        // Signup / email confirm does NOT auto login; user logs in explicitly.
                        try {
                            SupabaseClientProvider.client.auth.signOut()
                        } catch (_: Exception) {}

                        AuthFeedbackBus.emitSuccess("تم تأكيد البريد الإلكتروني بنجاح، يمكنك الآن تسجيل الدخول.")
                    } catch (e: Exception) {
                        SafeAuthLogger.logAuthError(TAG, "EmailOtp", e)
                        val msg = SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.EMAIL_CONFIRM)
                        AuthFeedbackBus.emitError(msg)
                    }
                }
            }

            is DeepLinkAuthResult.FragmentSession -> {
                scope.launch {
                    try {
                        // In case Supabase returned fragment tokens directly, clear session to prevent unintended auto login
                        try {
                            SupabaseClientProvider.client.auth.signOut()
                        } catch (_: Exception) {}

                        AuthFeedbackBus.emitSuccess("تم تأكيد البريد الإلكتروني بنجاح، يمكنك الآن تسجيل الدخول.")
                    } catch (e: Exception) {
                        SafeAuthLogger.logAuthError(TAG, "FragmentSession", e)
                    }
                }
            }

            is DeepLinkAuthResult.Ignored -> Unit
        }
    }

    /**
     * Resets the cache of last processed URI (useful for unit testing).
     */
    fun resetForTesting() {
        lastProcessedUri.set(null)
    }
}
