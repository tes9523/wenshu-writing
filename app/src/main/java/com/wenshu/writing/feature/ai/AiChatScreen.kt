package com.wenshu.writing.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ApiConnStatus
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.Novel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(container: AppContainer) {
    val viewModel: AiChatViewModel = viewModel(
        factory = AiChatViewModel.factory(
            preferencesRepository = container.preferencesRepository,
            novelRepository = container.novelRepository,
            aiClient = container.aiClient
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(state.messages.size, state.isSending) {
        val itemCount = state.messages.size +
            (if (state.messages.isEmpty()) 1 else 0) +
            (if (state.isSending) 1 else 0)
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("AI 对话", style = MaterialTheme.typography.titleLarge) },
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
            ApiConfigPanel(
                state = state,
                onToggle = viewModel::toggleConfigPanel,
                onBaseUrlChange = viewModel::updateBaseUrl,
                onApiKeyChange = viewModel::updateApiKey,
                onModelChange = viewModel::updateModel,
                onProfileNameChange = viewModel::updateProfileName,
                onSave = viewModel::saveConfig,
                onRefreshModels = viewModel::refreshModels,
                onSelectProfile = viewModel::selectProfile,
                onNewProfile = viewModel::startNewProfile,
                onDeleteProfile = viewModel::deleteActiveProfile
            )

            InsertTargetSelector(
                novels = state.novels,
                chapters = state.chapters,
                selectedNovelId = state.selectedNovelId,
                selectedChapterId = state.selectedChapterId,
                onSelectNovel = viewModel::selectNovel,
                onSelectChapter = viewModel::selectChapter
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
                        canInsert = state.selectedChapterId != null,
                        onInsert = { viewModel.insertToChapter(message.content) }
                    )
                }
                if (state.isSending) {
                    item(key = "typing") { TypingBubble() }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiConfigPanel(
    state: AiChatUiState,
    onToggle: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onProfileNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onRefreshModels: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onNewProfile: () -> Unit,
    onDeleteProfile: () -> Unit
) {
    var apiKeyVisible by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }

    val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
    val statusColor = apiStatusColor(state.connectionStatus)
    val modelOptions = state.availableModels.ifEmpty { AiConfig.PRESET_MODELS }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("API 配置", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = buildString {
                                append(activeProfile?.name ?: "未保存的新配置")
                                append(" · ")
                                append(state.model.ifBlank { "未选择模型" })
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (state.isConfigExpanded) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        },
                        contentDescription = if (state.isConfigExpanded) "收起" else "展开"
                    )
                }
            }

            if (state.isConfigExpanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── 配置集切换区 ──
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("配置集切换", Modifier.weight(1f))
                    TextButton(onClick = onNewProfile) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("新增")
                    }
                }
                if (state.profiles.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExposedDropdownMenuBox(
                            expanded = profileMenuExpanded,
                            onExpandedChange = { profileMenuExpanded = !profileMenuExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = activeProfile?.name ?: "未保存的新配置",
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                label = { Text("已保存配置集") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = profileMenuExpanded
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = profileMenuExpanded,
                                onDismissRequest = { profileMenuExpanded = false }
                            ) {
                                state.profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    profile.name,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    profile.model.ifBlank { "未设置模型" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            if (profile.id == state.activeProfileId) {
                                                Text(
                                                    "当前",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            profileMenuExpanded = false
                                            onSelectProfile(profile.id)
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onDeleteProfile) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "删除当前配置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = state.profileName,
                    onValueChange = onProfileNameChange,
                    label = { Text("备注名称") },
                    placeholder = { Text("如：智谱 GLM / 备用服务") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── 服务地址 & 密钥区 ──
                Spacer(Modifier.height(16.dp))
                SectionTitle("服务地址 & 密钥")
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("API 地址（OpenAI 兼容）") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                imageVector = if (apiKeyVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (apiKeyVisible) "隐藏" else "显示"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                ApiStatusStrip(
                    status = state.connectionStatus,
                    message = state.connectionMessage
                )

                // ── 模型选择区 ──
                Spacer(Modifier.height(16.dp))
                SectionTitle("模型选择")
                Spacer(Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = !modelMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = onModelChange,
                        label = { Text("模型 ID") },
                        placeholder = { Text("留空可从列表选择，或手动输入") },
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        modelOptions.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                onClick = {
                                    onModelChange(model)
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRefreshModels,
                    enabled = !state.isFetchingModels,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isFetchingModels) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isFetchingModels) "正在拉取模型…" else "刷新模型列表")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.availableModels.isNotEmpty()) {
                        "已获取 ${state.availableModels.size} 个可用模型，可下拉选择；也支持手动输入"
                    } else {
                        "支持手动输入模型 ID，适配不提供 models 接口的服务商"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ── 保存 ──
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onSave) { Text("保存配置") }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "保存后自动测试连接，仅存本机",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
private fun apiStatusColor(status: ApiConnStatus): Color = when (status) {
    ApiConnStatus.SUCCESS -> Color(0xFF2E7D32)
    ApiConnStatus.FAILURE -> MaterialTheme.colorScheme.error
    ApiConnStatus.TIMEOUT, ApiConnStatus.CHECKING -> Color(0xFFF9A825)
    ApiConnStatus.UNVERIFIED -> Color(0xFF9E9E9E)
}

private fun apiStatusLabel(status: ApiConnStatus): String = when (status) {
    ApiConnStatus.SUCCESS -> "连接成功"
    ApiConnStatus.FAILURE -> "连接失败"
    ApiConnStatus.TIMEOUT -> "请求超时"
    ApiConnStatus.CHECKING -> "正在校验"
    ApiConnStatus.UNVERIFIED -> "未校验"
}

@Composable
private fun ApiStatusStrip(status: ApiConnStatus, message: String) {
    val accent = apiStatusColor(status)
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apiStatusLabel(status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (status == ApiConnStatus.CHECKING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = accent
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsertTargetSelector(
    novels: List<Novel>,
    chapters: List<Chapter>,
    selectedNovelId: Long?,
    selectedChapterId: Long?,
    onSelectNovel: (Long) -> Unit,
    onSelectChapter: (Long) -> Unit
) {
    var novelMenuExpanded by remember { mutableStateOf(false) }
    var chapterMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = "AI 回复插入目标",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (novels.isEmpty()) {
                Text(
                    text = "还没有作品。请先在「书稿」中新建小说与章节。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = novelMenuExpanded,
                        onExpandedChange = { novelMenuExpanded = !novelMenuExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = novels.firstOrNull { it.id == selectedNovelId }?.title ?: "选择小说",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("小说") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = novelMenuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = novelMenuExpanded,
                            onDismissRequest = { novelMenuExpanded = false }
                        ) {
                            novels.forEach { novel ->
                                DropdownMenuItem(
                                    text = {
                                        Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    onClick = {
                                        onSelectNovel(novel.id)
                                        novelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = chapterMenuExpanded,
                        onExpandedChange = { chapterMenuExpanded = !chapterMenuExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = chapters.firstOrNull { it.id == selectedChapterId }?.title
                                ?: "选择章节",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("章节") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = chapterMenuExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = chapterMenuExpanded,
                            onDismissRequest = { chapterMenuExpanded = false }
                        ) {
                            if (chapters.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("该作品还没有章节") },
                                    onClick = { chapterMenuExpanded = false },
                                    enabled = false
                                )
                            } else {
                                chapters.forEach { chapter ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                chapter.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            onSelectChapter(chapter.id)
                                            chapterMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingBubble() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
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
                        "生成后可一键插入到所选章节。",
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
    canInsert: Boolean,
    onInsert: () -> Unit
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
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                SelectionContainer {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!isUser && !message.isError) {
                    Spacer(Modifier.height(2.dp))
                    Row {
                        TextButton(
                            onClick = onInsert,
                            enabled = canInsert,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("插入章节", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
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
                placeholder = { Text("描述你想写的情节…") },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
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
