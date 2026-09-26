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
    var mockTeacherId: String? = null

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

    fun getEdgeFunctionUrl(functionName: String): String {
        val configuredUrl = try {
            BuildConfig.SUPABASE_EDGE_FUNCTION_URL
        } catch (e: Exception) {
            ""
        }
        return if (configuredUrl.isNotBlank() && !configuredUrl.contains("placeholder")) {
            val trimmed = configuredUrl.trimEnd('/')
            if (trimmed.endsWith(functionName)) {
                trimmed
            } else {
                "$trimmed/$functionName"
            }
        } else {
            "$baseUrl/functions/v1/$functionName"
        }
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
