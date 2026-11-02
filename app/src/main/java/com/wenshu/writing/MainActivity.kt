package com.wenshu.writing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wenshu.writing.domain.model.AppSettings
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
            WenShuTheme(themeMode = settings.themeMode) {
                AppNavHost(container = container)
            }
        }
    }
}
