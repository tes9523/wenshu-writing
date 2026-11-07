package com.wenshu.writing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenshu.writing.domain.model.AppSettings
import com.wenshu.writing.service.AiGenerationService
import com.wenshu.writing.ui.components.CoverFiles
import com.wenshu.writing.ui.components.StoragePermissionDialog
import com.wenshu.writing.ui.navigation.AppNavHost
import com.wenshu.writing.ui.theme.WenShuTheme
import com.wenshu.writing.ui.util.ExternalStorage

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as WenShuApplication).container
        handleOpenChatIntent(intent)

        setContent {
            val settings by container.preferencesRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            WenShuTheme(
                themeMode = settings.themeMode,
                themeColorName = settings.themeColorName,
                backgroundColorName = settings.backgroundColorName,
                hasBackgroundImage = settings.backgroundImagePath != null,
                fontName = settings.fontName
            ) {
                // 全局背景图：绘制在所有页面底层，主题背景色此时为透明。
                Box(modifier = Modifier.fillMaxSize()) {
                    BackgroundImage(settings.backgroundImagePath)
                    AppNavHost(container = container)
                    StoragePermissionGate()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenChatIntent(intent)
    }

    /** 通知栏点击「返回对话」：交给 AppNavHost 切回 AI 会话页。 */
    private fun handleOpenChatIntent(intent: Intent?) {
        if (intent?.action != AiGenerationService.ACTION_OPEN_CHAT) return
        (application as WenShuApplication).container.requestOpenChat()
        intent.action = null
    }
}

@Composable
private fun BackgroundImage(path: String?) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = CoverFiles.decode(path, maxSide = 1080)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 首次使用自动申请存储权限：授权后创建 /wenshu 及四个子目录；
 * 拒绝则不做任何事，全部数据继续保存在应用数据库内。
 */
@Composable
private fun StoragePermissionGate() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ExternalStorage.isReady(context)) {
            ExternalStorage.ensureDirectories(context)
            return@LaunchedEffect
        }
        val prefs = context.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PERMISSION_PROMPTED, false)) {
            prefs.edit().putBoolean(KEY_PERMISSION_PROMPTED, true).apply()
            // 排除外部存储不可用（如未挂载）的场景，不在此时打扰用户。
            if (ExternalStorage.accessState(context) != ExternalStorage.AccessState.UNAVAILABLE) {
                showDialog = true
            }
        }
    }

    // 从系统授权页返回后自动创建目录，无需用户再次操作。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && ExternalStorage.isReady(context)) {
                ExternalStorage.ensureDirectories(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDialog) {
        StoragePermissionDialog(onDismiss = { showDialog = false })
    }
}

private const val STORAGE_PREFS = "wenshu_storage"
private const val KEY_PERMISSION_PROMPTED = "permission_prompted"
