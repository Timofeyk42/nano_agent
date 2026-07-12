package com.example.nanoagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [WikiEntry::class, WikiFtsEntry::class, ChatSession::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun knowledgeBaseDao(): KnowledgeBaseDao
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nano_agent_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.knowledgeBaseDao())
                    }
                }
            }

            suspend fun populateDatabase(dao: KnowledgeBaseDao) {
                dao.insertEntry(
                    WikiEntry(
                        title = "Pixel 10 Specification",
                        content = "Pixel 10 features the Google Tensor G5 processor, 16GB of LPDDR5X RAM, and the AI Core system service running Gemini Nano 4 (gemma-e4b-4b). It runs Android 15. The device supports local multimodal features, enabling offline image, audio, and text analysis."
                    )
                )
                dao.insertEntry(
                    WikiEntry(
                        title = "Local Wiki Guide",
                        content = "This is a local database. The on-device agent can search this knowledge base to answer questions offline without querying the internet."
                    )
                )
            }
        }
    }
}
