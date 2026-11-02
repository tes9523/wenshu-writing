package com.wenshu.writing.feature.ai

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.ChatSession
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.ui.util.formatShortTimestamp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    container: AppContainer,
    onOpenProviders: () -> Unit,
    onOpenPipeline: (novelId: Long, skillId: Long) -> Unit
) {
    val viewModel: AiChatViewModel = viewModel(
        factory = AiChatViewModel.factory(
            preferencesRepository = container.preferencesRepository,
            novelRepository = container.novelRepository,
            skillRepository = container.skillRepository,
            chatRepository = container.chatRepository,
            pipelineRepository = container.pipelineRepository,
            memoRepository = container.memoRepository,
            aiClient = container.aiClient
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var saveMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    var showNewSessionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.pipelineSkill) {
        val skill = state.pipelineSkill ?: return@LaunchedEffect
        viewModel.consumePipelineSkill()
        val novelId = state.selectedNovelId
        if (novelId == null) {
            snackbarHostState.showSnackbar("请先在「书稿」中新建一本书，再启用该流水线技能")
        } else {
            onOpenPipeline(novelId, skill.id)
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(state.toast) {
        val toast = state.toast ?: return@LaunchedEffect
        viewModel.consumeToast()
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(state.messages.size, state.isSending) {
        val itemCount = state.messages.size +
            (if (state.messages.isEmpty()) 1 else 0) +
            (if (state.isSending) 1 else 0)
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    val activeSession = state.sessions.firstOrNull { it.id == state.activeSessionId }
    val activeBookTitle = state.novels.firstOrNull { it.id == state.selectedNovelId }?.title

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(304.dp),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 20.dp)
                ) {
                    Text(
                        text = "会话",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showNewSessionDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("新建会话")
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (state.sessions.isEmpty()) {
                        Text(
                            text = "还没有会话。发送第一条消息后会自动创建并永久保存。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(items = state.sessions, key = { it.id }) { session ->
                                SessionDrawerItem(
                                    session = session,
                                    isActive = session.id == state.activeSessionId,
                                    bookTitle = session.bookId
                                        ?.let { id -> state.novels.firstOrNull { it.id == id }?.title },
                                    onClick = {
                                        viewModel.selectSession(session.id)
                                        scope.launch { drawerState.close() }
                                    },
                                    onDelete = { sessionToDelete = session }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Menu, contentDescription = "会话列表")
                        }
                    },
                    title = {
                        Text(
                            text = activeSession?.title ?: "AI 对话",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        if (state.messages.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearConversation) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "清空对话")
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            ) {
                ProviderSummaryBar(
                    providerName = state.providerName,
                    model = state.model,
                    onClick = onOpenProviders
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.messages.isEmpty()) {
                        item(key = "greeting") { GreetingBubble() }
                    }
                    items(items = state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isSending = state.isSending,
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.content))
                                Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                            },
                            onSave = { saveMessage = message },
                            onDelete = { messageToDelete = message },
                            onRegenerate = { viewModel.regenerateMessage(message.id) }
                        )
                    }
                    if (state.isSending) {
                        item(key = "typing") { TypingBubble() }
                    }
                }

                if (state.mentionQuery != null) {
                    SkillMentionPanel(
                        query = state.mentionQuery.orEmpty(),
                        results = state.mentionResults,
                        onSelect = viewModel::selectMentionSkill,
                        onDismiss = viewModel::dismissMentionPanel
                    )
                }

                InputBar(
                    input = state.input,
                    isSending = state.isSending,
                    onInputChange = viewModel::updateInput,
                    onSend = viewModel::sendMessage
                )
            }
        }
    }

    if (showNewSessionDialog) {
        NewSessionDialog(
            bookTitle = activeBookTitle,
            onBookSession = {
                viewModel.createSession(state.selectedNovelId)
                showNewSessionDialog = false
                scope.launch { drawerState.close() }
            },
            onGlobalSession = {
                viewModel.createSession(null)
                showNewSessionDialog = false
                scope.launch { drawerState.close() }
            },
            onDismiss = { showNewSessionDialog = false }
        )
    }

    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("删除会话") },
            text = { Text("确定删除该会话？删除后消息无法恢复") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(session.id)
                        sessionToDelete = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("取消") }
            }
        )
    }

    messageToDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("删除消息") },
            text = { Text("确定删除这条消息？仅从当前会话移除，书稿数据不受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(message.id)
                        messageToDelete = null
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) { Text("取消") }
            }
        )
    }

    saveMessage?.let { message ->
        SaveToBookDialog(
            novels = state.novels,
            presetNovelId = activeSession?.bookId ?: state.selectedNovelId,
            onDismiss = { saveMessage = null },
            onConfirm = { novelId, target ->
                viewModel.saveMessageContent(message.content, novelId, target) { success ->
                    if (success) saveMessage = null
                }
            }
        )
    }

    state.pendingOutline?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingOutline,
            title = { Text("书稿重名") },
            text = { Text("已存在书稿《${pending.title}》，请选择处理方式。") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingOutline(createNew = true) }) {
                    Text("新建另一本")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmPendingOutline(createNew = false) }) {
                    Text("打开已有书稿")
                }
            }
        )
    }
}

/** 抽屉中的会话条目：标题、创建时间、绑定书稿与删除按钮。 */
@Composable
private fun SessionDrawerItem(
    session: ChatSession,
    isActive: Boolean,
    bookTitle: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(formatShortTimestamp(session.createdAt))
                        append(" · ")
                        append(bookTitle?.let { "《$it》" } ?: "全局")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除会话",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun NewSessionDialog(
    bookTitle: String?,
    onBookSession: () -> Unit,
    onGlobalSession: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column {
                TextButton(
                    onClick = onBookSession,
                    enabled = bookTitle != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bookTitle?.let { "本书会话 ·《$it》" } ?: "本书会话（请先在书稿中新建书籍）",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onGlobalSession, modifier = Modifier.fillMaxWidth()) {
                    Text("全局会话（不绑定书稿）")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 保存弹窗：选择书稿与保存目标（新增章节 / 写入大纲 / 保存到备忘录）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveToBookDialog(
    novels: List<Novel>,
    presetNovelId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (novelId: Long, target: SaveTarget) -> Unit
) {
    var novelId by remember { mutableStateOf(presetNovelId) }
    var target by remember { mutableStateOf(SaveTarget.CHAPTER) }
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedNovel = novels.firstOrNull { it.id == novelId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存内容至书稿/备忘录") },
        text = {
            Column {
                if (novels.isEmpty()) {
                    Text(
                        text = "还没有书稿，请先在「书稿」中新建一本。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = !menuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedNovel?.title ?: "选择书稿",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("书稿") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            novels.forEach { novel ->
                                DropdownMenuItem(
                                    text = {
                                        Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    onClick = {
                                        novelId = novel.id
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("保存目标", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                SaveTarget.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { target = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = target == option,
                            onClick = { target = option }
                        )
                        Text(
                            text = when (option) {
                                SaveTarget.CHAPTER -> "新增章节"
                                SaveTarget.OUTLINE -> "写入大纲"
                                SaveTarget.MEMO -> "保存到备忘录"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { novelId?.let { onConfirm(it, target) } },
                enabled = novelId != null
            ) { Text("确认保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ProviderSummaryBar(
    providerName: String?,
    model: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "当前提供商：${providerName ?: "未配置"}｜模型：${model.ifBlank { "未配置" }}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "管理 AI 提供商",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun GreetingBubble() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "告诉我你想写的情节、场景或人物对白，我会直接给出可用的正文。" +
                        "生成后可通过气泡下方的【保存】存入书稿章节、大纲或备忘录。" +
                        "也可以直接粘贴完整大纲，我会帮你创建新书。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isSending: Boolean,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == ChatRole.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
            contentColor = when {
                message.isError -> MaterialTheme.colorScheme.onErrorContainer
                isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 18.dp
            ),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .animateContentSize()
            ) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!isUser && !message.isError) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MessageAction("复制", Icons.Outlined.ContentCopy, onClick = onCopy)
                        MessageAction("保存", Icons.Outlined.Save, onClick = onSave)
                        MessageAction("删除", Icons.Outlined.DeleteOutline, onClick = onDelete)
                        MessageAction(
                            "重新生成",
                            Icons.Outlined.Refresh,
                            enabled = !isSending,
                            onClick = onRegenerate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = Modifier.height(30.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "正在生成…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SkillMentionPanel(
    query: String,
    results: List<Skill>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "选择要激活的 Skill",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "关闭技能面板",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (results.isEmpty()) {
                Text(
                    text = "没有匹配的 Skill，可继续输入关键字筛选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(items = results, key = { it.id }) { skill ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(skill.id) }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = skill.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = skill.description.ifBlank { "暂无简介" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (skill.enabled) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "已激活",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = if (query.isBlank()) "输入文字可筛选 Skill" else "继续输入可筛选",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("描述你想写的情节，或直接粘贴完整大纲…") },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = input.trim().isNotEmpty() && !isSending,
                modifier = Modifier.size(48.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}
