package com.example.nanoagent.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nanoagent.data.ChatSession
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: AgentViewModel, modifier: Modifier = Modifier) {
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val status by viewModel.aiCoreStatus.collectAsState()
    val sessions by viewModel.chatSessions.collectAsState(emptyList())
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val language by viewModel.interfaceLanguage.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val thinkingDepth by viewModel.thinkingDepth.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }

    val send = {
        if (input.isNotBlank() && !isGenerating) {
            viewModel.sendMessage(input.trim())
            input = ""
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                sessions = sessions,
                selectedSessionId = currentSessionId,
                language = language,
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onOpenSession = {
                    viewModel.loadSession(it)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionToDelete = it }
            )
        }
    ) {
        androidx.compose.material3.Scaffold(
            modifier = modifier,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nano Agent", style = MaterialTheme.typography.titleLarge)
                            Text(status, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = localized(language, "Открыть чаты", "Open chats"))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = localized(language, "Настройки", "Settings"))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Composer(
                    value = input,
                    isGenerating = isGenerating,
                    language = language,
                    onValueChange = { input = it },
                    onSend = send
                )
            }
        ) { padding ->
            if (messages.isEmpty()) {
                EmptyConversation(
                    language = language,
                    modifier = Modifier.padding(padding),
                    onSuggestion = { input = it }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(messages, key = { it.id }) { message -> MessageBubble(message, language) }
                    if (isGenerating) item { GeneratingIndicator(language) }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            language = language, temperature = temperature, thinkingDepth = thinkingDepth,
            onDismiss = { showSettings = false },
            onTemperatureChange = viewModel::setModelTemperature,
            onDepthChange = viewModel::setModelThinkingDepth,
            onLanguageChange = {
                viewModel.setInterfaceLang(it)
                viewModel.setModelLang(it)
            }
        )
    }
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text(localized(language, "Удалить чат?", "Delete chat?")) },
            text = { Text(localized(language, "История этого чата будет удалена без возможности восстановления.", "This chat history will be permanently removed.")) },
            confirmButton = { TextButton(onClick = { viewModel.deleteSession(session.sessionId); sessionToDelete = null }) { Text(localized(language, "Удалить", "Delete")) } },
            dismissButton = { TextButton(onClick = { sessionToDelete = null }) { Text(localized(language, "Отмена", "Cancel")) } }
        )
    }
}

@Composable
private fun AppDrawer(
    sessions: List<ChatSession>, selectedSessionId: String?, language: String,
    onNewChat: () -> Unit, onOpenSession: (String) -> Unit, onDeleteSession: (ChatSession) -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Nano Agent", style = MaterialTheme.typography.titleLarge)
                    Text("On-device assistant", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(localized(language, "Новый чат", "New chat"))
            }
            Spacer(Modifier.height(20.dp))
            Text(localized(language, "НЕДАВНИЕ ЧАТЫ", "RECENT CHATS"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                items(sessions, key = { it.sessionId }) { session ->
                    NavigationDrawerItem(
                        label = { Text(session.title, maxLines = 1) },
                        selected = session.sessionId == selectedSessionId,
                        onClick = { onOpenSession(session.sessionId) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        badge = { IconButton(onClick = { onDeleteSession(session) }) { Icon(Icons.Default.Delete, localized(language, "Удалить", "Delete"), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyConversation(language: String, modifier: Modifier, onSuggestion: (String) -> Unit) {
    val suggestions = if (language == "Russian") listOf("Который сейчас час?", "Какая погода сегодня?", "Вычисли 36 × 14") else listOf("What time is it?", "What's the weather today?", "Calculate 36 × 14")
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        }
        Spacer(Modifier.height(24.dp))
        Text(localized(language, "Чем могу помочь?", "How can I help?"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(localized(language, "Я работаю локально и использую инструменты только при необходимости.", "I work locally and use tools only when they are needed."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        suggestions.forEach { suggestion ->
            AssistChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Composer(value: String, isGenerating: Boolean, language: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f),
                placeholder = { Text(localized(language, "Напишите сообщение", "Message Nano Agent")) },
                shape = RoundedCornerShape(24.dp), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent)
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(56.dp).clickable(enabled = value.isNotBlank() && !isGenerating, onClick = onSend),
                shape = RoundedCornerShape(20.dp),
                color = if (value.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, localized(language, "Отправить", "Send"), tint = if (value.isNotBlank() && !isGenerating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, language: String) {
    val isUser = message.sender == Sender.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        if (message.steps.isNotEmpty()) { ThinkingCard(message.steps, language); Spacer(Modifier.height(8.dp)) }
        if (message.content.isNotBlank() && message.content != "Thinking...") {
            Surface(
                shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.widthIn(max = 360.dp)
            ) { Text(message.content, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp) }
        }
    }
}

@Composable
private fun ThinkingCard(steps: List<AgentStep>, language: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.widthIn(max = 360.dp).clickable { expanded = !expanded }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                Text(localized(language, "Ход выполнения · ${steps.size}", "Activity · ${steps.size}"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown, null)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider()
                    steps.forEach { step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(if (step.type == StepType.TOOL_CALL) Icons.Default.PlayArrow else Icons.Default.Build, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp)); Column { Text(step.summary, style = MaterialTheme.typography.labelLarge); if (step.details.isNotBlank()) Text(step.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratingIndicator(language: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(localized(language, "Nano Agent думает…", "Nano Agent is thinking…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDialog(language: String, temperature: Float, thinkingDepth: Int, onDismiss: () -> Unit, onTemperatureChange: (Float) -> Unit, onDepthChange: (Int) -> Unit, onLanguageChange: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(localized(language, "Параметры агента", "Agent settings")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SettingSlider(localized(language, "Температура", "Temperature"), String.format("%.1f", temperature), temperature, 0f..1f, 9) { onTemperatureChange(it) }
            SettingSlider(localized(language, "Шаги рассуждения", "Reasoning steps"), thinkingDepth.toString(), thinkingDepth.toFloat(), 1f..10f, 8) { onDepthChange(it.toInt()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language == "Russian", onClick = { onLanguageChange("Russian") }, label = { Text("Русский") })
                FilterChip(selected = language == "English", onClick = { onLanguageChange("English") }, label = { Text("English") })
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(localized(language, "Готово", "Done")) } }
    )
}

@Composable
private fun SettingSlider(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(valueLabel, color = MaterialTheme.colorScheme.primary) }; Slider(value, onChange, valueRange = range, steps = steps) }
}

private fun localized(language: String, russian: String, english: String) = if (language == "Russian") russian else english
