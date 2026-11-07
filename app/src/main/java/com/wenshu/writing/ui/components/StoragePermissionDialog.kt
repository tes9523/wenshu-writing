package com.wenshu.writing.ui.components

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.wenshu.writing.ui.util.ExternalStorage

/** 首次使用 / 导出前申请存储权限；拒绝时保持仅数据库存储。 */
@Composable
fun StoragePermissionDialog(
    onDismiss: () -> Unit,
    onGranted: () -> Unit = {},
    onDecline: () -> Unit = {}
) {
    val context = LocalContext.current
    val requestLegacy = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ExternalStorage.ensureDirectories(context)
            onGranted()
        } else {
            Toast.makeText(context, "未授权，已回落为仅本地数据库存储", Toast.LENGTH_SHORT).show()
            onDecline()
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要存储权限") },
        text = {
            Text(
                "授权后会在手机存储根目录创建 wenshu 文件夹：" +
                    "books（书稿）、setting（设定库）、memo（备忘录）、skill（技能）。" +
                    "导出文件统一写入对应子目录，编码统一 UTF-8。\n\n" +
                    "不授权不影响写作与对话，内容始终保存在应用数据库内，仅无法导出外部文件。"
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (ExternalStorage.accessState(context)) {
                        ExternalStorage.AccessState.NEEDS_LEGACY_PERMISSION ->
                            requestLegacy.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

                        ExternalStorage.AccessState.NEEDS_ALL_FILES -> {
                            val opened = runCatching {
                                context.startActivity(ExternalStorage.allFilesAccessIntent(context))
                            }.isSuccess
                            if (!opened) {
                                Toast.makeText(
                                    context,
                                    "无法打开授权页，请到系统设置中手动开启",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            onDismiss()
                        }

                        ExternalStorage.AccessState.READY -> {
                            ExternalStorage.ensureDirectories(context)
                            onGranted()
                            onDismiss()
                        }

                        ExternalStorage.AccessState.UNAVAILABLE -> onDismiss()
                    }
                }
            ) { Text("去授权") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDecline()
                    onDismiss()
                }
            ) { Text("仅本地存储") }
        }
    )
}

/** 判断外部存储是否可用，供导出入口调用。 */
fun requireStorageOrHint(context: Context, onHint: () -> Unit): Boolean {
    if (ExternalStorage.isReady(context)) {
        ExternalStorage.ensureDirectories(context)
        return true
    }
    onHint()
    return false
}
