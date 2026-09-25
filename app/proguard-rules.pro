# ProGuard / R8 Rules for MIDAR Release Build Optimization

# Preserve source file names and line numbers for stacktraces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# 1. Kotlin Serialization Rules
# ------------------------------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

-keepclassmembers class * {
    public static final **Companion Companion;
}

-keepclassmembers class **$Companion {
    public kotlinx.serialization.KSerializer serializer(...);
}

-keep @kotlinx.serialization.Serializable class com.example.core.model.** { *; }

# ------------------------------------------------------------------------------
# 2. Room Database Rules
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}

-keep class com.example.data.local.entity.** { *; }
-keep interface com.example.data.local.dao.** { *; }
-keep class com.example.data.local.AppDatabase { *; }

# ------------------------------------------------------------------------------
# 3. WorkManager Rules
# ------------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.data.sync.OutboxSyncWorker { *; }

# ------------------------------------------------------------------------------
# 4. Supabase & Ktor Client Rules
# ------------------------------------------------------------------------------
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }

# Ktor optionally checks Java Desktop JVM Management APIs which are not present on Android Runtime
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# ------------------------------------------------------------------------------
# 5. ML Kit Barcode Scanning & CameraX Rules
# ------------------------------------------------------------------------------
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.android.gms.vision.** { *; }

# ------------------------------------------------------------------------------
# 6. PDF Generation
# ------------------------------------------------------------------------------
-keep class android.graphics.pdf.PdfDocument { *; }
