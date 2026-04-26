package com.boxedagent.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.boxedagent.android.ui.AppViewModel
import com.boxedagent.android.ui.BoxedAgentApp
import com.boxedagent.android.ui.theme.BoxedAgentTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.0f)) {
                BoxedAgentTheme {
                    BoxedAgentApp(viewModel)
                }
            }
        }
    }
}
