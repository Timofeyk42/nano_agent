package com.example.nanoagent.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeBaseDao {
    @Query("SELECT * FROM wiki_entries ORDER BY id DESC")
    fun getAllEntriesFlow(): Flow<List<WikiEntry>>

    @Query("SELECT * FROM wiki_entries ORDER BY id DESC")
    suspend fun getAllEntries(): List<WikiEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: WikiEntry): Long

    @Delete
    suspend fun deleteEntry(entry: WikiEntry)

    @Query("""
        SELECT wiki_entries.* FROM wiki_entries
        JOIN wiki_entries_fts ON wiki_entries.rowid = wiki_entries_fts.rowid
        WHERE wiki_entries_fts MATCH :searchQuery
    """)
    suspend fun search(searchQuery: String): List<WikiEntry>
}
