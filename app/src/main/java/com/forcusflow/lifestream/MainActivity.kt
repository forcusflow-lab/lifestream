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
import android.content.Intent
import com.forcusflow.lifestream.ui.components.BottomNavBar
import com.forcusflow.lifestream.ui.screens.CycleMatrixScreen
import com.forcusflow.lifestream.ui.screens.HistoryScreen
import com.forcusflow.lifestream.ui.screens.MemoScreen
import com.forcusflow.lifestream.ui.screens.SettingsScreen
import com.forcusflow.lifestream.ui.screens.TimelineScreen
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        com.forcusflow.lifestream.widget.TodayTimelineWidgetReceiver.updateAll(this)
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val currentTab by viewModel.currentTab.collectAsState()
            val todayBadgeCount by viewModel.todayBadgeCount.collectAsState()

            LifeStreamTheme(themeMode = themeMode) {
                val colors = LifeStreamTheme.colors
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = colors.background,
                    bottomBar = {
                        BottomNavBar(
                            selectedTab = currentTab,
                            todayBadgeCount = todayBadgeCount,
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
                                3 -> MemoScreen(viewModel = viewModel)
                                4 -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_SHEET, false) == true) {
            viewModel.currentTab.value = 0
            viewModel.requestOpenAddSheet()
        }
    }

    companion object {
        const val EXTRA_OPEN_ADD_SHEET = "extra_open_add_sheet"
    }
}
