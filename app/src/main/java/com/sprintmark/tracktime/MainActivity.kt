package com.sprintmark.tracktime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sprintmark.tracktime.ui.TrackTimeApp
import com.sprintmark.tracktime.ui.TrackTimeViewModel
import com.sprintmark.tracktime.ui.theme.TrackTimeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TrackTimeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrackTimeTheme {
                Surface(modifier = Modifier) {
                    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
                    TrackTimeApp(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
