package com.algermusic.player.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object JsonHelper {
    val tolerantJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun parseObjectOrNull(text: String?): JsonObject? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val element = tolerantJson.parseToJsonElement(text)
            element as? JsonObject
        }.getOrNull()
    }

    fun parseElementOrNull(text: String?): JsonElement? {
        if (text.isNullOrBlank()) return null
        return runCatching { tolerantJson.parseToJsonElement(text) }.getOrNull()
    }
}

fun JsonElement?.asObjOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.asArrOrNull(): JsonArray? = this as? JsonArray

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

fun JsonObject.str(key: String, fallback: String = ""): String {
    val prim = this[key] as? JsonPrimitive ?: return fallback
    return prim.contentOrNull ?: fallback
}

fun JsonObject.str(key1: String, key2: String, fallback: String = ""): String {
    val v1 = str(key1, "")
    return if (v1.isNotEmpty()) v1 else str(key2, fallback)
}

fun JsonObject.long(key: String, fallback: Long = 0L): Long {
    val prim = this[key] as? JsonPrimitive ?: return fallback
    return prim.longOrNull ?: prim.doubleOrNull?.toLong() ?: prim.contentOrNull?.toLongOrNull() ?: fallback
}

fun JsonObject.long(key1: String, key2: String, fallback: Long = 0L): Long {
    val prim1 = this[key1] as? JsonPrimitive
    val v1 = prim1?.longOrNull ?: prim1?.doubleOrNull?.toLong() ?: prim1?.contentOrNull?.toLongOrNull()
    return v1 ?: long(key2, fallback)
}

fun JsonObject.int(key: String, fallback: Int = 0): Int {
    val prim = this[key] as? JsonPrimitive ?: return fallback
    return prim.intOrNull ?: prim.doubleOrNull?.toInt() ?: prim.contentOrNull?.toIntOrNull() ?: fallback
}

fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean {
    val prim = this[key] as? JsonPrimitive ?: return fallback
    return prim.booleanOrNull ?: prim.contentOrNull?.toBooleanStrictOrNull() ?: fallback
}

fun JsonElement.primitiveStrOrEmpty(): String {
    return (this as? JsonPrimitive)?.contentOrNull.orEmpty()
}
