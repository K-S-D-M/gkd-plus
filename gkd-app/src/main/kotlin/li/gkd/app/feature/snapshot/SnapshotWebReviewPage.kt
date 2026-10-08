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
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.util.LogUtils
import li.gkd.app.util.ToastUtils
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import li.gkd.app.util.AiRuleGenerator
import li.gkd.app.util.appScope
import li.gkd.app.util.launchLogged

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
    var webView by remember { mutableStateOf<WebView?>(null) }
    var snapshotJson by remember { mutableStateOf<String?>(null) }
    var screenshotBase64 by remember { mutableStateOf<String?>(null) }
    var inspectReady by remember { mutableStateOf(false) }
    var loadingInspect by remember { mutableStateOf(true) }
    var useOfficial by remember { mutableStateOf(false) }

    // 1. 准备官方 inspect 工具（下载 ZIP 并解压）
    LaunchedEffect(Unit) {
        try {
            val inspectDir = withContext(Dispatchers.IO) {
                prepareInspectTool()
            }
            val indexFile = File(inspectDir, "index.html")
            useOfficial = indexFile.exists()
        } catch (e: Exception) {
            LogUtils.d(e)
            useOfficial = false
        } finally {
            inspectReady = true
            loadingInspect = false
        }
    }

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
            snapshotJson = json
            screenshotBase64 = b64
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val jsApi = remember {
        object {
            @JavascriptInterface
            fun getSnapshotJson(): String = snapshotJson ?: ""

            @JavascriptInterface
            fun getScreenshot(): String = screenshotBase64 ?: ""

            @JavascriptInterface
            fun copyText(text: String) {
                ToastUtils.copyText(text)
            }

            @JavascriptInterface
            fun toast(text: String) {
                ToastUtils.toast(text)
            }

            @JavascriptInterface
            fun aiGenerateRule(nodeInfoJson: String) {
                // 从 WebView 线程切换到主线程处理
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    appScope.launchLogged {
                        try {
                            ToastUtils.toast("AI 正在为选中节点生成规则...", forced = true)
                            AiRuleGenerator.generateRuleForNode(route.snapshotId, nodeInfoJson)
                        } catch (e: Exception) {
                            ToastUtils.toast("AI 生成失败：${e.message}")
                        }
                    }
                }
            }
        }
    }

    // 注入快照数据到 window.__GKD_SNAPSHOT__ / __GKD_SCREENSHOT__
    fun injectSnapshot(view: WebView) {
        val json = snapshotJson ?: return
        val escapedJson = JSONObject.quote(json)
        val escapedShot = screenshotBase64?.let { JSONObject.quote(it) } ?: "null"
        view.evaluateJavascript(
            """
            (function() {
                try {
                    window.__GKD_SNAPSHOT__ = JSON.parse($escapedJson);
                    ${if (screenshotBase64 != null) "window.__GKD_SCREENSHOT__ = $escapedShot;" else ""}
                    console.log('GKD snapshot injected');
                } catch(e) { console.error('GKD snapshot inject failed', e); }
            })();
            """.trimIndent(),
            null
        )
    }

    val inspectDir = remember(useOfficial) {
        if (useOfficial) File(app.filesDir, "inspect-tool") else null
    }
    val startUrl = if (useOfficial && inspectDir != null) {
        "file://${File(inspectDir, "index.html").absolutePath}"
    } else {
        "file:///android_asset/snapshot-review/index.html"
    }

    val webViewState = rememberWebViewState(url = startUrl)

    val webViewClient = remember {
        object : AccompanistWebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (useOfficial) {
                    injectSnapshot(view)
                } else {
                    snapshotJson?.let { json ->
                        val escaped = JSONObject.quote(json)
                        view.evaluateJavascript("if(window.loadSnapshot){window.loadSnapshot($escaped)}", null)
                    }
                }
            }
        }
    }

    // 数据到达后重新注入（官方版）
    LaunchedEffect(snapshotJson, screenshotBase64, webView, useOfficial, inspectReady) {
        val wv = webView ?: return@LaunchedEffect
        if (snapshotJson == null) return@LaunchedEffect
        if (!inspectReady) return@LaunchedEffect
        if (webViewState.loadingState is LoadingState.Finished) {
            if (useOfficial) {
                injectSnapshot(wv)
            } else {
                val escaped = JSONObject.quote(snapshotJson!!)
                wv.evaluateJavascript("if(window.loadSnapshot){window.loadSnapshot($escaped)}", null)
            }
        }
    }

    // URL 变化时重新加载（从降级切换到官方版时）
    LaunchedEffect(startUrl, webView) {
        val wv = webView ?: return@LaunchedEffect
        if (webViewState.loadingState is LoadingState.Finished) {
            // 已经加载完成，不需要重复加载
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
            if (loadingInspect) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                WebView(
                    modifier = Modifier.fillMaxSize(),
                    state = webViewState,
                    client = webViewClient,
                    onCreated = {
                        webView = it
                        it.addJavascriptInterface(jsApi, "GkdBridge")
                        it.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            allowFileAccessFromFileURLs = true
                            allowUniversalAccessFromFileURLs = true
                            mediaPlaybackRequiresUserGesture = false
                        }
                    },
                )
            }
        }
    }
}

/**
 * 准备官方 inspect 工具：从 GitHub Pages 下载 ZIP 并解压到内部存储。
 * 已解压则直接返回目录。
 */
private fun prepareInspectTool(): File {
    val dir = File(app.filesDir, "inspect-tool")
    val marker = File(dir, ".ready")
    if (marker.exists() && File(dir, "index.html").exists()) {
        return dir
    }
    val zipFile = File(app.cacheDir, "inspect-dist.zip")
    URL(INSPECT_ZIP_URL).openStream().use { input ->
        zipFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    dir.deleteRecursively()
    dir.mkdirs()
    ZipInputStream(zipFile.inputStream()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val outFile = File(dir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { zis.copyTo(it) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    zipFile.delete()
    marker.createNewFile()
    return dir
}
