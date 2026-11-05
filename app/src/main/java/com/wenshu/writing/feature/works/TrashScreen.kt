package com.wenshu.writing.feature.works

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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.ui.components.ConfirmDeleteDialog
import com.wenshu.writing.ui.components.EmptyState
import com.wenshu.writing.ui.components.NovelCover
import com.wenshu.writing.ui.util.formatShortTimestamp

/** 回收站页面：软删除的书稿在此暂存，可恢复或永久删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: TrashViewModel = viewModel(
        factory = TrashViewModel.factory(container.novelRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var novelToDelete by remember { mutableStateOf<Novel?>(null) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("回收站", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.novels.isEmpty() && !state.isLoading) {
            EmptyState(
                icon = Icons.Outlined.DeleteOutline,
                title = "回收站是空的",
                message = "删除的书稿会先移入这里，随时可以恢复。",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = state.novels, key = { it.id }) { novel ->
                    TrashNovelCard(
                        novel = novel,
                        onRestore = { viewModel.restore(novel.id) },
                        onDelete = { novelToDelete = novel }
                    )
                }
            }
        }
    }

    novelToDelete?.let { novel ->
        ConfirmDeleteDialog(
            title = "永久删除",
            message = "确定永久删除《${novel.title}》吗？该书稿及其全部章节、设定、备忘录将被彻底清除，此操作不可恢复。",
            confirmText = "永久删除",
            onConfirm = {
                viewModel.deletePermanently(novel.id)
                novelToDelete = null
            },
            onDismiss = { novelToDelete = null }
        )
    }
}

@Composable
private fun TrashNovelCard(
    novel: Novel,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NovelCover(
                novel = novel,
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "移入于 ${formatShortTimestamp(novel.deletedAt ?: novel.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRestore) {
                        Icon(
                            Icons.Outlined.RestoreFromTrash,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("恢复")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("永久删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
