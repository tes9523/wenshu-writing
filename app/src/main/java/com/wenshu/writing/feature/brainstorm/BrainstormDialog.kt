package com.wenshu.writing.feature.brainstorm

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一条脑洞生成历史记录。 */
private data class BrainstormEntry(
    val time: Long,
    val band: String,
    val genre: String,
    val requirement: String,
    val content: String
)

/** 脑洞历史的本地持久化（SharedPreferences + JSON，最多保留 30 条）。 */
private object BrainstormStore {
    private const val PREFS = "brainstorm_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 30

    fun load(context: Context): List<BrainstormEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                BrainstormEntry(
                    time = obj.optLong("time"),
                    band = obj.optString("band"),
                    genre = obj.optString("genre"),
                    requirement = obj.optString("requirement"),
                    content = obj.optString("content")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, items: List<BrainstormEntry>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { entry ->
            array.put(
                JSONObject()
                    .put("time", entry.time)
                    .put("band", entry.band)
                    .put("genre", entry.genre)
                    .put("requirement", entry.requirement)
                    .put("content", entry.content)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}

private val BANDS = listOf("男频", "女频", "不限")
private val GENRES = listOf("都市", "高武", "玄幻", "仙侠", "悬疑", "科幻", "历史", "游戏", "跟随榜单")
private val SECTION_TITLES = listOf("书名", "核心脑洞", "金手指", "核心爽点")

private fun buildPrompt(band: String, genre: String, requirement: String): String = buildString {
    appendLine("你是一位资深网文编辑，请按照以下要求生成一个新书脑洞方案。")
    appendLine("频段：$band")
    appendLine("题材：$genre")
    if (requirement.isNotBlank()) appendLine("补充要求：$requirement")
    appendLine()
    appendLine("请严格按以下四个板块输出，每个板块以「【板块名】」单独一行开头，不要输出其他内容：")
    appendLine("【书名】给出 1 个主推书名和 2 个备选书名，各附一句话卖点说明。")
    appendLine("【核心脑洞】用 100-200 字描述核心设定与核心钩子。")
    appendLine("【金手指】说明金手指的规则、限制、成长性，以及如何驱动剧情。")
    appendLine("【核心爽点】说明核心爽点，以及在不同阶段如何运用。")
}

/** 将模型输出按板块名切分为若干段落。 */
private fun parseSections(content: String): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    var currentTitle: String? = null
    val buffer = StringBuilder()
    fun flush() {
        val title = currentTitle
        if (title != null) {
            result += title to buffer.toString().trim()
            buffer.clear()
        }
    }
    content.lineSequence().forEach { line ->
        val trimmed = line.trim()
        val matched = SECTION_TITLES.firstOrNull { title ->
            trimmed.length <= title.length + 8 &&
                trimmed.trimStart('#', '*', ' ', '【').trimEnd('】', '*', ' ', '：', ':') == title
        }
        if (matched != null) {
            flush()
            currentTitle = matched
        } else if (currentTitle != null) {
            buffer.appendLine(line)
        }
    }
    flush()
    if (result.isEmpty()) result += "脑洞方案" to content.trim()
    return result
}

/**
 * 脑洞生成器弹窗：频段 + 题材 + 补充要求 → 调用当前激活的 AI 模型生成脑洞方案。
 * 生成结果只展示与复制，不自动创建书稿。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrainstormDialog(
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableIntStateOf(0) }
    var band by remember { mutableStateOf(BANDS.first()) }
    var genre by remember { mutableStateOf(GENRES.first()) }
    var requirement by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<BrainstormEntry>().apply { addAll(BrainstormStore.load(context)) } }

    fun generate() {
        if (generating) return
        generating = true
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
            val messages = listOf(
                ChatMessage(id = 0L, role = ChatRole.USER, content = buildPrompt(band, genre, requirement))
            )
            val outcome = container.aiClient.chat(config, messages, systemPrompt = "")
            outcome.onSuccess { content ->
                result = content
                val entry = BrainstormEntry(
                    time = System.currentTimeMillis(),
                    band = band,
                    genre = genre,
                    requirement = requirement,
                    content = content
                )
                history.add(0, entry)
                while (history.size > 30) history.removeAt(history.lastIndex)
                BrainstormStore.save(context, history)
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
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("脑洞生成器", style = MaterialTheme.typography.titleMedium)
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
                        GenerateTab(
                            band = band,
                            onBandChange = { band = it },
                            genre = genre,
                            onGenreChange = { genre = it },
                            requirement = requirement,
                            onRequirementChange = { requirement = it },
                            generating = generating,
                            error = error,
                            result = result,
                            onGenerate = { generate() },
                            onCopy = { text -> clipboard.setText(AnnotatedString(text)) }
                        )
                    } else {
                        HistoryTab(
                            history = history,
                            onReuse = { entry ->
                                band = entry.band.ifBlank { BANDS.first() }
                                genre = entry.genre.ifBlank { GENRES.first() }
                                requirement = entry.requirement
                                result = entry.content
                                tab = 0
                            },
                            onCopy = { text -> clipboard.setText(AnnotatedString(text)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenerateTab(
    band: String,
    onBandChange: (String) -> Unit,
    genre: String,
    onGenreChange: (String) -> Unit,
    requirement: String,
    onRequirementChange: (String) -> Unit,
    generating: Boolean,
    error: String?,
    result: String?,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit
) {
    Text("频段", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BANDS.forEach { item ->
            FilterChip(
                selected = band == item,
                onClick = { onBandChange(item) },
                label = { Text(item) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Text("题材", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GENRES.forEach { item ->
            FilterChip(
                selected = genre == item,
                onClick = { onGenreChange(item) },
                label = { Text(item) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Text("补充要求（风格/元素/雷点，自由填写）", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = requirement,
        onValueChange = onRequirementChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
        placeholder = { Text("例如：轻松搞笑、无系统、单女主……") }
    )

    Spacer(Modifier.height(14.dp))

    Button(
        onClick = onGenerate,
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
            Text("生成中…")
        } else {
            Text("生成脑洞")
        }
    }

    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    result?.let { content ->
        Spacer(Modifier.height(14.dp))
        BrainstormResultCard(content = content, onRegenerate = onGenerate, generating = generating, onCopy = onCopy)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BrainstormResultCard(
    content: String,
    onRegenerate: () -> Unit,
    generating: Boolean,
    onCopy: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("脑洞方案", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onCopy(content) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制全文", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onRegenerate, enabled = !generating) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("再来一个", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            parseSections(content).forEach { (title, body) ->
                if (body.isNotBlank()) {
                    SectionBlock(title = title, body = body, onCopy = onCopy)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionBlock(
    title: String,
    body: String,
    onCopy: (String) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onCopy(body) }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "复制$title",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<BrainstormEntry>,
    onReuse: (BrainstormEntry) -> Unit,
    onCopy: (String) -> Unit
) {
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
            Text("暂无生成历史", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    history.forEach { entry ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${entry.band} · ${entry.genre} · ${timeFormat.format(Date(entry.time))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onReuse(entry) }) {
                        Text("查看", style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { onCopy(entry.content) }) {
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
