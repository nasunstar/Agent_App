package com.example.agent_app.auth

import android.content.Context
import android.content.Intent
import com.example.agent_app.data.repo.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleAuthClient(
    context: Context,
    private val authRepository: AuthRepository,
    serverClientId: String = DEFAULT_WEB_CLIENT_ID,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestServerAuthCode(serverClientId, true)
            .build(),
    )

    fun signInIntent(): Intent = signInClient.signInIntent

    suspend fun handleSignInResult(task: Task<GoogleSignInAccount>): GoogleAuthResult =
        withContext(ioDispatcher) {
            try {
                val account = task.getResult(ApiException::class.java)
                val email = account.email
                val serverAuthCode = account.serverAuthCode

                if (email.isNullOrBlank()) {
                    return@withContext GoogleAuthResult.Failure("Missing Google account email")
                }
                if (serverAuthCode.isNullOrBlank()) {
                    return@withContext GoogleAuthResult.Failure("Missing server auth code")
                }

                val tokens = authRepository.exchangeServerAuthCode(serverAuthCode)
                authRepository.upsertGoogleSession(email, serverAuthCode, tokens)
                GoogleAuthResult.Success(email)
            } catch (error: ApiException) {
                GoogleAuthResult.Failure(error.localizedMessage ?: error.statusCode.toString())
            }
        }

    suspend fun signOut(): GoogleAuthResult = withContext(ioDispatcher) {
        try {
            signInClient.signOut().await()
            GoogleAuthResult.SignedOut
        } catch (error: Exception) {
            GoogleAuthResult.Failure(error.localizedMessage ?: "Sign out failed")
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (continuation.isCancelled) return@addOnCompleteListener
            if (task.isSuccessful) {
                continuation.resume(task.result, null)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Unknown Task failure"),
                )
            }
        }
    }

    companion object {
        const val DEFAULT_WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID"
    }
}

sealed interface GoogleAuthResult {
    data class Success(val accountEmail: String) : GoogleAuthResult
    data class Failure(val message: String) : GoogleAuthResult
    data object SignedOut : GoogleAuthResult
}
