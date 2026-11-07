package com.wenshu.writing.feature.ai

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.feature.brainstorm.BrainstormDialog
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.ChatSession
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.Skill
import com.wenshu.writing.ui.util.SoundPlayer
import com.wenshu.writing.ui.util.formatShortTimestamp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    container: AppContainer,
    viewModel: AiChatViewModel,
    onOpenProviders: () -> Unit,
    onOpenPipeline: (novelId: Long, skillId: Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by container.preferencesRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.wenshu.writing.domain.model.AppSettings()
    )
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var saveMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }
    var showBindBookDialog by remember { mutableStateOf(false) }
    var showBrainstorm by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    /** 点击页面空白时递增，用于清空文本选中状态。 */
    var selectionEpoch by remember { mutableStateOf(0) }
    /** 上一次呈现的会话，用于切换会话时强制定位到最新消息。 */
    var lastSessionId by remember { mutableStateOf<Long?>(null) }
    /** 是否是「离开后再次回到对话页」：是则先恢复上次的滚动位置，而不是跳到底部。 */
    val isReturning = remember { viewModel.hasOpenedChat.also { viewModel.markChatOpened() } }
    var restoreScrollPending by remember { mutableStateOf(isReturning) }
    val savedIndexOnEnter = remember { viewModel.savedScrollIndex }
    val savedOffsetOnEnter = remember { viewModel.savedScrollOffset }

    // 返回页面后等消息加载完成再恢复滚动位置，避免被自动滚动覆盖。
    LaunchedEffect(state.messages.size) {
        if (restoreScrollPending && state.messages.isNotEmpty()) {
            restoreScrollPending = false
            lastSessionId = state.activeSessionId
            listState.scrollToItem(
                savedIndexOnEnter.coerceIn(0, state.messages.lastIndex),
                savedOffsetOnEnter.coerceAtLeast(0)
            )
        }
    }

    // 持续记录滚动位置，供下次回到页面时恢复。
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> viewModel.saveScrollPosition(index, offset) }
    }

    /** 对话视口是否已滚动到最底部，用于控制「一键到底」浮动按钮。 */
    val isAtBottom by remember(listState) {
        derivedStateOf { !listState.canScrollForward }
    }

    /** 流式生成时是否自动跟随滚动到底部：用户上滑浏览历史时自动暂停。 */
    var autoFollow by remember { mutableStateOf(true) }

    // 根据用户手势维护自动跟随：滚动到底部→跟随；主动上滑→停止跟随，不强制下拉。
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward to listState.isScrollInProgress }
            .collect { (canForward, scrolling) ->
                autoFollow = when {
                    !canForward -> true
                    scrolling -> false
                    else -> autoFollow
                }
            }
    }

    /** 精确滚动到列表最底部：最后一条消息可能高于可视区，需要额外补一屏。 */
    suspend fun scrollToLatest() {
        val count = listState.layoutInfo.totalItemsCount
        if (count <= 0) return
        listState.scrollToItem(count - 1)
        listState.scrollBy(100_000f)
    }

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

    // 提示音：AI 回复完成（isSending 由 true 变 false）与弹出确认窗口时播放。
    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(state.isSending) {
        if (wasSending && !state.isSending && appSettings.soundEnabled) {
            SoundPlayer.play(context, appSettings.soundType)
        }
        wasSending = state.isSending
    }
    LaunchedEffect(state.pendingQuestion) {
        if (state.pendingQuestion != null && appSettings.soundEnabled) {
            SoundPlayer.play(context, appSettings.soundType)
        }
    }

    // 切换会话：消息流切换后多次兜底滚动，确保定位到目标会话底部（最新内容）。
    LaunchedEffect(state.activeSessionId) {
        if (lastSessionId == state.activeSessionId) return@LaunchedEffect
        lastSessionId = state.activeSessionId
        if (restoreScrollPending) return@LaunchedEffect
        autoFollow = true
        kotlinx.coroutines.delay(50)
        repeat(8) {
            scrollToLatest()
            if (!listState.canScrollForward) return@LaunchedEffect
            kotlinx.coroutines.delay(60)
        }
    }

    // 新消息 / 流式输出 / 提问卡片更新：仅在自动跟随开启时滚到底部。
    LaunchedEffect(state.messages, state.isSending, state.pendingQuestion, state.streamingText) {
        if (restoreScrollPending) return@LaunchedEffect
        if (!autoFollow) return@LaunchedEffect
        scrollToLatest()
    }

    val activeSession = state.sessions.firstOrNull { it.id == state.activeSessionId }
    val latestUserMessageId = state.messages.lastOrNull { it.role == ChatRole.USER }?.id

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
                        onClick = { viewModel.createSession() },
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
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 16.dp, top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Skill 调试日志", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "记录 Agent 轮次与工具执行",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.skillLogEnabled,
                            onCheckedChange = viewModel::setSkillLogEnabled
                        )
                    }
                    if (state.skillLogEnabled) {
                        TextButton(
                            onClick = { showLogDialog = true },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text("查看日志（${state.skillLogs.size}）")
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeSession?.title ?: "AI 对话",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(10.dp))
                            ModelStatusChip(
                                model = state.model,
                                isSending = state.isSending,
                                isSearching = state.isSearching,
                                lastMessageIsError = state.messages.lastOrNull()?.isError == true,
                                onClick = onOpenProviders
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBrainstorm = true }) {
                            Icon(
                                Icons.Outlined.Lightbulb,
                                contentDescription = "脑洞生成器"
                            )
                        }
                        IconButton(onClick = { showBindBookDialog = true }) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = "绑定书稿",
                                tint = if (state.activeSessionId != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                }
                            )
                        }
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
                    .pointerInput(Unit) {
                        // 只处理"未被控件消费"的空白点击：收起输入法并退出文本选中态。
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            val up = waitForUpOrCancellation()
                            // 只有真正落在空白区域（down/up 均未被控件消费）才收起键盘与选中态，
                            // 否则点击气泡按钮时也会触发重组，长文本下会让按钮表现为「无响应」。
                            if (up != null && !down.isConsumed && !up.isConsumed) {
                                keyboardController?.hide()
                                selectionEpoch++
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
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
                                canEdit = message.id == latestUserMessageId,
                                selectionEpoch = selectionEpoch,
                                chatFontSize = appSettings.chatFontSize,
                                skillNames = state.skills.map { it.name },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(message.content))
                                    Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
                                },
                                onSave = { saveMessage = message },
                                onDelete = { messageToDelete = message },
                                onRegenerate = { viewModel.regenerateMessage(message.id) },
                                onEdit = { viewModel.startEditingMessage(message) }
                            )
                        }
                        if (state.isSending) {
                            item(key = "typing") {
                                TypingBubble(
                                    isSearching = state.isSearching,
                                    streamingText = state.streamingText,
                                    streamingReasoning = state.streamingReasoning,
                                    activeToolName = state.activeToolName,
                                    selectionEpoch = selectionEpoch,
                                    chatFontSize = appSettings.chatFontSize,
                                    skillNames = state.skills.map { it.name }
                                )
                            }
                        }
                        // 技能待确认项：以对话气泡形式内嵌展示，看完内容再选择，不再弹遮挡弹窗。
                        state.pendingQuestion?.let { question ->
                            item(key = "pending_question") {
                                PendingQuestionBubble(
                                    question = question,
                                    onAnswer = viewModel::answerQuestion
                                )
                            }
                        }
                    }

                    if (!isAtBottom) {
                        Surface(
                            onClick = {
                                autoFollow = true
                                scope.launch { scrollToLatest() }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 3.dp,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 10.dp)
                                .size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "↓",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                    isEditing = state.editingMessageId != null,
                    webSearchEnabled = state.webSearchEnabled,
                    skillNames = state.skills.map { it.name },
                    onInputChange = viewModel::updateInput,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration,
                    onCancelEdit = viewModel::cancelEditingMessage,
                    onToggleSearch = viewModel::setWebSearchEnabled
                )
            }
        }
    }

    if (showBrainstorm) {
        BrainstormDialog(
            container = container,
            onDismiss = { showBrainstorm = false }
        )
    }

    if (state.showBackgroundGuide) {
        BackgroundKeepAliveDialog(onDismiss = { viewModel.dismissBackgroundGuide() })
    }

    // 智能识别到新书内容时的轻量确认，拒绝后内容仅保留在对话中。
    state.pendingNovelDraft?.let { draft ->
        NovelDraftConfirmDialog(
            draft = draft,
            onConfirm = viewModel::confirmNovelDraft,
            onDismiss = viewModel::dismissNovelDraft
        )
    }

    if (showBindBookDialog) {
        AlertDialog(
            onDismissRequest = { showBindBookDialog = false },
            title = { Text("绑定书稿") },
            text = {
                Column {
                    Text(
                        text = "当前会话：${activeSession?.title ?: "无"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(items = state.novels, key = { it.id }) { novel ->
                            val isBound = state.activeSessionBookId == novel.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.bindCurrentSessionBook(novel.id)
                                        showBindBookDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = novel.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isBound) {
                                    Icon(
                                        Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        item(key = "unbind") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.bindCurrentSessionBook(null)
                                        showBindBookDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (state.activeSessionBookId == null) "保持全局会话" else "解绑书稿（变回全局会话）",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBindBookDialog = false }) { Text("关闭") }
            }
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
            text = { Text("确定要删除这条消息吗？仅从当前会话移除，书稿数据不受影响。") },
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

    state.pendingDoc?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingDoc,
            title = { Text("书稿重名") },
            text = { Text("已存在书稿《${pending.title}》，请选择处理方式。") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingDoc(createNew = true) }) {
                    Text("新建另一本")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmPendingDoc(createNew = false) }) {
                    Text("打开已有书稿")
                }
            }
        )
    }

    state.pendingTitleInput?.let { doc ->
        var titleInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::dismissTitleInput,
            title = { Text("请输入书名") },
            text = {
                Column {
                    Text("未能从文档中识别出书名，请手动输入后继续落库。")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("书名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (doc.hasAnyContent) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = buildString {
                                append("将写入：")
                                val parts = mutableListOf<String>()
                                if (!doc.outline.isNullOrBlank()) parts.add("大纲")
                                if (doc.characters.isNotEmpty()) parts.add("人物")
                                if (doc.worldSettings.isNotEmpty()) parts.add("世界观")
                                if (doc.custom.isNotEmpty()) parts.add("自定义设定")
                                append(parts.joinToString("、"))
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmTitleInput(titleInput) },
                    enabled = titleInput.isNotBlank()
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissTitleInput) { Text("取消") }
            }
        )
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("Skill 执行日志") },
            text = {
                if (state.skillLogs.isEmpty()) {
                    Text("暂无日志。开启开关后，发送消息即可记录 Agent 轮次与工具调用。")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(items = state.skillLogs) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearSkillLogs() }) { Text("清空") }
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

/** 顶部模型状态：模型名称 + 状态圆点（绿=正常，黄=生成中，红=异常），点击进入提供商管理。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelStatusChip(
    model: String,
    isSending: Boolean,
    isSearching: Boolean,
    lastMessageIsError: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when {
        isSending || isSearching -> Color(0xFFF59E0B)
        lastMessageIsError -> Color(0xFFE53935)
        else -> Color(0xFF43A047)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = model.ifBlank { "未配置模型" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                        "生成后可通过气泡下方的图标存入书稿章节、大纲或备忘录。" +
                        "也可以直接粘贴完整文档（含书名、大纲、人物、世界观等），我会自动解析并创建新书。",
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
    canEdit: Boolean,
    selectionEpoch: Int,
    chatFontSize: Int,
    skillNames: List<String>,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit
) {
    val isUser = message.role == ChatRole.USER
    var menuExpanded by remember { mutableStateOf(false) }
    var showFullText by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (isUser) {
            // 用户气泡支持长按选段复制，操作菜单改由气泡左侧的更多按钮唤出，避免手势冲突。
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "消息操作",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        }
                    )
                    // 仅当前会话最新一条用户消息允许修改，历史消息隐藏该入口。
                    if (canEdit) {
                        DropdownMenuItem(
                            text = { Text("修改") },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
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
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // key 随空白点击变化，重新创建 SelectionContainer 以退出文本选中态。
                key(selectionEpoch) {
                    // 超长回复只渲染前 1500 字预览，全文在独立弹窗中按分片懒加载，
                    // 避免单个超大 Text 撑高气泡、阻塞下方按钮的点击事件。
                    val isLongMessage = message.content.length > LONG_MESSAGE_PREVIEW_CHARS
                    val preview = remember(message.content, isLongMessage) {
                        if (isLongMessage) {
                            message.content.take(LONG_MESSAGE_PREVIEW_CHARS) + "…"
                        } else {
                            message.content
                        }
                    }
                    val mentionColor = MaterialTheme.colorScheme.primary
                    val bodyText = remember(preview, skillNames, mentionColor) {
                        skillMentionAnnotatedString(preview, skillNames, mentionColor)
                    }
                    SelectionContainer {
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = chatFontSize.sp
                            )
                        )
                    }
                    if (isLongMessage) {
                        TextButton(
                            onClick = { showFullText = true },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                        ) {
                            Text("查看全文（共 ${message.content.length} 字）")
                        }
                    }
                }
                if (message.isError) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onRegenerate,
                            enabled = !isSending
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("重新生成")
                        }
                    }
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
                        MessageAction(Icons.Outlined.ContentCopy, onClick = onCopy)
                        MessageAction(Icons.Outlined.Save, onClick = onSave)
                        MessageAction(Icons.Outlined.DeleteOutline, onClick = onDelete)
                        MessageAction(
                            Icons.Outlined.Refresh,
                            enabled = !isSending,
                            onClick = onRegenerate
                        )
                    }
                }
            }
        }
    }

    if (showFullText) {
        FullMessageDialog(
            content = message.content,
            chatFontSize = chatFontSize,
            onCopy = onCopy,
            onDismiss = { showFullText = false }
        )
    }
}

@Composable
private fun NovelDraftConfirmDialog(
    draft: DetectedNovelDraft,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AutoStories, contentDescription = null) },
        title = { Text("检测到新书内容") },
        text = {
            Column {
                Text(
                    text = buildString {
                        append("《").append(draft.title).append("》")
                        if (draft.genre.isNotBlank()) append(" · ").append(draft.genre)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (draft.chapters.isNotEmpty()) {
                        "识别到 ${draft.chapters.size} 个章节，是否创建书稿并写入正文？"
                    } else {
                        "识别到完整书籍设定，是否创建书稿并写入大纲？"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (draft.preview.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = draft.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("是，创建书稿") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("否，仅保留对话") }
        }
    )
}

/** 长消息全文阅读：分片懒加载，仅渲染可见区域，超大文本也不会卡住交互。 */
@Composable
private fun FullMessageDialog(
    content: String,
    chatFontSize: Int,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    val chunks = remember(content) { splitMessageChunks(content) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "消息全文",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "共 ${content.length} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = chunks) { chunk ->
                        SelectionContainer {
                            Text(
                                text = chunk,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = chatFontSize.sp
                                )
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCopy) { Text("复制全文") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun MessageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // 统一 40dp 触控区域，避免小按钮在部分机型上难以点中。
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
    }
}

/** 技能提问卡片：预设选项 + 自定义输入，生成过程中即可作答。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingQuestionBubble(
    question: PendingQuestion,
    onAnswer: (String) -> Unit
) {
    var selected by remember(question.question) { mutableStateOf<String?>(null) }
    var custom by remember(question.question) { mutableStateOf("") }
    // 只有一个选项时默认选中，减少一次点击。
    LaunchedEffect(question.options) {
        if (question.options.size == 1 && selected == null) {
            selected = question.options.first().label
        }
    }
    val answer = custom.trim().ifBlank { selected.orEmpty() }

    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Outlined.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = question.header.ifBlank { "需要确认" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = question.question,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (question.options.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        question.options.forEach { option ->
                            val active = selected == option.label
                            Surface(
                                onClick = { selected = option.label; custom = "" },
                                shape = RoundedCornerShape(20.dp),
                                color = if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (active) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = {
                        custom = it
                        if (it.isNotBlank()) selected = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入自定义答案…", style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 3
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (question.options.isEmpty()) "" else "可直接点选或自行输入",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onAnswer("取消") }) { Text("跳过") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { if (answer.isNotBlank()) onAnswer(answer) },
                        enabled = answer.isNotBlank()
                    ) {
                        Text(if (question.options.size > 1) "下一题" else "确定")
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(
    isSearching: Boolean,
    streamingText: String,
    streamingReasoning: String,
    activeToolName: String?,
    selectionEpoch: Int,
    chatFontSize: Int,
    skillNames: List<String>
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // 思考过程流式展示：浅色弱化字体，正文开始输出后由 ViewModel 清空，不会保存。
                if (streamingReasoning.isNotBlank() && streamingText.isBlank()) {
                    Text(
                        text = streamingReasoning,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = (chatFontSize - 2).coerceAtLeast(10).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (streamingText.isNotBlank()) {
                    // 流式阶段只渲染尾部片段，超长正文生成时保持滚动与输入流畅。
                    val visibleStreaming = remember(streamingText) {
                        if (streamingText.length > STREAMING_PREVIEW_CHARS) {
                            "…" + streamingText.takeLast(STREAMING_PREVIEW_CHARS)
                        } else {
                            streamingText
                        }
                    }
                    key(selectionEpoch) {
                        SelectionContainer {
                            val mentionColor = MaterialTheme.colorScheme.primary
                            val bodyText = remember(visibleStreaming, skillNames, mentionColor) {
                                skillMentionAnnotatedString(visibleStreaming, skillNames, mentionColor)
                            }
                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = chatFontSize.sp
                                )
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(
                        top = if (streamingText.isBlank() && streamingReasoning.isBlank()) 0.dp else 6.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isSearching -> "联网检索中…"
                            activeToolName != null -> "执行技能工具：$activeToolName…"
                            streamingText.isBlank() && streamingReasoning.isNotBlank() -> "思考中…"
                            else -> "正在生成…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

private val skillMentionRegex = Regex("""@([\w\-\u4e00-\u9fa5]+)""")

/** 构建 @技能名 高亮加粗的正文，用于 AI 气泡与流式文本展示。 */
private fun skillMentionAnnotatedString(
    text: String,
    skillNames: List<String>,
    color: Color
): AnnotatedString {
    if (skillNames.isEmpty() || !text.contains('@')) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text)
    skillMentionRegex.findAll(text).forEach { match ->
        val name = match.groupValues.getOrNull(1) ?: return@forEach
        if (skillNames.any { it.equals(name, ignoreCase = true) }) {
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, color = color),
                match.range.first,
                match.range.last + 1
            )
        }
    }
    return builder.toAnnotatedString()
}

/** 把输入框中 @技能名 高亮加粗，用来标识本次要调用的技能。 */
private class SkillMentionTransformation(
    private val skillNames: List<String>,
    private val color: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            skillMentionAnnotatedString(text.text, skillNames, color),
            OffsetMapping.Identity
        )
    }
}

@Composable
private fun InputBar(
    input: String,
    isSending: Boolean,
    isEditing: Boolean,
    webSearchEnabled: Boolean,
    skillNames: List<String>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onCancelEdit: () -> Unit,
    onToggleSearch: (Boolean) -> Unit
) {
    // TextFieldValue 版本：外部回填（@技能名、编辑消息）时把光标移到文本末尾；
    // 用户自己输入时保持光标位置不变。
    var fieldValue by remember { mutableStateOf(TextFieldValue(input, TextRange(input.length))) }
    LaunchedEffect(input) {
        if (input != fieldValue.text) {
            fieldValue = TextFieldValue(input, TextRange(input.length))
        }
    }
    val mentionColor = MaterialTheme.colorScheme.primary
    val mentionTransformation = remember(skillNames, mentionColor) {
        SkillMentionTransformation(skillNames, mentionColor)
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "正在编辑消息",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancelEdit) {
                        Text("撤回修改")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (webSearchEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable { onToggleSearch(!webSearchEnabled) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        Icons.Outlined.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (webSearchEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (webSearchEnabled) "联网搜索·开" else "联网搜索·关",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (webSearchEnabled) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { newValue ->
                        fieldValue = newValue
                        if (newValue.text != input) onInputChange(newValue.text)
                    },
                    visualTransformation = mentionTransformation,
                    placeholder = { Text("写下你的故事…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                if (isSending) {
                    Surface(
                        onClick = onStop,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.onPrimary)
                            )
                        }
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = input.trim().isNotEmpty(),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "发送"
                        )
                    }
                }
            }
        }
    }
}

// ---- 长消息分片渲染 ----

/** 气泡内直接渲染的字数上限，超出后只显示预览并提供「查看全文」。 */
private const val LONG_MESSAGE_PREVIEW_CHARS = 1500

/** 流式生成时气泡内展示的尾部字数，避免超长正文持续撑高布局。 */
private const val STREAMING_PREVIEW_CHARS = 3000

/** 全文弹窗中每个分片的大致字数上限。 */
private const val MAX_CHARS_PER_CHUNK = 1500

/**
 * 把长消息按行切分为若干分片，避免单个超大 Text 一次性布局。
 * 分片按原文顺序拼接后与原文一致（保留换行）。
 */
private fun splitMessageChunks(text: String): List<String> {
    if (text.length <= MAX_CHARS_PER_CHUNK) return listOf(text)
    val chunks = mutableListOf<String>()
    val buffer = StringBuilder()

    fun flush() {
        if (buffer.isNotEmpty()) {
            chunks += buffer.toString()
            buffer.clear()
        }
    }

    text.split('\n').forEachIndexed { index, line ->
        val segment = if (index == 0) line else "\n$line"
        if (segment.length > MAX_CHARS_PER_CHUNK) {
            flush()
            var start = 0
            while (start < segment.length) {
                var end = (start + MAX_CHARS_PER_CHUNK).coerceAtMost(segment.length)
                // 避免把代理对（emoji 等）从中间截断。
                if (end < segment.length && segment[end - 1].isHighSurrogate()) end++
                chunks += segment.substring(start, end)
                start = end
            }
        } else {
            if (buffer.length + segment.length > MAX_CHARS_PER_CHUNK) flush()
            buffer.append(segment)
        }
    }
    flush()
    return if (chunks.isEmpty()) listOf(text) else chunks
}
