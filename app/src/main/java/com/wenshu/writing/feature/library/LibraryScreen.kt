package com.wenshu.writing.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.wenshu.writing.domain.model.Setting
import com.wenshu.writing.domain.model.SettingCategories
import com.wenshu.writing.ui.components.ConfirmDeleteDialog
import com.wenshu.writing.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(
            repository = container.settingRepository,
            novelRepository = container.novelRepository,
            preferencesRepository = container.preferencesRepository
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Setting?>(null) }
    var toDelete by remember { mutableStateOf<Setting?>(null) }
    var showAddCategory by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteCategoryTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        // 轻量提示由 Snackbar 处理；此处留空，避免额外依赖。
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设定库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = null
                    showEditor = true
                },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新建${state.selectedCategory}") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BookSelectorBar(
                novels = state.novels,
                activeBookId = state.activeBookId,
                onSelect = viewModel::selectBook
            )

            CategoryTabs(
                categories = state.categories,
                selected = state.selectedCategory,
                onSelect = viewModel::selectCategory,
                onAddCategory = { showAddCategory = true },
                onRename = { renameTarget = it },
                onDelete = { name ->
                    deleteCategoryTarget = name
                }
            )

            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Description,
                    title = "暂无${state.selectedCategory}设定",
                    message = "点击右下角「新建${state.selectedCategory}」开始记录。",
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        TextButton(onClick = { editing = null; showEditor = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("新建${state.selectedCategory}")
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = state.items, key = { it.id }) { setting ->
                        SettingCard(
                            setting = setting,
                            onClick = {
                                editing = setting
                                showEditor = true
                            },
                            onDelete = { toDelete = setting }
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        SettingEditorDialog(
            category = state.selectedCategory,
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { title, subtitle, content ->
                if (editing != null) {
                    viewModel.saveSetting(
                        editing!!.copy(title = title, subtitle = subtitle, content = content)
                    ) { showEditor = false }
                } else {
                    viewModel.createSetting(title, subtitle, content) { showEditor = false }
                }
            }
        )
    }

    toDelete?.let { setting ->
        ConfirmDeleteDialog(
            title = "删除设定",
            message = "确定删除「${setting.displayTitle}」吗？",
            onConfirm = {
                viewModel.deleteSetting(setting.id)
                toDelete = null
            },
            onDismiss = { toDelete = null }
        )
    }

    if (showAddCategory) {
        CategoryNameDialog(
            title = "新增分类",
            initial = "",
            confirmLabel = "创建",
            onConfirm = { name ->
                viewModel.addCategory(name)
                showAddCategory = false
            },
            onDismiss = { showAddCategory = false }
        )
    }

    renameTarget?.let { old ->
        CategoryNameDialog(
            title = "重命名分类",
            initial = old,
            confirmLabel = "保存",
            onConfirm = { name ->
                viewModel.renameCategory(old, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteCategoryTarget?.let { name ->
        ConfirmDeleteDialog(
            title = "删除分类",
            message = "确定删除分类「$name」吗？该分类下的全部设定将一并删除，且不可恢复。",
            onConfirm = {
                viewModel.deleteCategory(name)
                deleteCategoryTarget = null
            },
            onDismiss = { deleteCategoryTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSelectorBar(
    novels: List<com.wenshu.writing.domain.model.Novel>,
    activeBookId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = novels.firstOrNull { it.id == activeBookId }?.title ?: "请选择书稿"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = selectedTitle,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("当前设定归属") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            novels.forEach { novel ->
                DropdownMenuItem(
                    text = { Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(novel.id); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryTabs(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onAddCategory: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it }) { category ->
            val isBuiltIn = SettingCategories.isBuiltIn(category)
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = category == selected,
                    onClick = { onSelect(category) },
                    label = {
                        Text(category, maxLines = 1)
                    },
                    trailingIcon = if (!isBuiltIn) {
                        {
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = "分类操作",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                        onClick = { menuExpanded = false; onRename(category) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除分类") },
                                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                        onClick = { menuExpanded = false; onDelete(category) }
                                    )
                                }
                            }
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
        item {
            FilterChip(
                selected = false,
                onClick = onAddCategory,
                label = { Text("新建分类") },
                leadingIcon = { Icon(Icons.Outlined.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun SettingCard(
    setting: Setting,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = setting.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                setting.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (setting.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = setting.content,
                        style = MaterialTheme.typography.bodySmall,
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

/** 设定库统一的底部抽屉表单：圆角+拖拽横条、高度为屏幕 4/5、内容可滚动、底部按钮固定。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryFormSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "保存",
    confirmEnabled: Boolean = true,
    /** true 时内容区不整体滚动，由内容自身填满剩余空间（用于大输入框向下拓展）。 */
    fillContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .imePadding()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            )
            if (fillContent) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    content()
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    content()
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingEditorDialog(
    category: String,
    initial: Setting?,
    onDismiss: () -> Unit,
    onSave: (title: String, subtitle: String, content: String) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title ?: "") }
    var subtitle by remember(initial) { mutableStateOf(initial?.subtitle.orEmpty()) }
    var content by remember(initial) { mutableStateOf(initial?.content ?: "") }
    val subtitleLabel = if (category == SettingCategories.CHARACTER) "身份/定位（选填）" else "副标题（选填）"

    LibraryFormSheet(
        title = if (initial == null) "新建$category" else "编辑$category",
        onDismiss = onDismiss,
        onConfirm = { onSave(title, subtitle, content) },
        fillContent = true
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(if (category == SettingCategories.CHARACTER) "姓名" else "标题") },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = subtitle,
            onValueChange = { subtitle = it },
            label = { Text(subtitleLabel) },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        // 内容输入框向下拓展填满剩余空间，内部滚动浏览长文本。
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("内容") },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    LibraryFormSheet(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        confirmEnabled = name.isNotBlank(),
        onConfirm = { if (name.isNotBlank()) onConfirm(name) }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("分类名称") },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}
