package com.algermusic.player.data.api

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 1:1 复刻 WinUI NeteaseCrypto.cs 的网易云音乐 WeAPI 与 EAPI 加密协议
 */
object NeteaseCrypto {
    private val IV = "0102030405060708".toByteArray(StandardCharsets.UTF_8)
    private val PRESET_KEY = "0CoJUm6Qyw8W8jud".toByteArray(StandardCharsets.UTF_8)
    private val EAPI_KEY = "e82ckenh8dichen8".toByteArray(StandardCharsets.UTF_8)
    private const val BASE62_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private const val RSA_MODULUS_HEX =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725" +
            "152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312" +
            "ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424" +
            "d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"

    private val RSA_MODULUS = BigInteger(RSA_MODULUS_HEX, 16)
    private val RSA_EXPONENT = BigInteger.valueOf(0x010001L)
    private val secureRandom = SecureRandom()

    fun encryptWeApi(jsonPayload: String): Map<String, String> {
        val secretKeyBytes = ByteArray(16) {
            BASE62_CHARS[secureRandom.nextInt(BASE62_CHARS.length)].code.toByte()
        }
        return encryptWeApiWithKey(jsonPayload, secretKeyBytes)
    }

    internal fun encryptWeApiWithKey(jsonPayload: String, secretKeyBytes: ByteArray): Map<String, String> {
        val firstEncrypted = aesEncryptCbc(jsonPayload.toByteArray(StandardCharsets.UTF_8), PRESET_KEY, IV)
        val firstBase64 = Base64.getEncoder().encodeToString(firstEncrypted)

        val secondEncrypted = aesEncryptCbc(firstBase64.toByteArray(StandardCharsets.UTF_8), secretKeyBytes, IV)
        val paramsVal = Base64.getEncoder().encodeToString(secondEncrypted)

        val reversedKey = secretKeyBytes.reversedArray()
        val encSecKey = rsaEncryptNoPadding(reversedKey)

        return mapOf(
            "params" to paramsVal,
            "encSecKey" to encSecKey
        )
    }

    fun encryptEApi(apiUrl: String, jsonPayload: String): Map<String, String> {
        val signMessage = "nobody${apiUrl}use${jsonPayload}md5forencrypt"
        val md5Bytes = md5(signMessage.toByteArray(StandardCharsets.UTF_8))
        val digest = toHexLower(md5Bytes)

        val dataText = "$apiUrl-36cd479b6b5-$jsonPayload-36cd479b6b5-$digest"
        val encrypted = aesEncryptEcb(dataText.toByteArray(StandardCharsets.UTF_8), EAPI_KEY)

        return mapOf(
            "params" to toHexUpper(encrypted)
        )
    }

    fun md5HexLower(text: String): String = toHexLower(md5(text.toByteArray(StandardCharsets.UTF_8)))

    fun md5(bytes: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(bytes)

    private fun aesEncryptCbc(plainBytes: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plainBytes)
    }

    private fun aesEncryptEcb(plainBytes: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(plainBytes)
    }

    private fun rsaEncryptNoPadding(reversedSecretKey: ByteArray): String {
        val inputInt = BigInteger(1, reversedSecretKey)
        val encryptedInt = inputInt.modPow(RSA_EXPONENT, RSA_MODULUS)
        return encryptedInt.toString(16).lowercase().padStart(256, '0')
    }

    fun toHexLower(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_LOWER[v ushr 4])
            sb.append(HEX_LOWER[v and 0x0F])
        }
        return sb.toString()
    }

    fun toHexUpper(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_UPPER[v ushr 4])
            sb.append(HEX_UPPER[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX_LOWER = "0123456789abcdef".toCharArray()
    private val HEX_UPPER = "0123456789ABCDEF".toCharArray()
}
