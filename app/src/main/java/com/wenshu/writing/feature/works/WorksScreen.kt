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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.ui.components.ConfirmDeleteDialog
import com.wenshu.writing.ui.components.EmptyState
import com.wenshu.writing.ui.components.InitialCover
import com.wenshu.writing.ui.util.formatShortTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksScreen(
    container: AppContainer,
    onOpenNovel: (Long) -> Unit
) {
    val viewModel: WorksViewModel = viewModel(
        factory = WorksViewModel.factory(container.novelRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var novelToDelete by remember { mutableStateOf<Novel?>(null) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text("书稿", style = MaterialTheme.typography.titleLarge) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新建小说") }
            )
        }
    ) { innerPadding ->
        if (state.novels.isEmpty() && !state.isLoading) {
            EmptyState(
                icon = Icons.Outlined.AutoStories,
                title = "还没有作品",
                message = "从一部小说开始，管理章节与设定，随时在本地保存你的创作。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                action = {
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("新建小说")
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = state.novels, key = { it.id }) { novel ->
                    NovelCard(
                        novel = novel,
                        onClick = { onOpenNovel(novel.id) },
                        onDelete = { novelToDelete = novel }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNovelDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, description ->
                viewModel.createNovel(title, description) { id ->
                    showCreateDialog = false
                    onOpenNovel(id)
                }
            }
        )
    }

    novelToDelete?.let { novel ->
        ConfirmDeleteDialog(
            title = "删除作品",
            message = "确定删除《${novel.title}》及其全部章节吗？此操作不可恢复。",
            onConfirm = {
                viewModel.deleteNovel(novel.id)
                novelToDelete = null
            },
            onDismiss = { novelToDelete = null }
        )
    }
}

@Composable
private fun NovelCard(
    novel: Novel,
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialCover(
                text = novel.title,
                modifier = Modifier.size(width = 48.dp, height = 64.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (novel.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = novel.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "更新于 ${formatShortTimestamp(novel.updatedAt)}",
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
                        text = { Text("删除作品") },
                        leadingIcon = {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        },
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
private fun CreateNovelDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建小说") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("作品名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("简介（可选）") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, description) },
                enabled = title.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
