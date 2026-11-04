package com.wenshu.writing

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenshu.writing.domain.model.AppSettings
import com.wenshu.writing.ui.components.CoverFiles
import com.wenshu.writing.ui.navigation.AppNavHost
import com.wenshu.writing.ui.theme.WenShuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as WenShuApplication).container

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
                }
            }
        }
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
