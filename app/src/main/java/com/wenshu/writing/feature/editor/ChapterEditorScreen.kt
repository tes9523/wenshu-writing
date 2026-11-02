package com.wenshu.writing.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.ui.components.EmptyState
import com.wenshu.writing.ui.util.formatShortTimestamp
import com.wenshu.writing.ui.util.formatWordCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterEditorScreen(
    container: AppContainer,
    chapterId: Long,
    onBack: () -> Unit
) {
    val viewModel: ChapterEditorViewModel = viewModel(
        factory = ChapterEditorViewModel.factory(chapterId, container.novelRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val saveAndLeave = { viewModel.save { onBack() } }
    BackHandler(onBack = saveAndLeave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑章节") },
                navigationIcon = {
                    IconButton(onClick = saveAndLeave) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.save() }) {
                            Icon(Icons.Outlined.Save, contentDescription = "保存")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }

            !state.exists -> {
                EmptyState(
                    icon = Icons.Outlined.Save,
                    title = "章节不存在",
                    message = "该章节可能已被删除。",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    action = { Button(onClick = onBack) { Text("返回") } }
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text("章节标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.content,
                        onValueChange = viewModel::updateContent,
                        label = { Text("正文") },
                        placeholder = { Text("开始写作…") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "字数 ${formatWordCount(state.wordCount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = when {
                                state.isSaving -> "保存中…"
                                state.isDirty -> "未保存"
                                state.lastSavedAt != null ->
                                    "已保存 ${formatShortTimestamp(state.lastSavedAt!!)}"

                                else -> "自动保存已开启"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.isDirty) {
                                MaterialTheme.colorScheme.secondary
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
