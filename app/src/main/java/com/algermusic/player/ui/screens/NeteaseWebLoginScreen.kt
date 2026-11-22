package com.algermusic.player.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.algermusic.player.data.api.NeteaseApiClient
import com.algermusic.player.ui.components.AlgerTopBar
import com.algermusic.player.ui.theme.EmeraldAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

private val NETEASE_CLEAR_URLS = listOf(
    "https://music.163.com",
    "https://y.music.163.com",
    "https://interface.music.163.com",
    "https://interface3.music.163.com",
    "https://163.com"
)

private val NETEASE_AUTH_COOKIE_KEYS = listOf(
    "MUSIC_U",
    "__csrf",
    "MUSIC_A",
    "NMTID",
    "NTES_SESS",
    "P_INFO",
    "S_INFO"
)

/**
 * 定向清理网易云域下鉴权 Cookie，避免误清同应用内其他平台（如 QQ 音乐）的 WebView 登录态
 */
private fun clearNeteaseWebCookies(cm: CookieManager) {
    for (url in NETEASE_CLEAR_URLS) {
        for (name in NETEASE_AUTH_COOKIE_KEYS) {
            cm.setCookie(url, "$name=; Max-Age=0; Path=/")
            cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.music.163.com")
            cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.163.com")
        }
    }
    runCatching { cm.flush() }
}

private fun safeReleaseNeteaseWebView(wv: WebView) {
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
fun NeteaseWebLoginScreen(
    isVerifying: Boolean,
    onBack: () -> Unit,
    onAuthCaptured: (combinedCookie: String, onDone: (Boolean, String) -> Unit) -> Unit
) {
    var loadProgress by remember { mutableIntStateOf(0) }
    var useDesktopMode by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var statusBanner by remember {
        mutableStateOf("支持手机号验证码与账号密码登录，登录完成后将自动同步")
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var initialWebViewUa by remember { mutableStateOf<String?>(null) }
    val currentIsVerifying by rememberUpdatedState(isVerifying)
    val handledOnce = remember { AtomicBoolean(false) }

    fun collectNeteaseCookies(): String {
        val cm = CookieManager.getInstance()
        runCatching { cm.flush() }
        val domainCookies = NeteaseApiClient.WEB_COOKIE_DOMAINS
            .map { cm.getCookie(it) }
            .toTypedArray()
        return NeteaseApiClient.mergeCookieStrings(*domainCookies)
    }

    fun tryCaptureCookies(manualTrigger: Boolean) {
        val merged = collectNeteaseCookies()
        if (NeteaseApiClient.hasValidAuthCookie(merged) && handledOnce.compareAndSet(false, true)) {
            webViewRef?.stopLoading()
            statusBanner = "已获取网易云登录凭证，正在验证账号并同步歌单..."
            onAuthCaptured(merged) { ok, msg ->
                if (!ok) {
                    handledOnce.set(false)
                    statusBanner = msg
                }
            }
        } else if (manualTrigger) {
            statusBanner = "尚未检测到登录完成，请先在下方完成验证码或密码登录"
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1200L)
            if (!handledOnce.get() && !currentIsVerifying) {
                tryCaptureCookies(manualTrigger = false)
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        AlgerTopBar(
            title = "网易云账号登录",
            onBack = onBack,
            actions = {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(EmeraldAccent.copy(alpha = 0.14f))
                        .border(0.6.dp, EmeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable {
                            useDesktopMode = !useDesktopMode
                            handledOnce.set(false)
                            val nextUrl = if (useDesktopMode) {
                                NeteaseApiClient.DESKTOP_WEB_LOGIN_URL
                            } else {
                                NeteaseApiClient.MOBILE_WEB_LOGIN_URL
                            }
                            webViewRef?.let { wv ->
                                wv.settings.userAgentString = if (useDesktopMode) {
                                    NeteaseApiClient.DESKTOP_CHROME_UA
                                } else {
                                    NeteaseApiClient.normalizeMobileChromeUa(initialWebViewUa)
                                }
                                wv.loadUrl(nextUrl)
                            }
                            statusBanner = if (useDesktopMode) {
                                "已切换至网易云电脑版页面，可点击右上角登录"
                            } else {
                                "已切换至手机版登录页，支持验证码与账号密码登录"
                            }
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (useDesktopMode) Icons.Filled.PhoneAndroid else Icons.Filled.Computer,
                        contentDescription = null,
                        tint = EmeraldAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (useDesktopMode) "手机版" else "电脑版",
                        color = EmeraldAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = {
                    handledOnce.set(false)
                    webViewRef?.reload()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
                IconButton(onClick = {
                    handledOnce.set(false)
                    clearNeteaseWebCookies(CookieManager.getInstance())
                    val resetUrl = if (useDesktopMode) {
                        NeteaseApiClient.DESKTOP_WEB_LOGIN_URL
                    } else {
                        NeteaseApiClient.MOBILE_WEB_LOGIN_URL
                    }
                    webViewRef?.loadUrl(resetUrl)
                    statusBanner = "已清除网易云网页缓存，可重新登录"
                }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "重置")
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EmeraldAccent.copy(alpha = 0.10f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    .background(EmeraldAccent.copy(alpha = 0.16f))
                    .border(0.6.dp, EmeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !isVerifying) {
                        tryCaptureCookies(manualTrigger = true)
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "提取登录态",
                    color = EmeraldAccent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (isVerifying || (loadProgress in 1..99)) {
            LinearProgressIndicator(
                progress = { if (isVerifying) 0.7f else loadProgress / 100f },
                color = EmeraldAccent,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            @SuppressLint("SetJavaScriptEnabled")
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val wv = WebView(ctx)
                    val cm = CookieManager.getInstance()
                    clearNeteaseWebCookies(cm)
                    wv.apply {
                        webViewRef = this
                        cm.setAcceptCookie(true)
                        cm.setAcceptThirdPartyCookies(wv, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            initialWebViewUa = userAgentString
                            userAgentString = NeteaseApiClient.normalizeMobileChromeUa(initialWebViewUa)
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
                                val scheme = request?.url?.scheme
                                if (NeteaseApiClient.isBlockedExternalScheme(scheme)) {
                                    statusBanner = "已拦截跳往外部 App，请直接在当前页面使用验证码或密码登录"
                                    return true
                                }
                                tryCaptureCookies(manualTrigger = false)
                                return false
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                canGoBack = view?.canGoBack() == true
                                tryCaptureCookies(manualTrigger = false)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                canGoBack = view?.canGoBack() == true
                                tryCaptureCookies(manualTrigger = false)
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean
                            ) {
                                canGoBack = view?.canGoBack() == true
                            }
                        }
                        loadUrl(NeteaseApiClient.MOBILE_WEB_LOGIN_URL)
                    }
                },
                onRelease = { wv ->
                    if (webViewRef === wv) webViewRef = null
                    safeReleaseNeteaseWebView(wv)
                }
            )
        }
    }
}
