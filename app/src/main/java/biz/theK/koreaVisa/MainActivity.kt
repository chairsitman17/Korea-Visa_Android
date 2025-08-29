package biz.theK.koreaVisa

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.NotificationManager
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    lateinit var webView: WebView
    private var backPressedTime: Long = 0
    private var initialLoadComplete: Boolean = false
    private lateinit var toast: Toast
    private val viewModel: MainViewModel by viewModels()
    private var connectionErrorDialog: AlertDialog? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 사용자의 권한 설정 수신 받기
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            val js = "window.dispatchEvent(new CustomEvent('notification-denied'));"
            viewModel.sendToJavaScript(js)
        }
    }

    // 사용자가 앱이 켜진 상태에서 알림을 클릭했을때, WebView에서 업데이트하기
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val url = intent.getStringExtra("url")
        if (!url.isNullOrEmpty()) {
            webView.loadUrl(url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Splash 화면 띄우기
        val splashLayout = findViewById<View>(R.id.splashLayout)

        // 웹 페이지가 로딩 중일때 ProgressBar 띄우기
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // 첫 시작 시 인터넷 연결 확인
        val connectivityManager = getSystemService(ConnectivityManager::class.java) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        val isConnected = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!isConnected) {
            AlertDialog.Builder(this)
            var alert = AlertDialog.Builder(this@MainActivity)
            Log.d("NetworkCallback", "No internet connection")
            // 현재 인터넷 연결을 확인하라는 경고창을 띄우고, retry, cancel 버튼을 띄우기
            alert.setTitle("No internet connection")
            alert.setMessage("To continue using the Korea-Visa, please check your internet connection.")
            alert.setCancelable(false)
            connectionErrorDialog = alert.show()
        }

        // 인터넷 연결을 확인하는 Callback 설정
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            // network is available for use
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                if (connectionErrorDialog != null) {
                    // 인터넷 연결이 됐을때, dialog 창 닫기
                    Log.d("NetworkCallback", "Network is available")
                    toast = Toast.makeText(this@MainActivity, "You are now online", Toast.LENGTH_SHORT)
                    toast.show()

                    connectionErrorDialog?.dismiss()
                    connectionErrorDialog = null

                    if (!initialLoadComplete) {
                        webView.reload()
                    }
                } else {
                    Log.d("NetworkCallback", "No dialog to dismiss")
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                var alert = AlertDialog.Builder(this@MainActivity)
                Log.d("NetworkCallback", "Network connection lost")
                // 현재 인터넷 연결을 확인하라는 경고창을 띄우고, retry, cancel 버튼을 띄우기
                alert.setTitle("Network connection lost")
                alert.setMessage("To continue using the Korea-Visa, please check your internet connection.")
                alert.setCancelable(false)
                connectionErrorDialog = alert.show()
            }

        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        // splashFadeOutEvent가 true로 업데이트 되면
        // splash screen 제거하기
        viewModel.splashFadeOutEvent.observe(this) { shouldFade ->
            if (shouldFade) {
                splashLayout.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        splashLayout.visibility = View.GONE
                    }
                    .start()
            }
        }

        // 구글 인증 observe
        viewModel.googleAuthEvent.observe(this) { _ ->
            val signInOption = viewModel.buildGoogleSignInOption(this)
            GoogleSignInActivity.launchCredentialManager(this, signInOption, { tokenId ->
                val loginJS = "window.dispatchEvent(new CustomEvent('firebase-login', { detail: { token: '${tokenId}', provider: 'google' } }));"
                viewModel.sendToJavaScript(loginJS)
            })
        }
        // 알림 권한 확인 observe
        viewModel.notificationStatus.observe(this) { _ ->
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // Android 12 이하에서는 앱 설정이나 NotificationManager로 확인
                val notificationManager =
                    this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.areNotificationsEnabled()
            }
            val statusString = if (status) "granted" else "denied"
            val js = "window.receiveNotificationPermission && window.receiveNotificationPermission('${statusString}');"
            viewModel.sendToJavaScript(js)
        }
        // 알림 권한 요청 observe
        viewModel.notificationToken.observe(this) { _ ->
            // 알림 권한 사용자에게 요청하기
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                        val js = "window.dispatchEvent(new CustomEvent('notification-denied'));"
                        viewModel.sendToJavaScript(js)
                        return@addOnCompleteListener
                    }

                    // FCM token
                    val token = task.result
                    Log.d("FCM", "FCM Token: $token")

                    // 예: JS로 전달
                    val js =
                        "window.receiveNotificationToken && window.receiveNotificationToken('${token}');"
                    viewModel.sendToJavaScript(js)
                }
        }

        webView = findViewById(R.id.webview)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = "K-VisaApp_Android"
        webView.settings.domStorageEnabled = true
        webView.settings.textZoom = 100
        webView.settings.allowFileAccess = true

        // 쿠키 허용
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true) // API 21+

        // WebView에서 App에게 메세지를 보낼 수 있는 환경 구성
        webView.addJavascriptInterface(viewModel.bridge, "AndroidBridge")

        // Javascript 커맨드를 injection 시킬 수 있게 구성
        viewModel.jsCommand.observe(this) { js ->
            webView.evaluateJavascript(js, null)
        }

        // 페이지 로드 전, 웹페이지가 전부 불러와졌는지 확인하기 위해 Javascript에 event listener 만들기
        val onLoadJS = "window.AndroidBridge.sendDataFromJS('pageDidFullyLoad');"

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "*/*"
                intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "application/pdf"))
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

                val chooserIntent = Intent.createChooser(intent, "Select file")
                startActivityForResult(chooserIntent, 1001)
                return true
            }
        }

        // 웹 페이지 로드
        webView.loadUrl("https://korea-visa.kr")
        webView.webViewClient = object : WebViewClient() {
            // 로딩 중일 때 ProgressBar 띄우기
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!initialLoadComplete) {
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                viewModel.sendToJavaScript(onLoadJS)
                CookieManager.getInstance().flush()
                initialLoadComplete = true
                progressBar.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val errorCode = error?.errorCode

                when (errorCode) {
                    // 네트워크 연결 오류
                    ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_TIMEOUT -> {
//                        Toast.makeText(this@MainActivity, "An unexpected connection error occurred. refreshing...", Toast.LENGTH_LONG).show()
                        webView.reload()
                    }
//                    // 기타 오류
//                    else -> {
//                        Toast.makeText(this@MainActivity, "웹 페이지 로드 오류: $errorMsg", Toast.LENGTH_LONG).show()
//                        Log.e("WebViewError", "기타 오류: $errorMsg, URL: $failingUrl")
//                    }
                }
            }
        }

        // 앱이 꺼진 상태에서 알림을 클릭했을 경우
        // WebView가 완전히 로딩이 완료되었을 때, 알림의 payload에 있는 url로 이동하게 하기
        intent.getStringExtra("url")?.let { webView.loadUrl(it) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && filePathCallback != null) {
            val results: Array<Uri>? = when {
                resultCode != Activity.RESULT_OK -> null
                data?.clipData != null -> {
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - backPressedTime < 2000) {
                super.onBackPressed()
                toast.cancel()  // 두 번째 눌렀을 때 토스트 종료
                finishAffinity()
            } else {
                backPressedTime = currentTime
                toast = Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT)
                toast.show()
            }
        }
    }
}