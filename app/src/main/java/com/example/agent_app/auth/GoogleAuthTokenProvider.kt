package com.example.agent_app.auth

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthTokenProvider(private val context: Context) {

    suspend fun fetchAccessToken(
        account: GoogleSignInAccount,
        scope: String,
    ): GoogleTokenFetchResult = withContext(Dispatchers.IO) {
        val authAccount: Account = account.account
            ?: return@withContext GoogleTokenFetchResult.Failure("선택한 계정 정보를 확인할 수 없습니다.")
        val oauthScope = if (scope.startsWith("oauth2:")) scope else "oauth2:$scope"
        try {
            val token = GoogleAuthUtil.getToken(context, authAccount, oauthScope)
            GoogleTokenFetchResult.Success(token)
        } catch (recoverable: UserRecoverableAuthException) {
            GoogleTokenFetchResult.NeedsConsent(recoverable)
        } catch (auth: GoogleAuthException) {
            GoogleTokenFetchResult.Failure(auth.localizedMessage ?: "Google 인증에 실패했습니다.")
        } catch (io: IOException) {
            GoogleTokenFetchResult.Failure(io.localizedMessage ?: "네트워크 오류로 토큰을 가져오지 못했습니다.")
        }
    }

    suspend fun invalidate(token: String) = withContext(Dispatchers.IO) {
        GoogleAuthUtil.clearToken(context, token)
    }
}

sealed class GoogleTokenFetchResult {
    data class Success(val accessToken: String) : GoogleTokenFetchResult()
    data class NeedsConsent(val exception: UserRecoverableAuthException) : GoogleTokenFetchResult()
    data class Failure(val message: String) : GoogleTokenFetchResult()
}
