package biz.theK.koreaVisa

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption

class MainViewModel : ViewModel() {
    private val _jsCommand = MutableLiveData<String>()
    val jsCommand: LiveData<String> = _jsCommand
    // Splash 화면에서 사용하는 LiveData, opacity 조정에 사용됩니다.
    private val _splashFadeOutEvent = MutableLiveData<Boolean>()
    val splashFadeOutEvent: LiveData<Boolean> = _splashFadeOutEvent
    // View에게 구글 인증을 호출하도록 하는 LiveData
    private val _googleAuthEvent = MutableLiveData<String>()
    val googleAuthEvent: LiveData<String> = _googleAuthEvent
    // 앱의 알림 권한을 반환하도록 하는 LiveData
    private val _notificationStatus = MutableLiveData<String>()
    val notificationStatus: LiveData<String> = _notificationStatus
    // 앱의 알림 토큰을 반환하도록 하는 LiveData
    private val _notificationToken = MutableLiveData<String>()
    val notificationToken: LiveData<String> = _notificationToken

    class jsBridge(private val viewModel: MainViewModel) : Any() {
        @JavascriptInterface
        fun sendDataFromJS(message: String) {
            try {
                Log.d("ViewModel", "JS에서 받은 메세지: $message")
                // 페이지 로드 되었을 경우, Splash 화면 제거
                if (message == "pageDidFullyLoad") {
                    viewModel.setSplashFadeOutEvent(true)
                }
                // 구글 인증 요청 받았을 경우, 구글 인증 화면 실행
                if (message == "login_google") {
                    viewModel.callGoogleAuthEvent()
                }
                // 앱에서 알림 권한이 있는지 요청할 경우, 권한 상태를 반환
                if (message == "check_notification_permission") {
                    viewModel.checkNotificationPermissionStatus()
                }
                // 앱에서 알림 권한을 요청할 경우, 알림 권한 사용자에게 물어보고, 토큰 반환
                if (message == "request_notification_permission") {
                    viewModel.getNotificationToken()
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "JS 호출 중 예외 발생: ${e.message}", e)
            }
        }
    }
    val bridge = jsBridge(this)

    fun sendToJavaScript(js: String) {
        _jsCommand.value = "javascript:$js"
    }

    fun getRandomString(length: Int) : String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    // jsBridge에서 사용할 수 있게 하기 위해 선언한 getters/setters
    // 이렇게 하면... private val로써 선언한 의미가 없습니다만... 구조상 어쩔 수 없는 선택...
    fun setSplashFadeOutEvent(value: Boolean) {
        _splashFadeOutEvent.postValue(value)
    }
    fun callGoogleAuthEvent() {
        _googleAuthEvent.postValue(getRandomString(5))
    }
    fun checkNotificationPermissionStatus() {
        _notificationStatus.postValue("check")
    }
    fun getNotificationToken() {
        _notificationToken.postValue("token")
    }

    fun buildGoogleSignInOption(context: Context): GetGoogleIdOption {
        return GetGoogleIdOption.Builder()
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
    }
}