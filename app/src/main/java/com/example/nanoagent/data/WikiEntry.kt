package com.example.nanoagent.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4 // Or Fts5 if Room configuration permits, but Fts4/5 is supported. Room supports Fts4 directly.
import androidx.room.PrimaryKey

@Entity(tableName = "wiki_entries")
data class WikiEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Fts4(contentEntity = WikiEntry::class)
@Entity(tableName = "wiki_entries_fts")
data class WikiFtsEntry(
    val title: String,
    val content: String
)
