package biz.theK.koreaVisa

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.launch

class GoogleSignInActivity : AppCompatActivity() {
    companion object {
        fun launchCredentialManager(context: Context, googleIdOption: GetGoogleIdOption, onTokenReceived: (String) -> Unit) {
            val credentialManager = CredentialManager.create(context)
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // context가 LifecycleOwner인지 확인 (Activity여야 함)
            if (context is LifecycleOwner) {
                context.lifecycleScope.launch {
                    try {
                        // 구글 인증창
                        val result = credentialManager.getCredential(
                            context = context,
                            request = request
                        )
                        // 인증 성공!
                        // 인증 토큰 idToken 값을 Javascript로 넘겨줘서 로그인 마저 완료하기
                        val credential = result.credential
                        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val token = googleIdTokenCredential.idToken
                            // 토큰 callback
                            onTokenReceived(token)
                        }
                    } catch (e: GetCredentialException) {
                        Log.e("GoogleSignInActivity", "Credential error: ${e.localizedMessage}")
                    }
                }
            } else {
                Log.e("GoogleSignInActivity", "Context is not LifecycleOwner")
            }
        }
    }
}