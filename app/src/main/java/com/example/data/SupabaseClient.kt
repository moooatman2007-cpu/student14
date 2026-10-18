package com.example.data

import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

object SupabaseClientProvider {
    @Volatile
    private var _mockTeacherId: String? = null

    /**
     * Test-only / Debug-only mock teacher identifier.
     * In release/production builds (BuildConfig.DEBUG == false), this strictly returns null
     * and setters are ignored, guaranteeing identity is sourced exclusively from Supabase Auth.
     */
    var mockTeacherId: String?
        get() = if (BuildConfig.DEBUG) _mockTeacherId else null
        set(value) {
            if (BuildConfig.DEBUG) {
                _mockTeacherId = value
            }
        }

    val baseUrl: String by lazy {
        val supabaseUrl = try {
            BuildConfig.SUPABASE_URL
        } catch (e: Exception) {
            ""
        }
        if (supabaseUrl.isNotBlank() && !supabaseUrl.contains("placeholder")) {
            supabaseUrl.trimEnd('/')
        } else {
            "https://placeholder.supabase.co"
        }
    }

    /**
     * Resolves an Edge Function endpoint URL deterministically.
     * Extracts the base functions path (/functions/v1) even if configuredUrl
     * accidentally specifies a specific function name (such as /waha-session).
     */
    fun resolveEdgeFunctionUrl(
        configuredUrl: String?,
        baseUrl: String,
        functionName: String
    ): String {
        val cleanName = functionName.trim().trim('/')
        val trimmed = (configuredUrl ?: "").trim().trimEnd('/')

        val functionsBaseUrl = if (trimmed.isNotBlank() && !trimmed.contains("placeholder", ignoreCase = true)) {
            val fv1Index = trimmed.indexOf("/functions/v1")
            if (fv1Index != -1) {
                trimmed.substring(0, fv1Index + "/functions/v1".length)
            } else {
                trimmed.removeSuffix("/$cleanName")
                    .removeSuffix("/waha-session")
                    .removeSuffix("/waha-webhook")
                    .removeSuffix("/studentmanager-notification-worker")
            }
        } else {
            val cleanBase = baseUrl.trim().trimEnd('/')
            if (cleanBase.isNotBlank() && !cleanBase.contains("placeholder", ignoreCase = true)) {
                "$cleanBase/functions/v1"
            } else {
                "https://placeholder.supabase.co/functions/v1"
            }
        }

        return if (cleanName.isNotBlank()) {
            "$functionsBaseUrl/$cleanName"
        } else {
            functionsBaseUrl
        }
    }

    fun getEdgeFunctionUrl(functionName: String): String {
        val configuredUrl = try {
            BuildConfig.SUPABASE_EDGE_FUNCTION_URL
        } catch (_: Exception) {
            ""
        }
        return resolveEdgeFunctionUrl(configuredUrl, baseUrl, functionName)
    }

    val client: SupabaseClient by lazy {
        val supabaseUrl = try {
            BuildConfig.SUPABASE_URL
        } catch (e: Exception) {
            ""
        }

        val supabaseKey = try {
            BuildConfig.SUPABASE_PUBLISHABLE_KEY
        } catch (e: Exception) {
            ""
        }

        createSupabaseClient(
            supabaseUrl = if (supabaseUrl.isNotBlank()) supabaseUrl else "https://placeholder.supabase.co",
            supabaseKey = if (supabaseKey.isNotBlank()) supabaseKey else "placeholder-key"
        ) {
            install(Auth) {
                scheme = "studentmanager"
                host = "login-callback"
                defaultRedirectUrl = "studentmanager://login-callback"
                sessionManager = try {
                    io.github.jan.supabase.auth.SettingsSessionManager()
                } catch (_: Throwable) {
                    io.github.jan.supabase.auth.MemorySessionManager()
                }
                codeVerifierCache = try {
                    io.github.jan.supabase.auth.SettingsCodeVerifierCache()
                } catch (_: Throwable) {
                    io.github.jan.supabase.auth.MemoryCodeVerifierCache()
                }
            }
            install(Postgrest)
            install(Storage)
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}
