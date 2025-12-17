package com.claygillman.gtnh.quest

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Parser for BetterQuesting's type-annotated JSON format.
 *
 * Keys have type suffixes: "name:8" means field "name" with type String (8).
 * Type codes:
 *   :1 = Boolean (stored as 0/1)
 *   :2 = Short
 *   :3 = Int
 *   :4 = Long
 *   :5 = Float
 *   :6 = Double
 *   :8 = String
 *   :9 = List/Map (JSON object with numeric keys)
 *   :10 = Compound (nested object)
 */
sealed class BQValue {
    data class BQBoolean(val value: Boolean) : BQValue()
    data class BQInt(val value: Int) : BQValue()
    data class BQLong(val value: Long) : BQValue()
    data class BQFloat(val value: Float) : BQValue()
    data class BQDouble(val value: Double) : BQValue()
    data class BQString(val value: String) : BQValue()
    data class BQList(val values: List<BQValue>) : BQValue()
    data class BQCompound(val values: Map<String, BQValue>) : BQValue()
    data object BQNull : BQValue()
}

object BetterQuestingJson {
    private val TYPE_PATTERN = Regex("^(.+):(\\d+)$")

    fun parseFile(file: File): BQValue.BQCompound {
        val json = file.readText()
        return parse(json)
    }

    fun parse(json: String): BQValue.BQCompound {
        val element = JsonParser.parseString(json)
        return parseCompound(element.asJsonObject)
    }

    private fun parseCompound(jsonObject: JsonObject): BQValue.BQCompound {
        val result = mutableMapOf<String, BQValue>()
        for ((key, value) in jsonObject.entrySet()) {
            val match = TYPE_PATTERN.matchEntire(key)
            if (match != null) {
                val fieldName = match.groupValues[1]
                val typeCode = match.groupValues[2].toInt()
                result[fieldName] = parseValue(value, typeCode)
            } else {
                // Key without type suffix - try to infer from value
                result[key] = inferValue(value)
            }
        }
        return BQValue.BQCompound(result)
    }

    private fun parseValue(element: JsonElement, typeCode: Int): BQValue {
        if (element.isJsonNull) return BQValue.BQNull

        return when (typeCode) {
            1 -> BQValue.BQBoolean(element.asInt != 0)
            2 -> BQValue.BQInt(element.asInt) // Short stored as Int
            3 -> BQValue.BQInt(element.asInt)
            4 -> BQValue.BQLong(element.asLong)
            5 -> BQValue.BQFloat(element.asFloat)
            6 -> BQValue.BQDouble(element.asDouble)
            8 -> BQValue.BQString(element.asString)
            9 -> parseList(element.asJsonObject) // Lists are objects with numeric keys
            10 -> parseCompound(element.asJsonObject)
            11 -> parseIntArray(element) // Int array
            else -> BQValue.BQString(element.toString())
        }
    }

    private fun parseList(jsonObject: JsonObject): BQValue.BQList {
        // BetterQuesting lists are objects with numeric string keys: "0:10", "1:10", etc.
        val items = mutableListOf<Pair<Int, BQValue>>()
        for ((key, value) in jsonObject.entrySet()) {
            val match = TYPE_PATTERN.matchEntire(key)
            if (match != null) {
                val index = match.groupValues[1].toIntOrNull() ?: continue
                val typeCode = match.groupValues[2].toInt()
                items.add(index to parseValue(value, typeCode))
            }
        }
        // Sort by index and return values
        return BQValue.BQList(items.sortedBy { it.first }.map { it.second })
    }

    private fun parseIntArray(element: JsonElement): BQValue.BQList {
        return if (element.isJsonArray) {
            BQValue.BQList(element.asJsonArray.map { BQValue.BQInt(it.asInt) })
        } else {
            BQValue.BQList(emptyList())
        }
    }

    private fun inferValue(element: JsonElement): BQValue {
        return when {
            element.isJsonNull -> BQValue.BQNull
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isBoolean -> BQValue.BQBoolean(prim.asBoolean)
                    prim.isNumber -> {
                        val num = prim.asNumber
                        if (num.toDouble() == num.toLong().toDouble()) {
                            BQValue.BQLong(num.toLong())
                        } else {
                            BQValue.BQDouble(num.toDouble())
                        }
                    }
                    prim.isString -> BQValue.BQString(prim.asString)
                    else -> BQValue.BQString(prim.toString())
                }
            }
            element.isJsonObject -> parseCompound(element.asJsonObject)
            element.isJsonArray -> BQValue.BQList(element.asJsonArray.map { inferValue(it) })
            else -> BQValue.BQString(element.toString())
        }
    }
}

// Extension functions for convenient typed access
fun BQValue.BQCompound.getString(key: String): String? = (values[key] as? BQValue.BQString)?.value
fun BQValue.BQCompound.getInt(key: String): Int? = when (val v = values[key]) {
    is BQValue.BQInt -> v.value
    is BQValue.BQLong -> v.value.toInt()
    is BQValue.BQBoolean -> if (v.value) 1 else 0
    else -> null
}
fun BQValue.BQCompound.getLong(key: String): Long? = when (val v = values[key]) {
    is BQValue.BQLong -> v.value
    is BQValue.BQInt -> v.value.toLong()
    else -> null
}
fun BQValue.BQCompound.getBoolean(key: String): Boolean? = when (val v = values[key]) {
    is BQValue.BQBoolean -> v.value
    is BQValue.BQInt -> v.value != 0
    else -> null
}
fun BQValue.BQCompound.getCompound(key: String): BQValue.BQCompound? = values[key] as? BQValue.BQCompound
fun BQValue.BQCompound.getList(key: String): BQValue.BQList? = values[key] as? BQValue.BQList
