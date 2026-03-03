package app.webcodex.codex.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.webcodex.codex.ui.screens.ConnectScreen
import app.webcodex.codex.ui.screens.ChatScreen
import app.webcodex.codex.ui.theme.CodexTheme

@Composable
fun CodexApp(
    viewModel: CodexViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    CodexTheme(settings = uiState.settings) {
        val currentUiState by viewModel.uiState.collectAsState()
        when {
            currentUiState.isConnected || (currentUiState.hasOfflineCache && currentUiState.preferOfflineHome) -> ChatScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel
            )
            else -> ConnectScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel
            )
        }
    }
}
