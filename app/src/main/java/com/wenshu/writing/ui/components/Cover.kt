package com.wenshu.writing.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wenshu.writing.domain.model.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 书稿封面的本地存取：从相册读取 -> 压缩 -> 写入应用私有目录。
 * 保存的是压缩后的 JPEG，避免大图占用过多存储。
 */
object CoverFiles {

    /** 从相册 Uri 读取并压缩到最长边不超过 maxSide 的位图。 */
    suspend fun loadFromUri(context: Context, uri: Uri, maxSide: Int = 720): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxSide &&
                    bounds.outHeight / (sample * 2) >= maxSide
                ) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@runCatching null
                scaleDown(bitmap, maxSide)
            }.getOrNull()
        }

    /** 压缩写入私有目录，并清理该书稿的旧封面文件，返回文件绝对路径。 */
    suspend fun save(context: Context, novelId: Long, bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, "covers").apply { mkdirs() }
                val target = File(dir, "cover_${novelId}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                }
                dir.listFiles { file -> file.name.startsWith("cover_${novelId}_") && file != target }
                    ?.forEach { it.delete() }
                target.absolutePath
            }.getOrNull()
        }

    /** 读取本地封面图；文件不存在时返回 null，由界面回退默认封面。 */
    suspend fun decode(path: String?, maxSide: Int = 480): Bitmap? =
        withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) return@withContext null
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxSide &&
                    bounds.outHeight / (sample * 2) >= maxSide
                ) {
                    sample *= 2
                }
                val bitmap = BitmapFactory.decodeFile(
                    path,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: return@runCatching null
                scaleDown(bitmap, maxSide)
            }.getOrNull()
        }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}

/** 书稿封面：有自定义封面时展示图片，否则展示默认封面。 */
@Composable
fun NovelCover(novel: Novel, modifier: Modifier = Modifier) {
    val path = novel.coverPath
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = CoverFiles.decode(path)
    }
    val image = bitmap
    if (path.isNullOrBlank() || image == null) {
        InitialCover(text = novel.title, modifier = modifier)
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "《${novel.title}》封面",
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

/** 打开相册选择封面；选中的图片会先压缩再回调，用于预览。返回触发选择的方法。 */
@Composable
fun rememberCoverPicker(onPicked: (Bitmap) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            CoverFiles.loadFromUri(context, uri)?.let(onPicked)
        }
    }
    return { launcher.launch("image/*") }
}

/** 选图后的封面预览确认弹窗。 */
@Composable
fun CoverPreviewDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预览封面") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "封面预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 180.dp, height = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Text(
                    text = "保存后将同步更新书稿列表与书稿详情页",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
