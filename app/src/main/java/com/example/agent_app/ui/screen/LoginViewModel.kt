package com.example.agent_app.ui.screen

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent_app.auth.GoogleAuthClient
import com.example.agent_app.auth.GoogleAuthResult
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val googleAuthClient: GoogleAuthClient,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun signInIntent(): Intent = googleAuthClient.signInIntent()

    fun onSignInResult(resultTask: Task<GoogleSignInAccount>) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = googleAuthClient.handleSignInResult(resultTask)
            when (result) {
                is GoogleAuthResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        accountEmail = result.accountEmail,
                        errorMessage = null,
                    )
                }
                is GoogleAuthResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                GoogleAuthResult.SignedOut -> _state.update {
                    it.copy(isLoading = false, accountEmail = null)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = googleAuthClient.signOut()) {
                GoogleAuthResult.SignedOut -> _state.update {
                    it.copy(isLoading = false, accountEmail = null, errorMessage = null)
                }
                is GoogleAuthResult.Failure -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is GoogleAuthResult.Success -> _state.update {
                    it.copy(isLoading = false, accountEmail = result.accountEmail)
                }
            }
        }
    }
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val accountEmail: String? = null,
    val errorMessage: String? = null,
)
