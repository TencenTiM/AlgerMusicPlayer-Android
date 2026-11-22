package com.algermusic.player.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.algermusic.player.data.api.QqMusicService
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.theme.QqGreen
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG_QQ_LOGIN = "QqQuickLogin"
private const val DESKTOP_CHROME_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private fun safeReleaseQqWebView(wv: WebView) {
    runCatching {
        wv.stopLoading()
        wv.onPause()
        wv.webChromeClient = null
        wv.webViewClient = WebViewClient()
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.removeAllViews()
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { wv.destroy() }
        }, 180L)
    }
}

@Composable
fun QqQuickLoginScreen(
    isExchanging: Boolean,
    onBack: () -> Unit,
    onAuthCaptured: (code: String?, combinedCookie: String) -> Unit
) {
    val context = LocalContext.current
    var loadProgress by remember { mutableIntStateOf(0) }
    var statusBanner by remember {
        mutableStateOf("请点击页内「密码登录」输入 QQ 账密，或直接扫码登录，成功后自动同步歌单")
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val handledOnce = remember { AtomicBoolean(false) }

    fun collectAllQqCookies(): String {
        val cm = CookieManager.getInstance()
        runCatching { cm.flush() }
        return QqMusicService.mergeCookieStrings(
            cm.getCookie("https://y.qq.com"),
            cm.getCookie("https://u.y.qq.com"),
            cm.getCookie("https://graph.qq.com"),
            cm.getCookie("https://ssl.ptlogin2.qq.com"),
            cm.getCookie("https://ui.ptlogin2.qq.com"),
            cm.getCookie("https://xui.ptlogin2.qq.com"),
            cm.getCookie("https://qq.com")
        )
    }

    fun currentGraphPskey(): String {
        val graphCookie = runCatching { CookieManager.getInstance().getCookie("https://graph.qq.com") }.getOrNull().orEmpty()
        return QqMusicService.extractCookieField(graphCookie, "p_skey")
    }

    var initialPskey by remember { mutableStateOf(currentGraphPskey()) }

    fun triggerCaptured(code: String?, mergedCookie: String, banner: String) {
        if (handledOnce.compareAndSet(false, true)) {
            webViewRef?.stopLoading()
            statusBanner = banner
            onAuthCaptured(code, mergedCookie)
        }
    }

    fun tryLaunchExternalQqUri(url: String): Boolean {
        val launched = runCatching {
            val intent = if (url.startsWith("intent://", ignoreCase = true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrElse { ex ->
            statusBanner = if (ex is ActivityNotFoundException) {
                "未检测到手机 QQ 客户端，请直接点击页内「密码登录」输入 QQ 账号密码"
            } else {
                "无法自动跳转手机 QQ (${ex.javaClass.simpleName})，请使用页内密码登录"
            }
            false
        }
        if (launched) {
            statusBanner = "已拉起手机 QQ，确认授权后请切回本页完成同步"
        }
        return launched
    }

    fun isYqqHost(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        return host.equals("y.qq.com", ignoreCase = true) || host.endsWith(".y.qq.com", ignoreCase = true)
    }

    fun inspectHttpUrl(url: String, isPageFinished: Boolean): Boolean {
        val code = QqMusicService.extractOAuthCodeFromUrl(url)
        val merged = collectAllQqCookies()
        val hasMusicKey = QqMusicService.extractCookieField(merged, "qm_keyst").isNotBlank() ||
            QqMusicService.extractCookieField(merged, "qqmusic_key").isNotBlank()

        if (code != null && isYqqHost(url)) {
            triggerCaptured(code, merged, "已捕获 QQ 授权码，正在换取 QQ 音乐凭证...")
            return true
        }
        if (isYqqHost(url) && hasMusicKey) {
            triggerCaptured(null, merged, "已获取 QQ 音乐登录态，正在同步个人歌单...")
            return true
        }

        val isLoginJump = url.contains("graph.qq.com/oauth2.0/login_jump", ignoreCase = true)
        val pskeyNow = currentGraphPskey()
        if (isLoginJump && pskeyNow.isNotBlank()) {
            triggerCaptured(null, merged, "QQ 账号验证通过，正在后台换取 QQ 音乐登录态...")
            return true
        }
        if (isPageFinished && pskeyNow.isNotBlank() && pskeyNow != initialPskey) {
            triggerCaptured(null, merged, "检测到新的 QQ 登录凭证，正在同步歌单...")
            return true
        }
        return false
    }

    fun manualExtractAuth() {
        val merged = collectAllQqCookies()
        val currentUrl = webViewRef?.url.orEmpty()
        val code = QqMusicService.extractOAuthCodeFromUrl(currentUrl)
        val pskey = currentGraphPskey()
        val hasMusicKey = QqMusicService.extractCookieField(merged, "qm_keyst").isNotBlank() ||
            QqMusicService.extractCookieField(merged, "qqmusic_key").isNotBlank()
        if (code != null || hasMusicKey || pskey.isNotBlank()) {
            handledOnce.set(false)
            triggerCaptured(code, merged, "正在提取 QQ 音乐登录态并同步歌单...")
        } else {
            statusBanner = "尚未检测到 QQ 登录凭证，请先在下方页面完成登录"
        }
    }

    fun loadLoginPage(wv: WebView) {
        handledOnce.set(false)
        initialPskey = currentGraphPskey()
        wv.settings.userAgentString = DESKTOP_CHROME_UA
        statusBanner = "请点击页内「密码登录」输入 QQ 账密，或直接扫码登录，成功后自动同步歌单"
        wv.loadUrl(QqMusicService.MOBILE_PTLOGIN_URL)
    }

    // 后台 500ms Cookie 轮询兜底
    LaunchedEffect(Unit) {
        while (!handledOnce.get()) {
            delay(500L)
            if (handledOnce.get()) break
            val merged = collectAllQqCookies()
            val hasMusicKey = QqMusicService.extractCookieField(merged, "qm_keyst").isNotBlank() ||
                QqMusicService.extractCookieField(merged, "qqmusic_key").isNotBlank()
            val pskeyNow = currentGraphPskey()
            val currentUrl = webViewRef?.url.orEmpty()
            when {
                hasMusicKey && isYqqHost(currentUrl) -> {
                    triggerCaptured(null, merged, "已获取 QQ 音乐登录态，正在同步歌单...")
                }
                pskeyNow.isNotBlank() && (pskeyNow != initialPskey || currentUrl.contains("login_jump", ignoreCase = true)) -> {
                    triggerCaptured(null, merged, "QQ 账号验证通过，正在后台换取 QQ 音乐登录态...")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        AlgerTopBar(
            title = "QQ 账号密码登录",
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    handledOnce.set(false)
                    webViewRef?.reload()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = {
                    handledOnce.set(false)
                    val cm = CookieManager.getInstance()
                    cm.removeAllCookies {
                        cm.flush()
                        initialPskey = ""
                        webViewRef?.let { loadLoginPage(it) }
                    }
                    statusBanner = "已清除网页缓存，可重新登录 QQ 账号"
                }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "重置")
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QqGreen.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = statusBanner,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(QqGreen.copy(alpha = 0.18f))
                    .border(0.6.dp, QqGreen.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !isExchanging) { manualExtractAuth() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "提取登录态",
                    color = QqGreen,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (isExchanging || (loadProgress in 1..99)) {
            LinearProgressIndicator(
                progress = { if (isExchanging) 0.7f else loadProgress / 100f },
                color = QqGreen,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            @SuppressLint("SetJavaScriptEnabled")
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val wv = WebView(ctx)
                    wv.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    wv.apply {
                        webViewRef = this
                        CookieManager.getInstance().apply {
                            setAcceptCookie(true)
                            setAcceptThirdPartyCookies(wv, true)
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            userAgentString = DESKTOP_CHROME_UA
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                if (url.isBlank()) return false
                                val scheme = request?.url?.scheme
                                if (!url.startsWith("http://", ignoreCase = true) &&
                                    !url.startsWith("https://", ignoreCase = true)
                                ) {
                                    // 拦截外部跳转（防止一键登录跳系统浏览器后无法回调本应用）
                                    statusBanner = "已拦截跳往外部应用，请直接点击页内「密码登录」完成授权"
                                    return true
                                }
                                return inspectHttpUrl(url, isPageFinished = false)
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                if (!url.isNullOrBlank()) {
                                    inspectHttpUrl(url, isPageFinished = false)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (!url.isNullOrBlank()) {
                                    inspectHttpUrl(url, isPageFinished = true)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    val msg = "${error?.errorCode}: ${error?.description}"
                                    Log.w(TAG_QQ_LOGIN, "Main frame load error: $msg")
                                    statusBanner = "页面加载异常 ($msg)，请点击右上角刷新"
                                }
                            }
                        }
                        loadUrl(QqMusicService.MOBILE_PTLOGIN_URL)
                    }
                },
                onRelease = { wv ->
                    if (webViewRef === wv) webViewRef = null
                    safeReleaseQqWebView(wv)
                }
            )
        }
    }
}
