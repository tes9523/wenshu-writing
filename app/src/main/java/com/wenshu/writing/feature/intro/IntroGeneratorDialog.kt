package com.wenshu.writing.feature.intro

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wenshu.writing.di.AppContainer
import com.wenshu.writing.domain.model.AiConfig
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.MemoNote
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.Setting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 一条简介生成历史。 */
private data class IntroEntry(
    val time: Long,
    val bookTitle: String,
    val band: String,
    val content: String
)

private object IntroStore {
    private const val PREFS = "intro_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 30

    fun load(context: Context): List<IntroEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                IntroEntry(
                    time = obj.optLong("time"),
                    bookTitle = obj.optString("bookTitle"),
                    band = obj.optString("band"),
                    content = obj.optString("content")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, items: List<IntroEntry>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { entry ->
            array.put(
                JSONObject()
                    .put("time", entry.time)
                    .put("bookTitle", entry.bookTitle)
                    .put("band", entry.band)
                    .put("content", entry.content)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}

private val INTRO_BANDS = listOf("自动", "男频", "女频")
private const val MAX_MATERIAL_CHARS = 50_000

/**
 * 简介生成器：从书稿页唤起，基于书稿资料（项目文档 / 备忘录）+ 可选联网热门简介参考，
 * 调用当前激活模型生成简介，结果输出到当前 AI 对话会话。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntroGeneratorDialog(
    container: AppContainer,
    novel: Novel,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var band by remember { mutableStateOf(INTRO_BANDS.first()) }
    var requirement by remember { mutableStateOf("") }
    var mimicEnabled by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    // 书稿资料候选与已选项
    var bookDocs by remember { mutableStateOf<List<Setting>>(emptyList()) }
    var memos by remember { mutableStateOf<List<MemoNote>>(emptyList()) }
    val selectedDocs = remember { mutableStateListOf<Setting>() }
    val selectedMemos = remember { mutableStateListOf<MemoNote>() }
    var showDocPicker by remember { mutableStateOf(false) }
    var showMemoPicker by remember { mutableStateOf(false) }

    val history = remember {
        mutableStateListOf<IntroEntry>().apply { addAll(IntroStore.load(context)) }
    }

    // 载入书籍资料候选：项目文档 + 备忘录（该书稿 + 全局）
    androidx.compose.runtime.LaunchedEffect(novel.id) {
        bookDocs = container.settingRepository.getByCategory(novel.id, "项目文档")
        memos = container.memoRepository.getNotes(novel.id) +
            container.memoRepository.getNotes(null)
    }

    val materialChars = selectedDocs.sumOf { it.content.length } +
        selectedMemos.sumOf { it.content.length }
    val materialWan = (materialChars / 1000.0).roundToInt() / 10.0

    fun buildMaterial(): String {
        val builder = StringBuilder()
        selectedDocs.forEach { doc ->
            builder.appendLine("【项目文档：${doc.title}】")
            builder.appendLine(doc.content)
            builder.appendLine()
        }
        selectedMemos.forEach { memo ->
            builder.appendLine("【备忘录：${memo.title}】")
            builder.appendLine(memo.content)
            builder.appendLine()
        }
        val full = builder.toString().trim()
        return if (full.length > MAX_MATERIAL_CHARS) {
            full.take(MAX_MATERIAL_CHARS) + "\n……（资料过长，已截取前 5 万字）"
        } else {
            full
        }
    }

    fun generate() {
        if (generating) return
        generating = true
        searching = false
        error = null
        scope.launch {
            val profiles = container.preferencesRepository.aiProfiles.first()
            val settings = container.preferencesRepository.settings.first()
            val active = profiles.firstOrNull { it.id == settings.activeAiProfileId }
                ?: profiles.firstOrNull()
            if (active == null || active.apiKey.isBlank()) {
                error = "请先在设置中配置 AI 提供商"
                generating = false
                return@launch
            }
            val config = AiConfig(
                baseUrl = active.baseUrl.trim(),
                apiKey = active.apiKey.trim(),
                model = active.model.trim(),
                apiType = active.apiType
            )
            val material = buildMaterial()

            // 仿写开关：开启时联网搜索热门简介作为风格参考；失败不阻塞生成。
            var hotReference = ""
            if (mimicEnabled) {
                searching = true
                hotReference = runCatching {
                    container.aiChatViewModel.searchWebReference(
                        "${novel.title} $band 热门网文小说 简介 文案"
                    )
                }.getOrDefault("")
                searching = false
            }

            val prompt = buildString {
                appendLine("请为小说《${novel.title}》撰写一版吸引人的作品简介。")
                if (band != "自动") appendLine("目标频段：$band")
                if (novel.description.isNotBlank()) appendLine("书籍简介（原始）：${novel.description}")
                if (material.isNotBlank()) {
                    appendLine()
                    appendLine("以下是与书稿相关的资料，请先理解再创作：")
                    appendLine(material)
                }
                if (hotReference.isNotBlank()) {
                    appendLine()
                    appendLine("以下是当前全网热门小说简介的写法参考（仅借鉴风格与节奏，不要抄袭内容）：")
                    appendLine(hotReference)
                }
                if (requirement.isNotBlank()) {
                    appendLine()
                    appendLine("补充要求：$requirement")
                }
                appendLine()
                appendLine("要求：直接输出简介正文（150-300 字），有钩子、有期待感，符合网文平台简介风格；不要输出解释或其他内容。")
            }

            val messages = listOf(
                ChatMessage(id = 0L, role = ChatRole.USER, content = prompt)
            )
            val outcome = container.aiClient.chat(config, messages, systemPrompt = "")
            outcome.onSuccess { content ->
                result = content
                val entry = IntroEntry(
                    time = System.currentTimeMillis(),
                    bookTitle = novel.title,
                    band = band,
                    content = content
                )
                history.add(0, entry)
                while (history.size > 30) history.removeAt(history.lastIndex)
                IntroStore.save(context, history)
                // 结果输出到当前 AI 对话会话
                container.aiChatViewModel.injectGeneratedContent(
                    userSummary = "为《${novel.title}》生成简介",
                    assistantContent = content
                )
            }.onFailure { e ->
                error = e.message ?: "生成失败，请重试"
            }
            generating = false
        }
    }

    Dialog(
        onDismissRequest = { if (!generating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Outlined.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("简介生成器", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !generating) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 生成 / 历史 Tab
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("生成") },
                        leadingIcon = {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    FilterChip(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("历史（${history.size}）") },
                        leadingIcon = {
                            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (tab == 0) {
                        // 频段
                        Text("频段", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            INTRO_BANDS.forEach { item ->
                                FilterChip(
                                    selected = band == item,
                                    onClick = { band = item },
                                    label = { Text(item) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 书籍资料
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "书籍资料（md 文件 / 备忘录，供 AI 理解书稿）",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${materialWan}万 / 5万",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { showDocPicker = true }) {
                                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("书籍文件", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(onClick = { showMemoPicker = true }) {
                                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("备忘录", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (selectedDocs.isNotEmpty() || selectedMemos.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        selectedDocs.forEach { doc ->
                                            FilterChip(
                                                selected = true,
                                                onClick = { selectedDocs.remove(doc) },
                                                label = { Text(doc.title, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                        selectedMemos.forEach { memo ->
                                            FilterChip(
                                                selected = true,
                                                onClick = { selectedMemos.remove(memo) },
                                                label = { Text(memo.title, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 简介仿写开关
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.TravelExplore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "简介仿写（搜索全网真实热门简介作参考）",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(checked = mimicEnabled, onCheckedChange = { mimicEnabled = it })
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text("补充要求（书名/风格/想强调的点，自由填写）", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = requirement,
                            onValueChange = { requirement = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            placeholder = { Text("例如：书名《签到都市》、轻松搞笑风、突出金手指的特殊规则…") }
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = { generate() },
                            enabled = !generating,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (generating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (searching) "正在搜索热门简介…" else "生成中…")
                            } else {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("生成简介")
                            }
                        }

                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        result?.let { content ->
                            Spacer(Modifier.height(14.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "生成结果（已同步到 AI 对话）",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { clipboard.setText(AnnotatedString(content)) }) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("复制", style = MaterialTheme.typography.labelMedium)
                                        }
                                        TextButton(onClick = onOpenChat) {
                                            Text("查看对话", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(content, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    } else {
                        // 历史
                        if (history.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "暂无生成历史",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                            history.forEach { entry ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "《${entry.bookTitle}》 · ${entry.band} · ${timeFormat.format(Date(entry.time))}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = {
                                                result = entry.content
                                                tab = 0
                                            }) {
                                                Text("查看", style = MaterialTheme.typography.labelMedium)
                                            }
                                            TextButton(onClick = { clipboard.setText(AnnotatedString(entry.content)) }) {
                                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("复制", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        Text(
                                            entry.content.lineSequence().firstOrNull { it.isNotBlank() }?.take(60).orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 书籍文件选择（项目文档）
    if (showDocPicker) {
        AlertDialog(
            onDismissRequest = { showDocPicker = false },
            title = { Text("选择书籍文件") },
            text = {
                if (bookDocs.isEmpty()) {
                    Text(
                        "该书稿暂无项目文档。可在设定库「项目文档」中手动新建后重试。",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    ) {
                        bookDocs.forEach { doc ->
                            val selected = selectedDocs.any { it.id == doc.id }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) selectedDocs.removeAll { it.id == doc.id } else selectedDocs.add(doc)
                                },
                                label = { Text(doc.title) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDocPicker = false }) { Text("完成") }
            }
        )
    }

    // 备忘录选择
    if (showMemoPicker) {
        AlertDialog(
            onDismissRequest = { showMemoPicker = false },
            title = { Text("选择备忘录") },
            text = {
                if (memos.isEmpty()) {
                    Text("暂无备忘录内容。", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    ) {
                        memos.forEach { memo ->
                            val selected = selectedMemos.any { it.id == memo.id }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) selectedMemos.removeAll { it.id == memo.id } else selectedMemos.add(memo)
                                },
                                label = { Text(memo.title) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMemoPicker = false }) { Text("完成") }
            }
        )
    }
}
