package com.wenshu.writing.feature.works

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.feature.intro.IntroGeneratorDialog
import com.wenshu.writing.ui.components.ConfirmDeleteDialog
import com.wenshu.writing.ui.components.CoverFiles
import com.wenshu.writing.ui.components.CoverPreviewDialog
import com.wenshu.writing.ui.components.EmptyState
import com.wenshu.writing.ui.components.NovelCover
import com.wenshu.writing.ui.components.StoragePermissionDialog
import com.wenshu.writing.ui.components.rememberCoverPicker
import com.wenshu.writing.ui.util.ExternalStorage
import com.wenshu.writing.ui.util.formatShortTimestamp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksScreen(
    container: AppContainer,
    onOpenNovel: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenChat: () -> Unit
) {
    val viewModel: WorksViewModel = viewModel(
        factory = WorksViewModel.factory(
            repository = container.novelRepository,
            settingRepository = container.settingRepository,
            memoRepository = container.memoRepository,
            preferencesRepository = container.preferencesRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var novelToDelete by remember { mutableStateOf<Novel?>(null) }
    var introNovel by remember { mutableStateOf<Novel?>(null) }
    var pendingExportNovel by remember { mutableStateOf<Novel?>(null) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var coverTargetNovelId by remember { mutableStateOf<Long?>(null) }
    var coverPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    val pickCover = rememberCoverPicker { bitmap -> coverPreviewBitmap = bitmap }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val novel = pendingExportNovel
        pendingExportNovel = null
        if (uri == null || novel == null) return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (stream == null) {
            Toast.makeText(context, "无法写入文件", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.exportBook(novel.id, stream) { result ->
            result.onSuccess {
                Toast.makeText(context, "整书导出成功", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    error.message ?: "整书导出失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (stream == null) {
            Toast.makeText(context, "无法读取文件", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.importBook(stream) { result ->
            result.onSuccess { novelId ->
                Toast.makeText(context, "整书导入成功", Toast.LENGTH_SHORT).show()
                onOpenNovel(novelId)
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    error.message ?: "整书导入失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun exportNovelToWenshuDir(novel: Novel) {
        viewModel.exportBookToExternal(context, novel.id) { result ->
            result.onSuccess { path ->
                Toast.makeText(context, "已导出到 $path", Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, error.message ?: "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("书稿", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "回收站")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "导入整书数据包")
                    }
                }
            )
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("新建小说")
                        }
                        TextButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                            Text("导入整书数据包")
                        }
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
                        onClick = { viewModel.openNovel(novel.id) { onOpenNovel(novel.id) } },
                        onExport = {
                            if (ExternalStorage.isReady(context)) {
                                exportNovelToWenshuDir(novel)
                            } else {
                                pendingExportNovel = novel
                                showStorageDialog = true
                            }
                        },
                        onChangeCover = {
                            coverTargetNovelId = novel.id
                            pickCover()
                        },
                        onRemoveCover = {
                            CoverFiles.delete(novel.coverPath)
                            viewModel.setCover(novel.id, null)
                        },
                        onDelete = { novelToDelete = novel },
                        onGenerateIntro = { introNovel = novel }
                    )
                }
            }
        }
    }

    if (showStorageDialog) {
        StoragePermissionDialog(
            onDismiss = { showStorageDialog = false },
            onGranted = {
                pendingExportNovel?.let { exportNovelToWenshuDir(it) }
                pendingExportNovel = null
            },
            onDecline = {
                pendingExportNovel?.let { novel ->
                    exportLauncher.launch(safeFileName(novel.title) + ".zip")
                }
                pendingExportNovel = null
            }
        )
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

    introNovel?.let { novel ->
        IntroGeneratorDialog(
            container = container,
            novel = novel,
            onDismiss = { introNovel = null },
            onOpenChat = {
                introNovel = null
                onOpenChat()
            }
        )
    }

    novelToDelete?.let { novel ->
        ConfirmDeleteDialog(
            title = "移入回收站",
            message = "确定将《${novel.title}》移入回收站吗？章节与设定会一并保留，可随时在回收站中恢复。",
            confirmText = "移入回收站",
            onConfirm = {
                viewModel.deleteNovel(novel.id)
                novelToDelete = null
            },
            onDismiss = { novelToDelete = null }
        )
    }

    coverPreviewBitmap?.let { bitmap ->
        CoverPreviewDialog(
            bitmap = bitmap,
            onDismiss = {
                coverPreviewBitmap = null
                coverTargetNovelId = null
            },
            onConfirm = {
                val novelId = coverTargetNovelId
                coverPreviewBitmap = null
                coverTargetNovelId = null
                if (novelId != null) {
                    scope.launch {
                        val path = CoverFiles.save(context, novelId, bitmap)
                        if (path == null) {
                            Toast.makeText(context, "封面保存失败", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setCover(novelId, path)
                            Toast.makeText(context, "封面已更新", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
}

private fun safeFileName(name: String): String {
    val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    return cleaned.ifBlank { "book" }
}

@Composable
private fun NovelCard(
    novel: Novel,
    onClick: () -> Unit,
    onExport: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onDelete: () -> Unit,
    onGenerateIntro: () -> Unit
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
                        text = { Text("生成简介") },
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onGenerateIntro()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("更换封面") },
                        onClick = {
                            menuExpanded = false
                            onChangeCover()
                        }
                    )
                    if (novel.coverPath != null) {
                        DropdownMenuItem(
                            text = { Text("恢复默认封面") },
                            onClick = {
                                menuExpanded = false
                                onRemoveCover()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("导出整书数据包") },
                        leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onExport()
                        }
                    )
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
