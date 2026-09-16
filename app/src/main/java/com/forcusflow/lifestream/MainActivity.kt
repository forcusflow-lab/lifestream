package com.forcusflow.lifestream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
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
                        when (currentTab) {
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
