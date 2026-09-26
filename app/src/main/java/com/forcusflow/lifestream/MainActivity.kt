package com.forcusflow.lifestream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.forcusflow.lifestream.ui.components.BottomNavBar
import com.forcusflow.lifestream.ui.screens.CycleMatrixScreen
import com.forcusflow.lifestream.ui.screens.HistoryScreen
import com.forcusflow.lifestream.ui.screens.SettingsScreen
import com.forcusflow.lifestream.ui.screens.TimelineScreen
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val currentTab by viewModel.currentTab.collectAsState()

            LifeStreamTheme(themeMode = themeMode) {
                val colors = LifeStreamTheme.colors
                Box(modifier = Modifier.fillMaxSize()) {
                    if (colors.wallpaperType != null) {
                        com.forcusflow.lifestream.ui.theme.ThemeWallpaper(wallpaperType = colors.wallpaperType)
                    }
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = if (colors.wallpaperType != null) androidx.compose.ui.graphics.Color.Transparent else colors.background,
                        bottomBar = {
                            BottomNavBar(
                                selectedTab = currentTab,
                                onTabSelected = { viewModel.currentTab.value = it }
                            )
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = innerPadding.calculateBottomPadding())
                        ) {
                            AnimatedContent(
                                targetState = currentTab,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                                },
                                label = "TabCrossfade"
                            ) { tab ->
                                when (tab) {
                                    0 -> TimelineScreen(viewModel = viewModel)
                                    1 -> HistoryScreen(viewModel = viewModel)
                                    2 -> CycleMatrixScreen(viewModel = viewModel)
                                    3 -> SettingsScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
