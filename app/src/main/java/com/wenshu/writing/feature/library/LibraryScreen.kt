package com.wenshu.writing.feature.library

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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.wenshu.writing.domain.model.CharacterCard
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.WorldSetting
import com.wenshu.writing.ui.components.ConfirmDeleteDialog
import com.wenshu.writing.ui.components.EmptyState
import com.wenshu.writing.ui.components.InitialCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(container: AppContainer) {
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(
            repository = container.libraryRepository,
            preferencesRepository = container.preferencesRepository,
            novelRepository = container.novelRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCharacterDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<CharacterCard?>(null) }
    var showWorldDialog by remember { mutableStateOf(false) }
    var editingWorld by remember { mutableStateOf<WorldSetting?>(null) }
    var characterToDelete by remember { mutableStateOf<CharacterCard?>(null) }
    var worldToDelete by remember { mutableStateOf<WorldSetting?>(null) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(title = { Text("设定库", style = MaterialTheme.typography.titleLarge) })
                BookSelectorBar(
                    novels = state.novels,
                    activeBookId = state.activeBookId,
                    onSelect = viewModel::selectBook
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("人物卡") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("世界观") }
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        editingCharacter = null
                        showCharacterDialog = true
                    } else {
                        editingWorld = null
                        showWorldDialog = true
                    }
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(if (selectedTab == 0) "新建人物" else "新建设定") }
            )
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            if (state.characters.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Groups,
                    title = "还没有人物卡",
                    message = "记录角色的身份、性格与背景，写作时随时查阅。",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    action = {
                        TextButton(onClick = { showCharacterDialog = true }) {
                            Text("新建人物卡")
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
                    items(items = state.characters, key = { it.id }) { character ->
                        CharacterCardItem(
                            card = character,
                            onClick = {
                                editingCharacter = character
                                showCharacterDialog = true
                            },
                            onDelete = { characterToDelete = character }
                        )
                    }
                }
            }
        } else {
            if (state.worldSettings.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Public,
                    title = "还没有世界观设定",
                    message = "整理地理、势力、规则与历史，让故事世界保持一致。",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    action = {
                        TextButton(onClick = { showWorldDialog = true }) {
                            Text("新建世界观设定")
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
                    items(items = state.worldSettings, key = { it.id }) { setting ->
                        WorldSettingItem(
                            setting = setting,
                            onClick = {
                                editingWorld = setting
                                showWorldDialog = true
                            },
                            onDelete = { worldToDelete = setting }
                        )
                    }
                }
            }
        }
    }

    if (showCharacterDialog) {
        CharacterDialog(
            initial = editingCharacter,
            onDismiss = { showCharacterDialog = false },
            onSave = { name, role, description ->
                val initial = editingCharacter
                val card = if (initial == null) {
                    CharacterCard(
                        name = name,
                        role = role,
                        description = description,
                        bookId = state.activeBookId
                    )
                } else {
                    initial.copy(name = name, role = role, description = description)
                }
                viewModel.saveCharacter(card) { showCharacterDialog = false }
            }
        )
    }

    if (showWorldDialog) {
        WorldSettingDialog(
            initial = editingWorld,
            onDismiss = { showWorldDialog = false },
            onSave = { category, title, content ->
                val initial = editingWorld
                val setting = if (initial == null) {
                    WorldSetting(
                        category = category,
                        title = title,
                        content = content,
                        bookId = state.activeBookId
                    )
                } else {
                    initial.copy(category = category, title = title, content = content)
                }
                viewModel.saveWorldSetting(setting) { showWorldDialog = false }
            }
        )
    }

    characterToDelete?.let { character ->
        ConfirmDeleteDialog(
            title = "删除人物卡",
            message = "确定删除「${character.name}」吗？",
            onConfirm = {
                viewModel.deleteCharacter(character.id)
                characterToDelete = null
            },
            onDismiss = { characterToDelete = null }
        )
    }

    worldToDelete?.let { setting ->
        ConfirmDeleteDialog(
            title = "删除世界观设定",
            message = "确定删除「${setting.title}」吗？",
            onConfirm = {
                viewModel.deleteWorldSetting(setting.id)
                worldToDelete = null
            },
            onDismiss = { worldToDelete = null }
        )
    }
}

/**
 * 书稿隔离选择器：所有人物卡与世界观只展示当前书稿的数据。
 * 选择「全局公共素材」时显示 bookId = null 的手动素材。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSelectorBar(
    novels: List<Novel>,
    activeBookId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (activeBookId == null) {
        "全局公共素材"
    } else {
        novels.firstOrNull { it.id == activeBookId }?.title ?: "全局公共素材"
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
                text = { Text("全局公共素材") },
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
private fun CharacterCardItem(
    card: CharacterCard,
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialCover(text = card.name, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (card.role.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = card.role,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (card.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = card.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorldSettingItem(
    setting: WorldSetting,
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (setting.category.isNotBlank()) {
                    Text(
                        text = setting.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = setting.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (setting.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = setting.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CharacterDialog(
    initial: CharacterCard?,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var role by remember { mutableStateOf(initial?.role.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建人物卡" else "编辑人物卡") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("身份 / 定位（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("性格、背景、关系等") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), role.trim(), description.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun WorldSettingDialog(
    initial: WorldSetting?,
    onDismiss: () -> Unit,
    onSave: (category: String, title: String, content: String) -> Unit
) {
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var content by remember { mutableStateOf(initial?.content.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建世界观设定" else "编辑世界观设定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类（可选，如地理 / 势力 / 规则）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("设定内容") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(category.trim(), title.trim(), content.trim()) },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
