package com.algermusic.player.data.nas

import okhttp3.Call
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * 按 NAS 主机端口（host:port）精准作用域注入鉴权头（飞牛音乐 Cookie: music-token / WebDAV Basic），
 * 并仅为显式勾选「允许局域网自签名证书」的私有 NAS 主机启用自签证书信任，绝不影响公网安全策略。
 */
object NasAuthRegistry {
    private val hostHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private val selfSignedHosts = ConcurrentHashMap.newKeySet<String>()

    private fun keyOf(host: String, port: Int): String = "${host.trim().lowercase()}:$port"

    fun registerBasicAuth(
        host: String,
        port: Int,
        username: String,
        password: String,
        allowSelfSignedSsl: Boolean
    ) {
        if (host.isBlank()) return
        val k = keyOf(host, port)
        if (username.isNotBlank() || password.isNotBlank()) {
            val basic = Credentials.basic(username, password, Charsets.UTF_8)
            hostHeaders[k] = mapOf("Authorization" to basic)
        }
        if (allowSelfSignedSsl) selfSignedHosts.add(k) else selfSignedHosts.remove(k)
    }

    fun registerFnosMusicAuth(
        host: String,
        port: Int,
        musicToken: String,
        accessCode: String = "",
        allowSelfSignedSsl: Boolean = true
    ) {
        if (host.isBlank()) return
        val k = keyOf(host, port)
        val isRelay = host.contains("5ddd.com", ignoreCase = true) || host.contains("fnos.net", ignoreCase = true)
        val map = LinkedHashMap<String, String>()
        if (musicToken.isNotBlank()) {
            map["Cookie"] = if (isRelay) "music-token=$musicToken; mode=relay" else "music-token=$musicToken"
        } else if (isRelay) {
            map["Cookie"] = "mode=relay"
        }
        if (accessCode.isNotBlank()) {
            val encoded = Base64.getEncoder().encodeToString(accessCode.toByteArray(StandardCharsets.UTF_8))
            map["x-access-code"] = encoded
            map["x-access-source"] = "app"
        }
        if (map.isNotEmpty()) {
            hostHeaders[k] = map
        }
        if (allowSelfSignedSsl) selfSignedHosts.add(k) else selfSignedHosts.remove(k)
    }

    fun registerSelfSignedHost(host: String, port: Int, allowSelfSignedSsl: Boolean) {
        if (host.isBlank()) return
        val k = keyOf(host, port)
        if (allowSelfSignedSsl) selfSignedHosts.add(k) else selfSignedHosts.remove(k)
    }

    fun isSelfSignedAllowed(url: HttpUrl): Boolean =
        selfSignedHosts.contains(keyOf(url.host, url.port))

    fun headersFor(url: HttpUrl): Map<String, String>? =
        hostHeaders[keyOf(url.host, url.port)]

    fun isRegisteredNasUri(uri: android.net.Uri): Boolean {
        val h = uri.host?.trim()?.lowercase() ?: return false
        val p = if (uri.port > 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
        val k = "$h:$p"
        return hostHeaders.containsKey(k) || selfSignedHosts.contains(k)
    }

    fun applyAuthHeaders(builder: Request.Builder, orig: Request) {
        val headers = headersFor(orig.url) ?: return
        for ((name, value) in headers) {
            if (orig.header(name) == null) {
                builder.header(name, value)
            }
        }
    }

    fun configureSelfSignedSsl(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
    }

    fun createRoutingCallFactory(
        normalClient: OkHttpClient,
        selfSignedClient: OkHttpClient
    ): Call.Factory = Call.Factory { request ->
        if (request.url.isHttps && isSelfSignedAllowed(request.url)) {
            selfSignedClient.newCall(request)
        } else {
            normalClient.newCall(request)
        }
    }
}
