package com.example

import android.net.Uri
import com.example.data.auth.DeepLinkAuthParser
import com.example.data.auth.DeepLinkAuthResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinkAuthParserTest {

    @Test
    fun parse_nullUri_returnsIgnored() {
        val result = DeepLinkAuthParser.parse(null)
        assertEquals(DeepLinkAuthResult.Ignored, result)
    }

    @Test
    fun parse_wrongSchemeOrHost_returnsIgnored() {
        val uri = Uri.parse("https://example.com/callback?code=12345")
        val result = DeepLinkAuthParser.parse(uri)
        assertEquals(DeepLinkAuthResult.Ignored, result)

        val uri2 = Uri.parse("studentmanager://wrong-host?code=12345")
        val result2 = DeepLinkAuthParser.parse(uri2)
        assertEquals(DeepLinkAuthResult.Ignored, result2)
    }

    @Test
    fun parse_errorParam_returnsAuthError() {
        val uri = Uri.parse("studentmanager://login-callback?error=access_denied&error_description=User+declined")
        val result = DeepLinkAuthParser.parse(uri)
        assertTrue(result is DeepLinkAuthResult.AuthError)
        val authError = result as DeepLinkAuthResult.AuthError
        assertEquals("access_denied", authError.errorCode)
        assertEquals("User declined", authError.errorDescription)
    }

    @Test
    fun parse_expiredOtpError_returnsAuthError() {
        val uri = Uri.parse("studentmanager://login-callback?error=otp_expired&error_description=Email+link+is+invalid+or+has+expired")
        val result = DeepLinkAuthParser.parse(uri)
        assertTrue(result is DeepLinkAuthResult.AuthError)
        val authError = result as DeepLinkAuthResult.AuthError
        assertEquals("otp_expired", authError.errorCode)
        assertTrue(authError.errorDescription.contains("expired"))
    }

    @Test
    fun parse_pkceCode_returnsCodeExchange() {
        val uri = Uri.parse("studentmanager://login-callback?code=084a7e93-547c-47bb-a9df-287702f2dd9d")
        val result = DeepLinkAuthParser.parse(uri)
        assertTrue(result is DeepLinkAuthResult.CodeExchange)
        val codeExchange = result as DeepLinkAuthResult.CodeExchange
        assertEquals("084a7e93-547c-47bb-a9df-287702f2dd9d", codeExchange.code)
    }

    @Test
    fun parse_tokenHashSignup_returnsEmailOtp() {
        val uri = Uri.parse("studentmanager://login-callback?token_hash=pkce_abc123xyz&type=signup")
        val result = DeepLinkAuthParser.parse(uri)
        assertTrue(result is DeepLinkAuthResult.EmailOtp)
        val emailOtp = result as DeepLinkAuthResult.EmailOtp
        assertEquals("pkce_abc123xyz", emailOtp.tokenHash)
        assertEquals("signup", emailOtp.type)
    }

    @Test
    fun parse_tokenParamSignup_returnsEmailOtp() {
        val uri = Uri.parse("studentmanager://login-callback?token=test_token_456&type=signup")
        val result = DeepLinkAuthParser.parse(uri)
        assertTrue(result is DeepLinkAuthResult.EmailOtp)
        val emailOtp = result as DeepLinkAuthResult.EmailOtp
        assertEquals("test_token_456", emailOtp.tokenHash)
        assertEquals("signup", emailOtp.type)
    }

    @Test
    fun parse_fragmentAccessToken_returnsFragmentSession() {
        val uri = Uri.parse("studentmanager://login-callback#access_token=eyJhbGciOi...&refresh_token=abc&expires_in=3600&token_type=bearer&type=signup")
        val result = DeepLinkAuthParser.parse(uri)
        assertTrue(result is DeepLinkAuthResult.FragmentSession)
    }

    @Test
    fun parse_emptyOrUnrelatedParams_returnsIgnored() {
        val uri = Uri.parse("studentmanager://login-callback")
        val result = DeepLinkAuthParser.parse(uri)
        assertEquals(DeepLinkAuthResult.Ignored, result)
    }
}
