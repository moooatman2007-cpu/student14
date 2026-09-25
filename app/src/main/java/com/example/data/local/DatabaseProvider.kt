package com.example.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null
    @Volatile
    private var appContext: Context? = null

    val context: Context
        get() = appContext ?: throw IllegalStateException("DatabaseProvider not initialized with Context.")

    fun init(context: Context) {
        appContext = context.applicationContext
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = buildDatabase(context.applicationContext)
                }
            }
        }
    }

    fun setDatabase(database: AppDatabase, context: Context? = null) {
        instance = database
        if (context != null) {
            appContext = context.applicationContext
        }
    }

    fun resetForTesting() {
        instance = null
        appContext = null
    }

    suspend fun clearAllData() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        instance?.clearAllTables()
    }

    fun clearAllDataSync() {
        instance?.clearAllTables()
    }

    fun getDatabase(context: Context? = null): AppDatabase {
        if (context != null && appContext == null) {
            appContext = context.applicationContext
        }
        return instance ?: synchronized(this) {
            instance ?: run {
                if (context != null) {
                    buildDatabase(context.applicationContext).also {
                        instance = it
                        appContext = context.applicationContext
                    }
                } else {
                    throw IllegalStateException("DatabaseProvider must be initialized with a Context.")
                }
            }
        }
    }

    private fun buildDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "quran_app.db"
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7
        ).build()
    }
}
