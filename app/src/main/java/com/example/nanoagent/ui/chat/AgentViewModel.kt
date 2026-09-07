package com.example.nanoagent.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nanoagent.NanoAgentApp
import com.example.nanoagent.agent.AgentEngine
import com.example.nanoagent.agent.ToolRegistry
import com.example.nanoagent.data.ChatMessageEntity
import com.example.nanoagent.data.ChatSession
import com.example.nanoagent.data.WikiEntry
import com.example.nanoagent.model.GeminiNanoClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: Sender,
    val content: String,
    val steps: List<AgentStep> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class Sender { USER, AGENT }

data class AgentStep(
    val id: String = UUID.randomUUID().toString(),
    val type: StepType,
    val summary: String,
    val details: String = "",
    val isRunning: Boolean = false
)

enum class StepType { THOUGHT, TOOL_CALL, TOOL_RESPONSE }

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NanoAgentApp
    private val client = GeminiNanoClient(app)
    private val registry = ToolRegistry(app, app.database.knowledgeBaseDao())
    private val engine = AgentEngine(client, registry)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _aiCoreStatus = MutableStateFlow("Initializing AICore...")
    val aiCoreStatus: StateFlow<String> = _aiCoreStatus.asStateFlow()

    // History and sessions
    val chatSessions = app.database.chatHistoryDao().getAllSessionsFlow()
    
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    // Configuration / Settings flows
    private val _temperature = MutableStateFlow(0.4f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()
    private val _thinkingDepth = MutableStateFlow(5)
    val thinkingDepth: StateFlow<Int> = _thinkingDepth.asStateFlow()
    private val _modelLanguage = MutableStateFlow("Russian")
    val modelLanguage: StateFlow<String> = _modelLanguage.asStateFlow()
    private val _interfaceLanguage = MutableStateFlow("Russian")
    val interfaceLanguage: StateFlow<String> = _interfaceLanguage.asStateFlow()

    val wikiEntries = app.database.knowledgeBaseDao().getAllEntriesFlow()

    init {
        checkAICoreStatus()
        startNewChat() // Initialize with a fresh session
    }

    fun checkAICoreStatus() {
        _aiCoreStatus.value = if (client.isInitialized) {
            "AICore Ready (Gemini Nano 4B)"
        } else {
            "AICore Unavailable: ${client.initializationError ?: "Device not supported (Using Simulation fallback)"}"
        }
    }

    // Settings adjustments
    fun setModelTemperature(temp: Float) {
        val bounded = temp.coerceIn(0f, 1f)
        _temperature.value = bounded
        client.temperature = bounded
    }

    fun setModelThinkingDepth(depth: Int) {
        val bounded = depth.coerceIn(1, 10)
        _thinkingDepth.value = bounded
        engine.maxIterations = bounded
    }

    fun setModelLang(lang: String) {
        _modelLanguage.value = lang
        engine.targetLanguage = lang
    }

    fun setInterfaceLang(lang: String) {
        _interfaceLanguage.value = lang
    }

    // Chat sessions lifecycle
    fun startNewChat() {
        viewModelScope.launch {
            val defaultTitle = if (_interfaceLanguage.value == "Russian") "Новый чат" else "New Chat"
            val newSession = ChatSession(title = defaultTitle)
            app.database.chatHistoryDao().insertSession(newSession)
            _currentSessionId.value = newSession.sessionId
            _messages.value = emptyList()
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            _currentSessionId.value = sessionId
            val dbMessages = app.database.chatHistoryDao().getMessagesForSession(sessionId)
            val chatMsgs = dbMessages.map { entity ->
                ChatMessage(
                    id = entity.id.toString(),
                    sender = Sender.valueOf(entity.sender),
                    content = entity.content,
                    steps = deserializeSteps(entity.stepsJson),
                    timestamp = entity.timestamp
                )
            }
            _messages.value = chatMsgs
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            app.database.chatHistoryDao().deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                // If active session was deleted, start new one
                val allSessions = chatSessions.first()
                val nextSession = allSessions.firstOrNull { it.sessionId != sessionId }
                if (nextSession != null) {
                    loadSession(nextSession.sessionId)
                } else {
                    startNewChat()
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isGenerating.value) return

        val sessionId = _currentSessionId.value ?: return

        // 1. Add User Message to UI & DB
        val userMsg = ChatMessage(sender = Sender.USER, content = text)
        _messages.update { it + userMsg }
        saveMessageToDb(sessionId, userMsg)

        // Automatically update session title on first message
        if (_messages.value.size == 1) {
            viewModelScope.launch {
                val title = if (text.length > 25) text.take(25) + "..." else text
                app.database.chatHistoryDao().updateSessionTitle(sessionId, title)
            }
        }

        _isGenerating.value = true

        // 2. Start Agent Loop
        viewModelScope.launch {
            val steps = mutableListOf<AgentStep>()
            val agentMsgId = UUID.randomUUID().toString()

            // Put temporary placeholder message
            _messages.update { list ->
                list + ChatMessage(id = agentMsgId, sender = Sender.AGENT, content = "Thinking...")
            }

            try {
                engine.runAgent(text).collect { state ->
                    when (state) {
                        is AgentEngine.AgentState.Running -> {
                            steps.add(AgentStep(type = StepType.THOUGHT, summary = "Reasoning", details = state.thought))
                            updateAgentMessage(agentMsgId, "Reasoning...", steps)
                        }
                        is AgentEngine.AgentState.ExecutingTool -> {
                            val argsStr = state.args.entries.joinToString { "${it.key}: ${it.value}" }
                            steps.add(
                                AgentStep(
                                    type = StepType.TOOL_CALL,
                                    summary = "Running ${state.toolName}",
                                    details = "Arguments: $argsStr",
                                    isRunning = true
                                )
                            )
                            updateAgentMessage(agentMsgId, "Calling tool: ${state.toolName}...", steps)
                        }
                        is AgentEngine.AgentState.ToolResult -> {
                            val lastIdx = steps.indexOfLast { it.type == StepType.TOOL_CALL && it.summary.contains(state.toolName) }
                            if (lastIdx != -1) {
                                val callStep = steps[lastIdx]
                                steps[lastIdx] = callStep.copy(isRunning = false)
                            }
                            steps.add(
                                AgentStep(
                                    type = StepType.TOOL_RESPONSE,
                                    summary = "${state.toolName} output",
                                    details = state.result
                                )
                            )
                            updateAgentMessage(agentMsgId, "Received output from ${state.toolName}...", steps)
                        }
                        is AgentEngine.AgentState.Finished -> {
                            val finalMsg = ChatMessage(id = agentMsgId, sender = Sender.AGENT, content = state.finalAnswer, steps = steps.toList())
                            updateAgentMessage(agentMsgId, state.finalAnswer, steps)
                            saveMessageToDb(sessionId, finalMsg)
                        }
                        is AgentEngine.AgentState.Error -> {
                            val errorMsg = ChatMessage(id = agentMsgId, sender = Sender.AGENT, content = "Error: ${state.message}", steps = steps.toList())
                            updateAgentMessage(agentMsgId, "Error: ${state.message}", steps)
                            saveMessageToDb(sessionId, errorMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                val message = e.message ?: "Unknown error"
                val failMsg = ChatMessage(id = agentMsgId, sender = Sender.AGENT, content = "Execution failed: $message", steps = steps.toList())
                updateAgentMessage(agentMsgId, "Execution failed: $message", steps)
                saveMessageToDb(sessionId, failMsg)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun updateAgentMessage(id: String, content: String, steps: List<AgentStep>) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id == id) {
                    msg.copy(content = content, steps = steps.toList())
                } else {
                    msg
                }
            }
        }
    }

    private fun saveMessageToDb(sessionId: String, msg: ChatMessage) {
        viewModelScope.launch {
            app.database.chatHistoryDao().insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    sender = msg.sender.name,
                    content = msg.content,
                    stepsJson = serializeSteps(msg.steps),
                    timestamp = msg.timestamp
                )
            )
        }
    }

    private fun serializeSteps(steps: List<AgentStep>): String {
        val array = JSONArray()
        steps.forEach { step ->
            val obj = JSONObject().apply {
                put("type", step.type.name)
                put("summary", step.summary)
                put("details", step.details)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeSteps(json: String): List<AgentStep> {
        val list = mutableListOf<AgentStep>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AgentStep(
                        type = StepType.valueOf(obj.getString("type")),
                        summary = obj.getString("summary"),
                        details = obj.optString("details", "")
                    )
                )
            }
        } catch (e: Exception) {
            // fallback
        }
        return list
    }

    fun addWikiEntry(title: String, content: String) {
        viewModelScope.launch {
            app.database.knowledgeBaseDao().insertEntry(
                WikiEntry(title = title, content = content)
            )
        }
    }

    fun deleteWikiEntry(entry: WikiEntry) {
        viewModelScope.launch {
            app.database.knowledgeBaseDao().deleteEntry(entry)
        }
    }

    fun clearChat() {
        val sessionId = _currentSessionId.value
        if (sessionId != null) {
            viewModelScope.launch {
                app.database.chatHistoryDao().deleteMessagesBySessionId(sessionId)
                _messages.value = emptyList()
            }
        }
    }
}
