package com.example.nanoagent.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nanoagent.model.ConversationMessage
import com.example.nanoagent.model.AgentModelResponse
import com.example.nanoagent.model.AgentToolCall
import com.example.nanoagent.model.AgentTools
import com.example.nanoagent.model.LocalModelSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val modelStatus: String = "Подготовка локальной модели",
    val agentActivity: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String
)

enum class MessageRole { USER, ASSISTANT }

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val session = LocalModelSession(application)
    private val tools = AgentTools()
    private val _uiState = MutableStateFlow(ChatUiState(modelStatus = session.availability))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun send(text: String) {
        val request = text.trim()
        if (request.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(role = MessageRole.USER, content = request)
        val history = _uiState.value.messages.map { message ->
            ConversationMessage(
                role = if (message.role == MessageRole.USER) ConversationMessage.Role.USER else ConversationMessage.Role.ASSISTANT,
                content = message.content
            )
        } + ConversationMessage(ConversationMessage.Role.USER, request)

        _uiState.update { it.copy(messages = it.messages + userMessage, isGenerating = true) }
        viewModelScope.launch {
            val answer = runCatching { runAgent(history) }
                .getOrElse { error -> error.message ?: "Не удалось получить ответ локальной модели." }
                .ifBlank { "Локальная модель вернула пустой ответ. Попробуйте переформулировать запрос." }
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(role = MessageRole.ASSISTANT, content = answer),
                    isGenerating = false,
                    agentActivity = null
                )
            }
        }
    }

    fun startNewChat() {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(messages = emptyList()) }
    }

    private suspend fun runAgent(history: List<ConversationMessage>): String {
        var toolResult: String? = null
        repeat(MAX_AGENT_STEPS) {
            when (val result = session.generate(history, toolResult)) {
                is AgentModelResponse.Final -> return result.text
                is AgentModelResponse.ToolCall -> {
                    _uiState.update { it.copy(agentActivity = activityLabel(result.tool)) }
                    toolResult = runCatching { tools.execute(result.tool) }
                        .getOrElse { error -> "Ошибка инструмента: ${error.message ?: "неизвестная ошибка"}" }
                }
            }
        }
        return "Не удалось завершить действие. Попробуйте уточнить запрос."
    }

    private fun activityLabel(tool: AgentToolCall): String = when (tool) {
        AgentToolCall.CurrentTime -> "Проверяю время на устройстве"
        is AgentToolCall.Calculate -> "Выполняю вычисление"
    }

    private companion object { const val MAX_AGENT_STEPS = 2 }
}
