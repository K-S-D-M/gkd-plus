package li.gkd.app.feature.snapshot

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.util.AiRuleGenerator
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils
import li.gkd.app.util.launchLogged
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

@Serializable
data class SnapshotWebReviewRoute(
    val snapshotId: Long,
) : NavKey

/** 官方 inspect 工具 ZIP 包地址（GitHub Pages） */
private const val INSPECT_ZIP_URL = "https://k-s-d-m.github.io/gkd-subscription-public/inspect-dist.zip"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun SnapshotWebReviewPage(route: SnapshotWebReviewRoute) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 用 AtomicReference 保证 JS 接口读到最新值
    val snapshotJsonRef = remember { AtomicReference<String?>(null) }
    val screenshotRef = remember { AtomicReference<String?>(null) }
    var snapshotJson by remember { mutableStateOf<String?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 1. 准备本地自定义审核页（主方案，稳定可靠）
    // 官方 inspect 作为实验选项，默认使用本地版
    val startUrl = "https://k-s-d-m.github.io/gkd-subscription-public/"
    val webViewState = rememberWebViewState(url = startUrl)

    // 2. 加载快照数据
    LaunchedEffect(route.snapshotId) {
        try {
            val json = withContext(Dispatchers.IO) {
                SnapshotRepository.snapshotFile(route.snapshotId).readText()
            }
            val shotFile: File = SnapshotRepository.screenshotFile(route.snapshotId)
            val b64 = withContext(Dispatchers.IO) {
                if (shotFile.exists()) {
                    Base64.encodeToString(shotFile.readBytes(), Base64.NO_WRAP)
                } else null
            }
            snapshotJsonRef.set(json)
            screenshotRef.set(b64)
            snapshotJson = json
            // 如果页面已加载完成，直接注入
            webViewRef?.let { wv ->
                if (pageReady) {
                    injectSnapshotData(wv, json, b64)
                }
            }
        } catch (e: Exception) {
            LogUtils.d("加载快照失败", e)
            loadError = "加载快照失败：${e.message}"
        }
    }

    val jsApi = remember {
        object {
            @JavascriptInterface
            fun getSnapshotJson(): String = snapshotJsonRef.get() ?: ""

            @JavascriptInterface
            fun getScreenshot(): String = screenshotRef.get() ?: ""

            @JavascriptInterface
            fun copyText(text: String) {
                ToastUtils.copyText(text)
            }

            @JavascriptInterface
            fun toast(text: String) {
                ToastUtils.toast(text)
            }

            @JavascriptInterface
            fun openAiSettings() {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    // TODO: 跳转到 AI 服务商设置页（需确认路由名）
                    ToastUtils.toast("AI 设置入口：请在 App 设置-高级设置-AI 服务商中配置")
                }
            }

            @JavascriptInterface
            fun aiGenerateRule(nodeInfoJson: String) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    appScope.launchLogged {
                        try {
                            AiRuleGenerator.generateRuleForNode(
                                route.snapshotId,
                                nodeInfoJson,
                                onProgress = { step ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        ToastUtils.toast(step, forced = true)
                                    }
                                }
                            )
                        } catch (e: Exception) {
                            ToastUtils.toast("AI 生成失败：${e.message}")
                        }
                    }
                }
            }
        }
    }

    val webViewClient = remember {
        object : AccompanistWebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 尽早注入，Vue 应用初始化时就能读到
                val json = snapshotJsonRef.get()
                if (json != null) {
                    injectSnapshotEarly(view, json, screenshotRef.get())
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                pageReady = true
                val json = snapshotJsonRef.get()
                if (json != null) {
                    injectSnapshotData(view, json, screenshotRef.get())
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    loadError = "页面加载失败：${error?.description}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("网页审核 Web Review") })
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loadError != null -> {
                    Text(
                        text = loadError ?: "未知错误",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                snapshotJson == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    WebView(
                        modifier = Modifier.fillMaxSize(),
                        state = webViewState,
                        client = webViewClient,
                        onCreated = { wv ->
                            webViewRef = wv
                            wv.addJavascriptInterface(jsApi, "GkdBridge")
                            wv.addJavascriptInterface(jsApi, "Android")
                            wv.settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mediaPlaybackRequiresUserGesture = false
                                // 缩放支持（配合网页端的 viewport width=1280）
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                // 使 viewport meta 生效（等比缩放）
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                // 缓存加速
                                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                domStorageEnabled = true
                            }
                            // 页面加载完成后会通过 onPageFinished 注入数据
                        },
                    )
                }
            }
        }
    }
}

/** 页面开始加载时尽早注入（供 Vue 初始化读取） */
private fun injectSnapshotEarly(view: WebView, json: String, screenshotB64: String?) {
    try {
        val escaped = JSONObject.quote(json)
        // 直接设置 window 变量，Vue 初始化时读取
        view.evaluateJavascript("window.__GKD_SNAPSHOT__ = $escaped;", null)
        if (screenshotB64 != null) {
            val escapedShot = JSONObject.quote(screenshotB64)
            view.evaluateJavascript("window.__GKD_SCREENSHOT__ = $escapedShot;", null)
        }
    } catch (e: Exception) {
        LogUtils.d("尽早注入失败", e)
    }
}

/** 向页面注入快照数据 */
private fun injectSnapshotData(view: WebView, json: String, screenshotB64: String?) {
    try {
        val escaped = JSONObject.quote(json)
        view.evaluateJavascript("if(window.loadSnapshot){window.loadSnapshot($escaped)}", null)
        // 截图通过 getScreenshot() 接口按需获取，不在这里注入（太大）
    } catch (e: Exception) {
        LogUtils.d("注入快照失败", e)
    }
}
