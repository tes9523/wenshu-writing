package com.wenshu.writing.feature.ai

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wenshu.writing.ui.util.BackgroundPermissions

/**
 * 后台保活引导：说明 AI / Skill 持续运行所需的三项权限，不阻塞使用。
 * 仅在首次长耗时生成时出现一次，关闭后不再自动弹出。
 */
@Composable
fun BackgroundKeepAliveDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    // 从系统设置页返回后重新刷新权限状态。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationEnabled = remember(refreshTick) {
        BackgroundPermissions.hasNotificationPermission(context)
    }
    val batteryIgnored = remember(refreshTick) {
        BackgroundPermissions.isIgnoringBatteryOptimizations(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("让 AI 在后台继续写作") },
        text = {
            Column {
                Text(
                    text = "长时间生成时，系统省电策略可能中断任务。以下权限仅用于 AI 与 Skill 持续运行，不用于其他用途。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PermissionRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "通知权限",
                    state = if (notificationEnabled) "已开启" else "未开启，后台进度不可见",
                    granted = notificationEnabled,
                    actionText = if (notificationEnabled) null else "去开启",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            BackgroundPermissions.openNotificationSettings(context)
                        }
                    }
                )
                PermissionRow(
                    icon = Icons.Outlined.BatteryAlert,
                    title = "忽略电池优化",
                    state = if (batteryIgnored) "已开启" else "未开启，后台可能被清理",
                    granted = batteryIgnored,
                    actionText = if (batteryIgnored) null else "去开启",
                    onAction = { BackgroundPermissions.requestIgnoreBatteryOptimizations(context) }
                )
                PermissionRow(
                    icon = Icons.Outlined.RestartAlt,
                    title = "允许自启动",
                    state = "按手机品牌在系统设置中开启",
                    granted = false,
                    actionText = "去设置",
                    onAction = { BackgroundPermissions.openAutoStartSettings(context) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "未开启时后台生成可能被系统中断，建议全部开启。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    state: String,
    granted: Boolean,
    actionText: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = state,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else if (actionText != null) {
            TextButton(onClick = onAction) {
                Text(actionText, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
