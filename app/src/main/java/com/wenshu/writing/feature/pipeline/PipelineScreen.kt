package com.wenshu.writing.feature.pipeline

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.NewBookInfo
import com.wenshu.writing.domain.model.PipelineStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineScreen(
    container: AppContainer,
    novelId: Long,
    skillId: Long,
    onBack: () -> Unit
) {
    val viewModel: PipelineViewModel = viewModel(
        factory = PipelineViewModel.factory(
            novelId = novelId,
            skillId = skillId,
            novelRepository = container.novelRepository,
            skillRepository = container.skillRepository,
            pipelineRepository = container.pipelineRepository,
            settingRepository = container.settingRepository,
            preferencesRepository = container.preferencesRepository,
            chatRepository = container.chatRepository,
            aiClient = container.aiClient
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("开启新书", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = state.novel?.title ?: "未选择书稿",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showRestartConfirm = true }) {
                        Text("重新开始")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StageOverview(
                    stage = state.stage,
                    status = state.status,
                    isRunning = state.isRunning
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (state.stage == 1) {
                                "阶段 1 · 信息采集"
                            } else {
                                "阶段 ${state.stage} · ${pipelineStageNames.getOrNull(state.stage - 1) ?: "已完成"}"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (state.stage == 1) {
                                "先填写题材、卖点、目标赛道、核心创意与风格偏好，全部必填。"
                            } else {
                                "本阶段完成后会自动暂停，可人工修改已落库内容，再继续下一阶段。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (state.stage == 1) {
                                        showInfoDialog = true
                                    } else {
                                        viewModel.runCurrentStage()
                                    }
                                },
                                enabled = !state.isRunning && state.stage <= 6
                            ) {
                                if (state.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                } else {
                                    Icon(
                                        Icons.Outlined.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = when {
                                        state.stage == 1 -> if (state.info == null) "填写新书信息" else "重新填写信息"
                                        state.isRunning -> "执行中…"
                                        else -> "执行阶段 ${state.stage}"
                                    }
                                )
                            }
                            if (state.info != null && state.stage != 1) {
                                TextButton(onClick = { showInfoDialog = true }) {
                                    Text("修改新书信息")
                                }
                            }
                        }
                    }
                }
            }

            item {
                DataSummaryCard(
                    characters = state.characters.size,
                    worlds = state.worldSettings.size,
                    outlines = state.outlines.size,
                    tasks = state.tasks.size
                )
            }

            if (state.output.isNotBlank()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("阶段输出", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = state.output,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .height(360.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        NewBookInfoDialog(
            initial = state.info,
            onDismiss = { showInfoDialog = false },
            onConfirm = { info ->
                viewModel.submitInfo(info)
                showInfoDialog = false
            }
        )
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("重新开始流水线") },
            text = { Text("将清除当前阶段断点并回到阶段 1。已落库的设定、大纲与正文不会删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartConfirm = false
                        viewModel.restart()
                    }
                ) { Text("重新开始") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StageOverview(stage: Int, status: PipelineStatus, isRunning: Boolean) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("流水线进度", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            pipelineStageNames.forEachIndexed { index, name ->
                val order = index + 1
                val done = order < stage || (status == PipelineStatus.COMPLETED && order <= 6)
                val current = order == stage && status != PipelineStatus.COMPLETED
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = when {
                            done -> MaterialTheme.colorScheme.primary
                            current -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "阶段 $order · $name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current || done) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    if (current) {
                        Text(
                            text = if (isRunning) "执行中" else "待确认",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataSummaryCard(characters: Int, worlds: Int, outlines: Int, tasks: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem("人物卡", characters)
            SummaryItem("世界观", worlds)
            SummaryItem("大纲", outlines)
            SummaryItem("任务卡", tasks)
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewBookInfoDialog(
    initial: NewBookInfo?,
    onDismiss: () -> Unit,
    onConfirm: (NewBookInfo) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var genre by remember { mutableStateOf(initial?.genre.orEmpty()) }
    var sellingPoint by remember { mutableStateOf(initial?.sellingPoint.orEmpty()) }
    var track by remember { mutableStateOf(initial?.track.orEmpty()) }
    var coreIdea by remember { mutableStateOf(initial?.coreIdea.orEmpty()) }
    var style by remember { mutableStateOf(initial?.style.orEmpty()) }

    val info = NewBookInfo(
        title = title.trim(),
        genre = genre.trim(),
        sellingPoint = sellingPoint.trim(),
        track = track.trim(),
        coreIdea = coreIdea.trim(),
        style = style.trim()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阶段 1 · 新书信息采集") },
        text = {
            Column(
                modifier = Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("题材") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sellingPoint,
                    onValueChange = { sellingPoint = it },
                    label = { Text("卖点") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = track,
                    onValueChange = { track = it },
                    label = { Text("目标赛道") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coreIdea,
                    onValueChange = { coreIdea = it },
                    label = { Text("核心创意") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = style,
                    onValueChange = { style = it },
                    label = { Text("风格偏好") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(info) },
                enabled = info.isValid()
            ) { Text("保存并进入阶段 2") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
