package com.wenshu.writing.ui.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 外部存储目录：在系统外部存储根目录创建 `wenshu` 主文件夹，
 * 下设 books / setting / memo / skill 四个子目录，仅用于存放导出、生成的实体文件。
 * 数据库仍是唯一数据源，外部目录不参与业务读写。
 */
object ExternalStorage {

    const val ROOT_DIR_NAME = "wenshu"
    const val BOOKS = "books"
    const val SETTING = "setting"
    const val MEMO = "memo"
    const val SKILL = "skill"

    enum class Category(val dirName: String) {
        BOOKS("books"),
        SETTING("setting"),
        MEMO("memo"),
        SKILL("skill")
    }

    enum class AccessState {
        /** 已具备写入条件。 */
        READY,

        /** Android 10 及以下：需要运行时申请 WRITE_EXTERNAL_STORAGE。 */
        NEEDS_LEGACY_PERMISSION,

        /** Android 11 及以上：需要用户授予「所有文件访问」权限。 */
        NEEDS_ALL_FILES,

        /** 外部存储不可用（未挂载）。 */
        UNAVAILABLE
    }

    fun accessState(context: Context): AccessState {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            return AccessState.UNAVAILABLE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                AccessState.READY
            } else {
                AccessState.NEEDS_ALL_FILES
            }
        } else {
            val granted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) AccessState.READY else AccessState.NEEDS_LEGACY_PERMISSION
        }
    }

    fun isReady(context: Context): Boolean = accessState(context) == AccessState.READY

    /** Android 11+ 跳转「所有文件访问」授权页。 */
    fun allFilesAccessIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    @Suppress("DEPRECATION")
    fun rootDir(): File = File(Environment.getExternalStorageDirectory(), ROOT_DIR_NAME)

    @Suppress("DEPRECATION")
    private fun appRoot(): File = File(Environment.getExternalStorageDirectory(), ROOT_DIR_NAME)

    /** 确保根目录与四个子目录存在；无权限或失败时返回 null。 */
    fun ensureDirectories(context: Context): File? {
        if (!isReady(context)) return null
        return runCatching {
            val root = appRoot()
            if (!root.exists() && !root.mkdirs()) return null
            @Suppress("DEPRECATION")
            Category.values().forEach { category ->
                val dir = File(root, category.dirName)
                if (!dir.exists()) dir.mkdirs()
            }
            root.takeIf { it.isDirectory }
        }.getOrNull()
    }

    /** 写入 UTF-8 文本文件，返回最终文件；失败返回 null。 */
    fun saveText(
        context: Context,
        category: Category,
        fileName: String,
        content: String
    ): File? = saveBytes(context, category, fileName, content.toByteArray(Charsets.UTF_8))

    /** 写入二进制文件，返回最终文件；失败返回 null。 */
    fun saveBytes(
        context: Context,
        category: Category,
        fileName: String,
        bytes: ByteArray
    ): File? {
        val root = ensureDirectories(context) ?: return null
        return runCatching {
            val dir = File(root, category.dirName).apply { mkdirs() }
            val target = uniqueFile(dir, sanitizeFileName(fileName))
            target.writeBytes(bytes)
            target
        }.getOrNull()
    }

    /** 同名文件自动追加时间戳，避免覆盖历史导出。 */
    private fun uniqueFile(dir: File, fileName: String): File {
        val base = File(dir, fileName)
        if (!base.exists()) return base
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = base.nameWithoutExtension
        val ext = base.extension
        val suffix = if (ext.isBlank()) "" else ".$ext"
        return File(dir, "${name}_$stamp$suffix")
    }

    /** 去掉文件名中的非法字符并限制长度，中文保持原样。 */
    fun sanitizeFileName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
        val safe = cleaned.ifBlank { "未命名" }
        return if (safe.length <= 60) safe else safe.take(60)
    }

    fun signedName(stamp: String = "yyyyMMdd_HHmmss"): String =
        SimpleDateFormat(stamp, Locale.US).format(Date())

    /** 供设置页展示的目录说明。 */
    fun directoryHint(): String = "存储根目录/wenshu（books · setting · memo · skill）"
}
