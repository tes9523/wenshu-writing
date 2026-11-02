package com.wenshu.writing.feature.works

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.ui.components.ConfirmDeleteDialog
import com.wenshu.writing.ui.components.EmptyState
import com.wenshu.writing.ui.util.formatShortTimestamp
import com.wenshu.writing.ui.util.formatWordCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListScreen(
    container: AppContainer,
    novelId: Long,
    onBack: () -> Unit,
    onOpenChapter: (Long) -> Unit
) {
    val viewModel: ChapterListViewModel = viewModel(
        factory = ChapterListViewModel.factory(novelId, container.novelRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var chapterToDelete by remember { mutableStateOf<Chapter?>(null) }
    val totalWords = state.chapters.sumOf { it.wordCount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.novel?.title ?: "章节列表",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新建章节") }
            )
        }
    ) { innerPadding ->
        if (state.chapters.isEmpty() && !state.isLoading) {
            EmptyState(
                icon = Icons.Outlined.Description,
                title = "还没有章节",
                message = "创建第一章，开始记录你的故事。章节内容会自动保存到本机。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                action = {
                    TextButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("新建章节")
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "共 ${state.chapters.size} 章 · ${formatWordCount(totalWords)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                itemsIndexed(items = state.chapters, key = { _, c -> c.id }) { index, chapter ->
                    ChapterCard(
                        chapter = chapter,
                        index = index,
                        onClick = { onOpenChapter(chapter.id) },
                        onDelete = { chapterToDelete = chapter }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateChapterDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title ->
                viewModel.createChapter(title) { id ->
                    showCreateDialog = false
                    onOpenChapter(id)
                }
            }
        )
    }

    chapterToDelete?.let { chapter ->
        ConfirmDeleteDialog(
            title = "删除章节",
            message = "确定删除「${chapter.title}」吗？此操作不可恢复。",
            onConfirm = {
                viewModel.deleteChapter(chapter.id)
                chapterToDelete = null
            },
            onDismiss = { chapterToDelete = null }
        )
    }
}

@Composable
private fun ChapterCard(
    chapter: Chapter,
    index: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatWordCount(chapter.wordCount)} · ${formatShortTimestamp(chapter.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("删除章节") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateChapterDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String) -> Unit
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建章节") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("章节标题") },
                placeholder = { Text("留空将自动命名为「第 N 章」") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
