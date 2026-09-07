package com.example.nanoagent.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private val ScreenHorizontalPadding = 16.dp
private val ConversationMaxWidth = 720.dp
private val MessageMaxWidth = 440.dp
private const val ConversationMemoryLimit = 10_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var draft by remember { mutableStateOf("") }
    var showAgentSheet by remember { mutableStateOf(false) }

    val sendMessage = {
        if (draft.isNotBlank() && !state.isGenerating) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.send(draft)
            draft = ""
        }
    }
    val newChat: () -> Unit = {
        viewModel.startNewChat()
        scope.launch { snackbarHostState.showSnackbar("Начат новый чат") }
        Unit
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ChatTopAppBar(
                modelStatus = state.modelStatus,
                canStartNewChat = !state.isGenerating,
                onNewChat = newChat,
                onShowAgentDetails = { showAgentSheet = true }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ChatComposer(
                draft = draft,
                memoryCharacters = contextCharacters(state.messages),
                enabled = !state.isGenerating,
                onDraftChange = { draft = it },
                onSend = sendMessage,
                onNewChat = newChat
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            if (state.messages.isEmpty()) {
                EmptyConversation(
                    modifier = Modifier.widthIn(max = ConversationMaxWidth),
                    onSuggestion = { draft = it }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = ConversationMaxWidth)
                        .fillMaxSize()
                        .padding(horizontal = ScreenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(state.messages, key = { it.id }) { message -> ChatMessageItem(message) }
                    if (state.isGenerating) {
                        item { AgentActivityRow(state.agentActivity) }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    if (showAgentSheet) {
        AgentCapabilitiesSheet(onDismiss = { showAgentSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopAppBar(
    modelStatus: String,
    canStartNewChat: Boolean,
    onNewChat: () -> Unit,
    onShowAgentDetails: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nano Agent", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = modelStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        navigationIcon = {
            Surface(
                modifier = Modifier.padding(start = 12.dp).size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onShowAgentDetails) {
                Icon(Icons.Default.Settings, contentDescription = "Возможности агента")
            }
            IconButton(onClick = onNewChat, enabled = canStartNewChat) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат")
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun EmptyConversation(modifier: Modifier, onSuggestion: (String) -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Локальный помощник", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Диалог и доступные действия обрабатываются на устройстве.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        listOf(
            "Помоги составить план на день",
            "Который сейчас час?",
            "Вычисли (18 + 7) × 4"
        ).forEach { prompt ->
            AssistChip(onClick = { onSuggestion(prompt) }, label = { Text(prompt) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Text(
                text = "NANO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Surface(
            modifier = Modifier.widthIn(max = MessageMaxWidth),
            shape = if (isUser) {
                RoundedCornerShape(24.dp, 24.dp, 6.dp, 24.dp)
            } else {
                RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp)
            },
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 23.sp
            )
        }
    }
}

@Composable
private fun AgentActivityRow(activity: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        ThinkingWave()
        Spacer(Modifier.width(12.dp))
        Text(
            text = activity ?: "Nano Agent думает…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    memoryCharacters: Int,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onNewChat: () -> Unit
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .widthIn(max = ConversationMaxWidth)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = ScreenHorizontalPadding, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                ConversationMemoryButton(memoryCharacters = memoryCharacters, onNewChat = onNewChat)
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Напишите сообщение") },
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .clickable(enabled = enabled && draft.isNotBlank(), onClick = onSend),
                    shape = RoundedCornerShape(20.dp),
                    color = if (enabled && draft.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить сообщение",
                            tint = if (enabled && draft.isNotBlank()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationMemoryButton(memoryCharacters: Int, onNewChat: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val percent = (memoryCharacters * 100 / ConversationMemoryLimit).coerceIn(0, 100)
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = "Память диалога заполнена на $percent процентов" }
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("$percent", style = MaterialTheme.typography.labelSmall)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.width(224.dp).padding(16.dp)) {
                Text("Память диалога", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "$memoryCharacters из $ConversationMemoryLimit символов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                TextButton(onClick = { onNewChat(); expanded = false }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Новый чат")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentCapabilitiesSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Возможности агента", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Nano выполняет действия только локально и сообщает о них в чате.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            ListItem(
                headlineContent = { Text("Время на устройстве") },
                supportingContent = { Text("Уточняет текущие дату и время") },
                leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text("Безопасные вычисления") },
                supportingContent = { Text("Решает выражения с +, −, ×, ÷ и скобками") },
                leadingContent = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) }
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Готово") }
        }
    }
}

@Composable
private fun ThinkingWave() {
    val transition = rememberInfiniteTransition(label = "thinkingWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "wavePhase"
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.width(112.dp).height(24.dp)) {
        val path = Path()
        val middle = size.height / 2f
        for (index in 0..96) {
            val x = size.width * index / 96
            val y = middle + sin((index / 96f * 1.5f * 2 * PI + phase).toDouble()).toFloat() * size.height * .22f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}

private fun contextCharacters(messages: List<ChatMessage>): Int = messages.asReversed()
    .fold(0) { total, message ->
        (total + message.content.length.coerceAtMost(4_000)).coerceAtMost(ConversationMemoryLimit)
    }
