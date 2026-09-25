package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.DeepLinkAuthHandler
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.ThemeMode
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.StudentManagerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleAuthIntent(intent)

        setContent {
            val themeMode by RepositoryProvider.settingsRepository.getThemeMode()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            StudentManagerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null) {
            try {
                if (BuildConfig.DEBUG) Log.d("MainActivity", "Handling deep link URI: $uri")
                DeepLinkAuthHandler.handleUri(uri)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e("MainActivity", "Failed to handle deep link: ${e.message}", e)
            }
        }
    }
}
