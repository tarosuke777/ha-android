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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tarosuke777.ha.ui.theme.HaTheme

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
                    MainContent()
                }
            }
        }
    }
}

private const val TAG = "WebViewDebug" // ログ用のタグを定義
private const val CHROME_PACKAGE = "com.android.chrome"

@Composable
fun MainContent() {
    val initialUrl = "http://192.168.10.11/videos/"
    val secondUrl = "http://192.168.10.10/hms/"
    val thirdUrl = "https://www.google.com"

    var currentUrlState = remember { mutableStateOf(initialUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        WebViewScreen(
            url = currentUrlState.value,
            modifier = Modifier.weight(1f) // 修正: 引数名を明示 (modifier = )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { currentUrlState.value = initialUrl },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black // 背景色を黒に設定
                )
            ) {
                Text("MP4")
            }
            Button(
                onClick = { currentUrlState.value = secondUrl },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black // 背景色を黒に設定
                )
            ) {
                Text("HMS")
            }
            Button(
                onClick = { currentUrlState.value = thirdUrl },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black // 背景色を黒に設定
                )
            ) {
                Text("Google")
            }
        }
    }
}

@Composable
fun WebViewScreen(url: String, modifier: Modifier = Modifier) {
    Log.d(TAG, "WebViewScreen Composableが実行されました (再コンポーズ含む)")

    val webViewState = remember {
        Log.d(TAG, "MutableStateが初めて作成されました")
        mutableStateOf<WebView?>(null)
    }

//    val isRefreshingState = remember { mutableStateOf(false) }

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
        modifier = modifier,
        factory = { context ->
            Log.d(TAG, "AndroidView factory: SwipeRefreshLayoutとWebViewを新規作成します")

            SwipeRefreshLayout(context).apply {

                setOnRefreshListener {
                    Log.i(
                        TAG, "プル・トゥ・リフレッシュがトリガーされました: WebViewをリロードします"
                    )
                    isRefreshing = true
                    webViewState.value?.reload()  // WebViewをリロード
                }

                val newWebView = WebView(context).apply {
                    @Suppress("SetJavaScriptEnabled") // XSS脆弱性の警告を抑制。Web機能に必須のため。
                    settings.javaScriptEnabled = true
//                    settings.savePassword = true
//                    settings.saveFormData = true

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val newUrl = request.url.toString()
                            Log.i(TAG, "URL遷移を捕捉: $newUrl")

                            if (newUrl.lowercase().endsWith(".mp4")) {
                                Log.w(TAG, "MP4リンクを検出。外部プレイヤーに委譲します。")
                                // 外部プレイヤー起動のためのIntentを作成 (ACTION_VIEWでURLを開く)
                                val intent = Intent(Intent.ACTION_VIEW, newUrl.toUri()).apply {
                                    // 💡 Chromeのパッケージ名を明示的に指定
                                    setPackage(CHROME_PACKAGE)
                                }

                                try {
                                    view.context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "Google Chromeの起動に失敗しました (未インストールまたは無効): ${e.message}"
                                    )
                                    // Chromeがない場合、一般的なブラウザ/メディアプレイヤーでロードを試みる
                                    val fallbackIntent =
                                        Intent(Intent.ACTION_VIEW, newUrl.toUri())
                                    try {
                                        view.context.startActivity(fallbackIntent)
                                    } catch (e2: Exception) {
                                        Log.e(
                                            TAG,
                                            "他の外部アプリの起動も失敗。WebViewでロードを試みます。エラー: ${e2.message}"
                                        )
                                        view.loadUrl(newUrl)
                                    }
                                }
                                // WebViewでのロードはキャンセルし、外部で処理する
                                return true
                            }
                            isRefreshing = true
                            view.loadUrl(newUrl)
                            canGoBackState.value = !canGoBackState.value
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d(
                                TAG,
                                "onPageFinished: ページ読み込み完了。canGoBackStateを更新します。"
                            )
                            isRefreshing = false
                            canGoBackState.value = !canGoBackState.value
                        }
                    }
                    loadUrl(url)
                }

                // WebViewをSwipeRefreshLayoutに追加
                addView(newWebView)

                // WebViewインスタンスを状態変数に格納
                Log.d(TAG, ".alsoブロック: WebViewインスタンスを状態変数に格納します")
                webViewState.value = newWebView
            }

        },
        update = { swipeRefreshLayout ->
            // 3. URLが変更された場合はWebViewをロード
            val currentWebView = webViewState.value
            if (currentWebView != null && currentWebView.url != url) {
                Log.d(TAG, "AndroidView update: URLを $url に切り替えます")
                currentWebView.loadUrl(url)
                // URL変更時にはリフレッシュインジケータを表示する
                swipeRefreshLayout.isRefreshing = true
            }
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