package com.wenshu.writing.feature.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.AiProfile
import com.wenshu.writing.domain.model.ApiConnStatus
import com.wenshu.writing.domain.model.ApiProviderType
import com.wenshu.writing.ui.components.ConfirmDeleteDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ConnectedGreen = Color(0xFF2E7D32)
private val CheckingAmber = Color(0xFFF9A825)
private val IdleGray = Color(0xFF9E9E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: AiProviderViewModel = viewModel(
        factory = AiProviderViewModel.factory(
            preferencesRepository = container.preferencesRepository,
            aiClient = container.aiClient
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteEditorTarget by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(message)
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("AI 提供商", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "saved-label") {
                Text(
                    text = "已保存提供商",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            if (state.profiles.isEmpty()) {
                item(key = "empty") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "还没有 AI 提供商",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "添加一个 OpenAI 兼容或 Anthropic 接口，即可在对话页使用。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(items = state.profiles, key = { it.id }) { profile ->
                    ProviderCard(
                        profile = profile,
                        isActive = profile.id == state.activeProfileId,
                        connection = state.statuses[profile.id],
                        onSelect = { viewModel.selectProfile(profile.id) },
                        onEdit = { viewModel.startEditProvider(profile.id) }
                    )
                }
            }

            item(key = "add") {
                OutlinedButton(
                    onClick = viewModel::startAddProvider,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加提供商")
                }
            }

            val editor = state.editor
            if (editor != null) {
                item(key = "editor") {
                    ProviderEditorCard(
                        editor = editor,
                        isSaving = state.isSaving,
                        onNameChange = viewModel::updateName,
                        onBaseUrlChange = viewModel::updateBaseUrl,
                        onApiKeyChange = viewModel::updateApiKey,
                        onTypeChange = viewModel::updateApiType,
                        onModelChange = viewModel::updateModel,
                        onModelQueryChange = viewModel::updateModelQuery,
                        onRefreshModels = viewModel::refreshEditorModels,
                        onRemoveModel = viewModel::removeModel,
                        onSave = { viewModel.save(continueAdding = false) },
                        onSaveAndAdd = { viewModel.save(continueAdding = true) },
                        onCancel = viewModel::closeEditor,
                        onDelete = { deleteEditorTarget = true }
                    )
                }
            }
        }
    }

    val editor = state.editor
    if (deleteEditorTarget && editor != null) {
        ConfirmDeleteDialog(
            title = "删除提供商",
            message = "确定删除「${editor.name}」吗？此操作不可恢复。",
            onConfirm = {
                viewModel.deleteProfile(editor.id)
                deleteEditorTarget = false
            },
            onDismiss = { deleteEditorTarget = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    profile: AiProfile,
    isActive: Boolean,
    connection: ProviderConnectionState?,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    val status = connection?.status ?: ApiConnStatus.UNVERIFIED

    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (isActive) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.model.ifBlank { "未设置模型" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (status == ApiConnStatus.FAILURE || status == ApiConnStatus.TIMEOUT) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = connection?.message.orEmpty().ifBlank { "连接失败" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(status)
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑提供商",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorCard(
    editor: ProviderEditorState,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onTypeChange: (ApiProviderType) -> Unit,
    onModelChange: (String) -> Unit,
    onModelQueryChange: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onRemoveModel: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndAdd: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    var apiKeyVisible by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val filteredModels = remember(editor.models, editor.modelQuery) {
        val query = editor.modelQuery.trim()
        if (query.isEmpty()) {
            editor.models
        } else {
            editor.models.filter { it.contains(query, ignoreCase = true) }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (editor.isExisting) "编辑提供商" else "添加提供商",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onCancel) { Text("取消") }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                label = { Text("名称") },
                placeholder = { Text("如：智谱 GLM / 备用服务") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusBringIntoView()
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editor.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("API 地址") },
                placeholder = { Text("https://api.openai.com/v1") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusBringIntoView()
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "接口类型",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApiProviderType.entries.forEach { type ->
                    FilterChip(
                        selected = editor.apiType == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(type.label) },
                        leadingIcon = if (editor.apiType == type) {
                            {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editor.apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API 密钥") },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .focusBringIntoView()
            )

            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = modelMenuExpanded,
                onExpandedChange = { modelMenuExpanded = !modelMenuExpanded }
            ) {
                OutlinedTextField(
                    value = editor.model,
                    onValueChange = onModelChange,
                    label = { Text("模型") },
                    placeholder = { Text("获取列表后选择，也可手动输入") },
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .focusBringIntoView()
                )
                ExposedDropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    if (editor.models.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("先获取模型列表") },
                            onClick = { modelMenuExpanded = false },
                            enabled = false
                        )
                    } else {
                        editor.models.forEach { model ->
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
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onRefreshModels,
                    enabled = !editor.isFetchingModels
                ) {
                    if (editor.isFetchingModels) {
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
                    Spacer(Modifier.width(6.dp))
                    Text(if (editor.isFetchingModels) "正在获取…" else "获取模型列表")
                }
            }

            Spacer(Modifier.height(8.dp))
            ApiStatusStrip(status = editor.status, message = editor.statusMessage)

            if (editor.models.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "模型列表（${editor.models.size}）",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editor.modelQuery,
                    onValueChange = onModelQueryChange,
                    placeholder = { Text("按名称过滤…") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusBringIntoView()
                )
                Spacer(Modifier.height(4.dp))
                if (filteredModels.isEmpty()) {
                    Text(
                        text = "没有匹配的模型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(items = filteredModels, key = { it }) { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onModelChange(model) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = editor.model == model,
                                    onClick = { onModelChange(model) }
                                )
                                Text(
                                    text = model,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onRemoveModel(model) }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "删除模型",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("保存")
                    }
                }
                OutlinedButton(
                    onClick = onSaveAndAdd,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存并继续添加")
                }
            }

            if (editor.isExisting) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("删除该提供商", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ApiConnStatus) {
    val accent = statusAccent(status)
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = statusLabel(status),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ApiStatusStrip(status: ApiConnStatus, message: String) {
    val accent = statusAccent(status)
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
                    text = statusLabel(status),
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

@Composable
private fun statusAccent(status: ApiConnStatus): Color = when (status) {
    ApiConnStatus.SUCCESS -> ConnectedGreen
    ApiConnStatus.FAILURE, ApiConnStatus.TIMEOUT -> MaterialTheme.colorScheme.error
    ApiConnStatus.CHECKING -> CheckingAmber
    ApiConnStatus.UNVERIFIED -> IdleGray
}

private fun statusLabel(status: ApiConnStatus): String = when (status) {
    ApiConnStatus.SUCCESS -> "连接正常"
    ApiConnStatus.FAILURE, ApiConnStatus.TIMEOUT -> "连接失败"
    ApiConnStatus.CHECKING -> "校验中"
    ApiConnStatus.UNVERIFIED -> "未校验"
}

/**
 * 输入框获得焦点后，等输入法弹出并完成布局变化，再把该输入框滚动到可视区域，
 * 避免它被软键盘遮挡。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.focusBringIntoView(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                scope.launch {
                    delay(220)
                    requester.bringIntoView()
                }
            }
        }
}
