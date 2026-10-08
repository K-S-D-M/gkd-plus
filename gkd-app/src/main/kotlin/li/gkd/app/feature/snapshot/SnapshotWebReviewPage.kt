package li.gkd.app.feature.snapshot

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.app.data.snapshot.SnapshotRepository
import li.gkd.app.util.ToastUtils
import org.json.JSONObject
import java.io.File

@Serializable
data class SnapshotWebReviewRoute(
    val snapshotId: Long,
) : NavKey

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
fun SnapshotWebReviewPage(route: SnapshotWebReviewRoute) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var snapshotJson by remember { mutableStateOf<String?>(null) }
    var screenshotBase64 by remember { mutableStateOf<String?>(null) }

    // Load snapshot data
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
        }
    }

    val webViewState = rememberWebViewState("file:///android_asset/snapshot-review/index.html")
    val webViewClient = remember {
        object : AccompanistWebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                snapshotJson?.let { json ->
                    val escaped = JSONObject.quote(json)
                    view.evaluateJavascript("if(window.loadSnapshot){window.loadSnapshot($escaped)}", null)
                }
            }
        }
    }

    // Re-inject when data arrives after page load
    LaunchedEffect(snapshotJson, webView) {
        val wv = webView ?: return@LaunchedEffect
        val json = snapshotJson ?: return@LaunchedEffect
        if (webViewState.loadingState is LoadingState.Finished) {
            val escaped = JSONObject.quote(json)
            wv.evaluateJavascript("if(window.loadSnapshot){window.loadSnapshot($escaped)}", null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("网页审核 Web Review") })
        },
    ) { padding ->
        WebView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = webViewState,
            client = webViewClient,
            onCreated = {
                webView = it
                it.addJavascriptInterface(jsApi, "GkdBridge")
                it.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    mediaPlaybackRequiresUserGesture = false
                }
            },
        )
    }
}
