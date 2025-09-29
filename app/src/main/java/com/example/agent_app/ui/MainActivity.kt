package com.example.agent_app.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.example.agent_app.auth.GoogleTokenFetchResult
import com.example.agent_app.auth.GoogleAuthTokenProvider
import com.example.agent_app.BuildConfig
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.agent_app.data.db.AppDatabase
import com.example.agent_app.data.repo.AuthRepository
import com.example.agent_app.data.repo.GmailRepository
import com.example.agent_app.data.repo.IngestRepository
import com.example.agent_app.gmail.GmailServiceFactory
import com.example.agent_app.ui.theme.AgentAppTheme
import kotlinx.coroutines.launch

private const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

class MainActivity : ComponentActivity() {


    private val database: AppDatabase by lazy { AppDatabase.build(applicationContext) }
    private val authRepository: AuthRepository by lazy { AuthRepository(database.authTokenDao()) }
    private val ingestRepository: IngestRepository by lazy { IngestRepository(database.ingestItemDao()) }
    private val gmailRepository: GmailRepository by lazy {
        GmailRepository(
            api = GmailServiceFactory.create(),
            ingestRepository = ingestRepository,
        )
    }

    private val googleAuthTokenProvider: GoogleAuthTokenProvider by lazy { GoogleAuthTokenProvider(this) }
    private val googleSignInOptions: GoogleSignInOptions by lazy {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(GMAIL_SCOPE))
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isNotBlank() && clientId != "YOUR_GOOGLE_WEB_CLIENT_ID") {
            builder.requestIdToken(clientId)
            builder.requestServerAuthCode(clientId, true)
        }
        builder.build()
    }
    private val googleSignInClient by lazy { GoogleSignIn.getClient(this, googleSignInOptions) }
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            viewModel.onGoogleLoginFailed("Google 로그인이 취소되었습니다.")
            return@registerForActivityResult
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            handleGoogleAccount(account)
        } catch (exception: ApiException) {
            viewModel.onGoogleLoginFailed("Google 로그인 실패 (${exception.statusCode})")
        }
    }
    private val googleConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val account = pendingAccount
        if (result.resultCode == Activity.RESULT_OK && account != null) {
            pendingAccount = null
            handleGoogleAccount(account)
        } else {
            pendingAccount = null
            viewModel.onGoogleLoginFailed("필요한 권한 부여가 취소되었습니다.")
        }
    }

    private var pendingAccount: GoogleSignInAccount? = null

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(authRepository, ingestRepository, gmailRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentAppTheme {
                AssistantApp(viewModel = viewModel, onGoogleLogin = ::startGoogleLogin)
            }
        }
    }

    private fun startGoogleLogin() {
        viewModel.onGoogleLoginStarted()
        pendingAccount = null
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun handleGoogleAccount(account: GoogleSignInAccount) {
        lifecycleScope.launch {
            when (val result = googleAuthTokenProvider.fetchAccessToken(account, GMAIL_SCOPE)) {
                is GoogleTokenFetchResult.Success -> {
                    pendingAccount = null
                    viewModel.onGoogleLoginSucceeded(
                        accessToken = result.accessToken,
                        scope = GMAIL_SCOPE,
                    )
                }
                is GoogleTokenFetchResult.NeedsConsent -> {
                    pendingAccount = account
                    googleConsentLauncher.launch(result.exception.intent)
                }
                is GoogleTokenFetchResult.Failure -> {
                    pendingAccount = null
                    viewModel.onGoogleLoginFailed(result.message)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (database.isOpen) {
            database.close()
        }
    }
}

private class MainViewModelFactory(
    private val authRepository: AuthRepository,
    private val ingestRepository: IngestRepository,
    private val gmailRepository: GmailRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return MainViewModel(authRepository, ingestRepository, gmailRepository) as T
    }
}
