import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.studentmanager.xpseeg"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
    val releaseStorePassword = System.getenv("STORE_PASSWORD") ?: ""
    val releaseKeyAlias = System.getenv("KEY_ALIAS") ?: "upload"
    val releaseKeyPassword = System.getenv("KEY_PASSWORD") ?: releaseStorePassword

    create("release") {
      storeFile = file(releaseKeystorePath)
      storePassword = releaseStorePassword
      keyAlias = releaseKeyAlias
      keyPassword = releaseKeyPassword
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // STRICT: Release builds MUST ONLY use the production release signing configuration.
      // Under NO circumstances is debugConfig allowed as a fallback.
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

val verifyReleasePreconditions = tasks.register("verifyReleasePreconditions") {
  description = "Validates that all production release preconditions (signing keystore, passwords, and .env Supabase credentials) are properly satisfied."
  group = "verification"
  val rootDirPath = rootDir.absolutePath
  doFirst {
    val errors = mutableListOf<String>()

    // 1. Keystore file check
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "$rootDirPath/my-upload-key.jks"
    val keystoreFile = File(releaseKeystorePath)
    if (!keystoreFile.exists()) {
      errors.add("Production Keystore file missing at: ${keystoreFile.absolutePath} (Set KEYSTORE_PATH env var or place 'my-upload-key.jks' in root directory).")
    }

    // 2. STORE_PASSWORD check
    val storePassword = System.getenv("STORE_PASSWORD")
    if (storePassword.isNullOrBlank()) {
      errors.add("Missing or empty 'STORE_PASSWORD' environment variable.")
    }

    // 3. KEY_ALIAS check
    val keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
    if (keyAlias.isBlank()) {
      errors.add("Missing or empty 'KEY_ALIAS' environment variable.")
    }

    // 4. KEY_PASSWORD check
    val keyPassword = System.getenv("KEY_PASSWORD") ?: storePassword
    if (keyPassword.isNullOrBlank()) {
      errors.add("Missing or empty 'KEY_PASSWORD' environment variable.")
    }

    // 5. .env file check
    val envFile = File(rootDirPath, ".env")
    if (!envFile.exists() || !envFile.isFile) {
      errors.add("Missing root '.env' file. Production Release builds require a real '.env' file containing Production Supabase configuration.")
    } else {
      val envMap = mutableMapOf<String, String>()
      for (line in envFile.readLines()) {
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
          val splitIndex = trimmed.indexOf('=')
          if (splitIndex != -1) {
            val key = trimmed.substring(0, splitIndex).trim()
            val value = trimmed.substring(splitIndex + 1).trim().trim('"', '\'')
            envMap[key] = value
          }
        }
      }

      val supabaseUrl = envMap["SUPABASE_URL"]
      if (supabaseUrl.isNullOrBlank()) {
        errors.add("Missing or empty 'SUPABASE_URL' in .env.")
      } else if (supabaseUrl.contains("placeholder", ignoreCase = true)) {
        errors.add("'SUPABASE_URL' in .env contains placeholder value.")
      } else if (!supabaseUrl.startsWith("https://") && !supabaseUrl.startsWith("http://")) {
        errors.add("'SUPABASE_URL' in .env must start with 'https://'.")
      }

      val supabaseKey = envMap["SUPABASE_PUBLISHABLE_KEY"]
      if (supabaseKey.isNullOrBlank()) {
        errors.add("Missing or empty 'SUPABASE_PUBLISHABLE_KEY' in .env.")
      } else if (supabaseKey.contains("placeholder", ignoreCase = true)) {
        errors.add("'SUPABASE_PUBLISHABLE_KEY' in .env contains placeholder value.")
      }

      val supabaseEdgeUrl = envMap["SUPABASE_EDGE_FUNCTION_URL"]
      if (supabaseEdgeUrl.isNullOrBlank()) {
        errors.add("Missing or empty 'SUPABASE_EDGE_FUNCTION_URL' in .env.")
      } else if (supabaseEdgeUrl.contains("placeholder", ignoreCase = true)) {
        errors.add("'SUPABASE_EDGE_FUNCTION_URL' in .env contains placeholder value.")
      }
    }

    if (errors.isNotEmpty()) {
      val errorMessage = buildString {
        appendLine()
        appendLine("==================================================================================")
        appendLine("❌ PRODUCTION BUILD SAFETY GATE FAILED:")
        appendLine("==================================================================================")
        errors.forEachIndexed { index, err ->
          appendLine("  [${index + 1}] $err")
        }
        appendLine("==================================================================================")
        appendLine("Release build cannot proceed with missing or invalid production credentials.")
        appendLine("Please provide the required keystore and .env configurations before building release.")
        appendLine("==================================================================================")
      }
      throw GradleException(errorMessage)
    }
  }
}

tasks.matching {
  it.name in listOf(
    "preReleaseBuild",
    "bundleRelease",
    "assembleRelease",
    "packageReleaseBundle",
    "signReleaseBundle",
    "validateSigningRelease"
  )
}.configureEach {
  dependsOn(verifyReleasePreconditions)
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.zxing.core)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.auth)
  implementation(libs.supabase.storage)
  implementation(libs.ktor.client.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
