package com.tarosuke777.ha

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.tarosuke777.ha.ui.theme.HaTheme
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WebViewScreen("http://192.168.10.11")
//                    WebViewScreen("https://www.google.com")
                }
            }
        }
    }
}

private const val TAG = "WebViewDebug" // ログ用のタグを定義

@Composable
fun WebViewScreen(url:String){
    Log.d(TAG, "WebViewScreen Composableが実行されました (再コンポーズ含む)")

    val webViewState = remember {
        Log.d(TAG, "MutableStateが初めて作成されました")
        mutableStateOf<WebView?>(null)
    }

    val canGoBackState = remember { mutableStateOf(false) }

    val webView = webViewState.value

    val canGoBack = webView?.canGoBack() == true
    Log.d(TAG, "canGoBackの現在の値: $canGoBack (StateCheck: ${canGoBackState.value})")

    BackHandler(enabled = canGoBack) {
        // WebView の履歴がある場合は戻る
        Log.i(TAG, "戻るボタンが捕捉されました: WebViewでgoBack()を実行します")
        webView?.goBack()

        canGoBackState.value = !canGoBackState.value
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            Log.d(TAG, "AndroidView factory: WebViewを新規作成します")
            WebView(context).apply {
                @Suppress("SetJavaScriptEnabled") // XSS脆弱性の警告を抑制。Web機能に必須のため。
                settings.javaScriptEnabled = true
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val newUrl = request.url.toString()
                        Log.i(TAG, "URL遷移を捕捉: $newUrl")

                        if (newUrl.lowercase().endsWith(".mp4")) {
                            Log.w(TAG, "MP4リンクを検出。外部プレイヤーに委譲します。")

                            // 外部プレイヤー起動のためのIntentを作成
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(newUrl.toUri(), "video/*")

                            try {
                                view.context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "外部プレイヤーの起動に失敗しました: ${e.message}")
                                // 外部プレイヤーがない場合はWebViewでロードを試みる
                                view.loadUrl(newUrl)
                            }

                            // WebViewでのロードはキャンセルし、外部で処理する
                            return true
                        }

                        view.loadUrl(newUrl)
                        canGoBackState.value = !canGoBackState.value
                        return true
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d(TAG, "onPageFinished: ページ読み込み完了。canGoBackStateを更新します。")
                        canGoBackState.value = !canGoBackState.value
                    }
                }
                loadUrl(url)
            }.also {
                Log.d(TAG, ".alsoブロック: WebViewインスタンスを状態変数に格納します")
                webViewState.value = it
            }
        },
        update = {
        }
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HaTheme {
        WebViewScreen("https://www.google.com")
    }
}