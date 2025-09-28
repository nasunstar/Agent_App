package com.example.agent_app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier,
    onSignIn: () -> Unit,
    onSignOut: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreenContent(
        state = state,
        onSignInClick = onSignIn,
        onSignOutClick = onSignOut ?: { viewModel.signOut() },
        modifier = modifier,
    )
}

@Composable
fun LoginScreenContent(
    state: LoginUiState,
    onSignInClick: () -> Unit,
    onSignOutClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = "Sign in to link your Google account",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSignInClick,
                enabled = !state.isLoading,
            ) {
                Text(text = "Sign in with Google")
            }
            Spacer(modifier = Modifier.height(16.dp))
            when {
                state.isLoading -> CircularProgressIndicator()
                state.accountEmail != null -> {
                    Text(
                        text = "Signed in as ${state.accountEmail}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (onSignOutClick != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onSignOutClick) {
                            Text(text = "Sign out")
                        }
                    }
                }
                state.errorMessage != null -> Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview
@Composable
private fun LoginScreenPreview() {
    MaterialTheme {
        LoginScreenContent(
            state = LoginUiState(accountEmail = "user@example.com"),
            onSignInClick = {},
            onSignOutClick = {},
        )
    }
}
