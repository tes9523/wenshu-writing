package com.wenshu.writing.feature.update

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 一条版本更新公告。 */
data class UpdateNote(
    val version: String,
    val title: String,
    val highlights: List<String>
)

/** 历史更新公告（按版本倒序排列）。 */
val UPDATE_NOTES: List<UpdateNote> = listOf(
    UpdateNote(
        version = "1.1.5",
        title = "全新内置写作技能 · 后台保活 · 状态通知",
        highlights = listOf(
            "内置写作技能升级为 novel-writer-manager，支持章节创作、文档同步与项目初始化三种模式",
            "工具链适配新技能：设定库/人物、世界观、大纲、章节摘要可直接读写",
            "新增后台保活权限引导：通知权限、忽略电池优化、自启动，仅用于 AI 持续生成",
            "AI 思考 / 生成期间展示通知栏常驻状态，点击可直接返回当前对话",
            "生成完成、主动停止或中断后通知自动消失"
        )
    ),
    UpdateNote(
        version = "1.1.4",
        title = "书稿回收站 · 脑洞生成器 · 分类修复",
        highlights = listOf(
            "修复自定义分类重命名后被自动删除的问题，删除分类新增确认弹窗",
            "移除设定库归属下拉中无效的「全局公共素材」选项",
            "书稿删除先移入回收站，可随时恢复，永久删除才会清空章节与设定",
            "AI 对话新增脑洞生成器：频段 + 题材 + 补充要求，一键生成书名/脑洞/金手指/爽点",
            "优化 AI 首字响应链路，减少发送前的本地等待耗时"
        )
    ),
    UpdateNote(
        version = "1.1.3",
        title = "存储落库修复 · 后台保活 · 切换提速",
        highlights = listOf(
            "修复 Skill 写入设定库 / 章节后找不到内容的问题，写入后自动回读校验",
            "写入失败会在对话气泡中明确提示，不再显示「已保存」误导话术",
            "AI 生成时切到手机后台再回前台，不再中断，可继续接收流式内容",
            "新增版本更新公告，设置页可随时查看历史公告",
            "优化底部 Tab 切换渲染与状态缓存，减少卡顿与重复加载"
        )
    ),
    UpdateNote(
        version = "1.1.2",
        title = "后台保活 · 会话记忆 · 资料分流",
        highlights = listOf(
            "生成中切换页面不再中断，返回对话页继续接收内容",
            "仅明确「创建新书 / 续写书稿」指令才会新建书稿",
            "离开 AI 对话页再返回，自动恢复原会话与滚动位置",
            "分析报告、扫榜资料等非书稿文档统一存入备忘录"
        )
    ),
    UpdateNote(
        version = "1.1.1",
        title = "编辑区扩容 · 启动页 · 思考流",
        highlights = listOf(
            "设定库抽屉输入框向下扩容，消除底部空白",
            "@Skill 名称高亮加粗，输入框与 AI 气泡统一样式",
            "Skill 保存确认改为气泡内预览，取消阻断弹窗",
            "新增启动页「智启笔墨，编织万象」，可点击跳过",
            "区分【思考中】与【正在生成】，思考内容不写入最终回复",
            "设置新增「外观」子页面，主题 / 背景 / 提示音 / 字体集中管理"
        )
    ),
    UpdateNote(
        version = "1.1.0",
        title = "自定义外观 · 抽屉样式",
        highlights = listOf(
            "设定库抽屉高度调整为屏幕 4/5，展示更多内容",
            "新增自定义外观：主题模式、主题色、背景色、背景图、提示音、字体",
            "@ 唤起 Skill 面板，选中后回填高亮名称",
            "隔离书稿创建触发逻辑，制作 / 调试 Skill 不再生成书稿"
        )
    )
)

/** 读取当前安装包的版本号。 */
fun currentVersionName(context: Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()

/** 记录已经展示过公告的版本，保证同一版本只自动弹一次。 */
object UpdateAnnouncementStore {
    private const val PREFS = "update_announcement"
    private const val KEY_SEEN_VERSION = "seen_version"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSeen(context: Context, version: String): Boolean =
        prefs(context).getString(KEY_SEEN_VERSION, null) == version

    fun markSeen(context: Context, version: String) {
        prefs(context).edit().putString(KEY_SEEN_VERSION, version).apply()
    }
}

/** 启动时自动弹出的更新公告弹窗。 */
@Composable
fun UpdateAnnouncementDialog(
    note: UpdateNote,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("版本更新", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "v${note.version}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                note.highlights.forEach { item ->
                    Row {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}

/** 设置页手动查看的历史更新公告。 */
@Composable
fun UpdateHistoryDialog(
    notes: List<UpdateNote> = UPDATE_NOTES,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("更新公告", style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(notes, key = { it.version }) { note ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "v${note.version}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = note.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        note.highlights.forEach { item ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(text = item, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
