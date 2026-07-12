package com.example.nanoagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey
    val sessionId: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sessionId: String,
    val sender: String, // "USER" or "AGENT"
    val content: String,
    val stepsJson: String, // JSON serialization of AgentStep list
    val timestamp: Long = System.currentTimeMillis()
)
