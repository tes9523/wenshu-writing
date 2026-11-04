package com.wenshu.writing.feature.skill

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillEditorScreen(
    container: AppContainer,
    skillId: Long,
    onBack: () -> Unit
) {
    val viewModel: SkillEditorViewModel = viewModel(
        factory = SkillEditorViewModel.factory(container.skillRepository, skillId)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(state.closeRequested) {
        if (state.closeRequested) {
            viewModel.consumeCloseRequest()
            onBack()
        }
    }

    BackHandler {
        if (!viewModel.uiState.value.isEditing) onBack() else viewModel.requestExit()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            state.isNew -> "新建 Skill"
                            state.isEditing -> "编辑 Skill"
                            else -> "Skill 详情"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = viewModel::requestExit) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.loaded) {
                        if (state.isEditing) {
                            TextButton(
                                onClick = {
                                    if (state.isNew) viewModel.discardAndExit()
                                    else viewModel.cancelEditing()
                                }
                            ) { Text("取消") }
                            TextButton(
                                onClick = viewModel::requestSave,
                                enabled = state.canSave
                            ) {
                                Text(
                                    text = if (state.isSaving) "保存中…" else "保存",
                                    color = if (state.canSave) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    }
                                )
                            }
                        } else {
                            TextButton(onClick = viewModel::startEditing) { Text("编辑") }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (!state.loaded) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isBuiltIn) {
                item(key = "builtin-hint") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = "内置Skill不支持修改，可导出后新建自定义Skill修改。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            item(key = "basic") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::updateName,
                            label = { Text("技能名称") },
                            readOnly = !state.isEditing,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = viewModel::updateDescription,
                            label = { Text("简介") },
                            readOnly = !state.isEditing,
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item(key = "main-md") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "主提示词（skill.md）",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "激活该技能时，这段内容会注入到 AI 的系统提示词中。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.mainMarkdown,
                            onValueChange = viewModel::updateMainMarkdown,
                            readOnly = !state.isEditing,
                            minLines = 10,
                            maxLines = 30,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item(key = "references-header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "参考素材（references）",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "共 ${state.references.size} 个文件",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.isEditing) {
                        OutlinedButton(onClick = viewModel::addReference) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("添加")
                        }
                    }
                }
            }

            if (state.references.isEmpty()) {
                item(key = "references-empty") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "暂无参考素材。可按需添加额外的 md / txt 内容，导入导出时会一并打包到 references/ 目录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(items = state.references.indices.toList(), key = { it }) { index ->
                    val reference = state.references[index]
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = reference.name,
                                    onValueChange = { viewModel.updateReferenceName(index, it) },
                                    label = { Text("文件名") },
                                    readOnly = !state.isEditing,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                if (state.isEditing) {
                                    IconButton(onClick = { viewModel.removeReference(index) }) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "移除素材",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = reference.content,
                                onValueChange = { viewModel.updateReferenceContent(index, it) },
                                label = { Text("内容") },
                                readOnly = !state.isEditing,
                                minLines = 4,
                                maxLines = 14,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item(key = "footer") {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (state.isEditing) {
                            "编辑完成后点击右上角「保存」，会再次确认后写入本机。"
                        } else {
                            "当前为只读模式，点击右上角「编辑」后才能修改内容。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }

    if (state.showSaveConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveConfirm,
            title = { Text("确认保存") },
            text = { Text("是否确认保存对该Skill的修改？") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSave(onCreated = onBack) }) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSaveConfirm) { Text("取消") }
            }
        )
    }

    if (state.showExitConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::stayOnPage,
            title = { Text("未保存的修改") },
            text = { Text("当前存在未保存的修改，是否放弃？") },
            confirmButton = {
                TextButton(onClick = viewModel::discardAndExit) {
                    Text("放弃修改", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::stayOnPage) { Text("留在页面") }
            }
        )
    }
}
