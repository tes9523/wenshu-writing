package com.wenshu.writing.feature.memo

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.MemoNote
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.ui.components.ConfirmDeleteDialog
import com.wenshu.writing.ui.components.EmptyState
import com.wenshu.writing.ui.components.StoragePermissionDialog
import com.wenshu.writing.ui.util.ExternalStorage
import com.wenshu.writing.ui.util.formatShortTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoScreen(
    container: AppContainer,
    onOpenNote: (Long) -> Unit
) {
    val viewModel: MemoViewModel = viewModel(
        factory = MemoViewModel.factory(
            repository = container.memoRepository,
            preferencesRepository = container.preferencesRepository,
            novelRepository = container.novelRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notes = state.notes
    var noteToDelete by remember { mutableStateOf<MemoNote?>(null) }
    var showStorageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun exportMemosToWenshuDir() {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val all = container.memoRepository.getNotes(state.activeBookId)
                    if (all.isEmpty()) throw IllegalStateException("当前没有可导出的备忘录")
                    val markdown = buildString {
                        val bookName = state.novels.firstOrNull { it.id == state.activeBookId }?.title
                        appendLine("# 备忘录${if (bookName != null) " · 《$bookName》" else ""}")
                        appendLine()
                        all.forEach { note ->
                            appendLine("## ${note.displayTitle}")
                            appendLine()
                            if (note.content.isNotBlank()) {
                                appendLine(note.content.trim())
                                appendLine()
                            }
                        }
                    }
                    val bookName = state.novels.firstOrNull { it.id == state.activeBookId }?.title ?: "全局"
                    ExternalStorage.saveText(
                        context = context,
                        category = ExternalStorage.Category.MEMO,
                        fileName = "备忘录_${ExternalStorage.sanitizeFileName(bookName)}_" +
                            "${ExternalStorage.signedName()}.md",
                        content = markdown
                    ) ?: throw IllegalStateException("写入 wenshu/memo 失败，请检查存储权限")
                }
            }
            result.onSuccess { file ->
                Toast.makeText(context, "已导出到 ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, "导出失败：${error.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("备忘录", style = MaterialTheme.typography.titleLarge) },
                    actions = {
                        IconButton(
                            onClick = {
                                if (ExternalStorage.isReady(context)) {
                                    exportMemosToWenshuDir()
                                } else {
                                    showStorageDialog = true
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.FileDownload, contentDescription = "导出备忘录")
                        }
                    }
                )
                BookSelectorBar(
                    novels = state.novels,
                    activeBookId = state.activeBookId,
                    onSelect = viewModel::selectBook
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.createNote { id -> onOpenNote(id) } },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新建笔记") }
            )
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Lightbulb,
                title = "还没有灵感笔记",
                message = "随手记下人物、对白、桥段与设定灵感，内容会自动保存在本机。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                action = {
                    androidx.compose.material3.Button(
                        onClick = { viewModel.createNote { id -> onOpenNote(id) } }
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("新建笔记")
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = notes, key = { it.id }) { note ->
                    MemoCard(
                        note = note,
                        onClick = { onOpenNote(note.id) },
                        onDelete = { noteToDelete = note }
                    )
                }
            }
        }
    }

    if (showStorageDialog) {
        StoragePermissionDialog(
            onDismiss = { showStorageDialog = false },
            onGranted = { exportMemosToWenshuDir() }
        )
    }

    noteToDelete?.let { note ->
        ConfirmDeleteDialog(
            title = "删除笔记",
            message = "确定删除「${note.displayTitle}」吗？此操作不可恢复。",
            onConfirm = {
                viewModel.deleteNote(note.id)
                noteToDelete = null
            },
            onDismiss = { noteToDelete = null }
        )
    }
}

/** 书稿隔离选择器：只展示当前书稿的备忘录。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSelectorBar(
    novels: List<Novel>,
    activeBookId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (activeBookId == null) {
        "全局公共备忘录"
    } else {
        novels.firstOrNull { it.id == activeBookId }?.title ?: "全局公共备忘录"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("当前书稿") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("全局公共备忘录") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            novels.forEach { novel ->
                DropdownMenuItem(
                    text = { Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onSelect(novel.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun MemoCard(
    note: MemoNote,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (note.preview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatShortTimestamp(note.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除笔记",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
