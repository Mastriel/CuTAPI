package xyz.mastriel.cutapi.utils

import kotlinx.serialization.json.*

/** Recursively combines two JSON objects, preferring values from [other]. */
public fun JsonObject.combine(
    other: JsonObject,
    combineArrays: Boolean = true
): JsonObject {
    val combined = toMutableMap()
    for ((key, value) in other) {
        val current = combined[key]
        combined[key] = when {
            current is JsonObject && value is JsonObject -> current.combine(value, combineArrays)
            combineArrays && current is JsonArray && value is JsonArray -> JsonArray(current + value)
            else -> value
        }
    }
    return JsonObject(combined)
}
