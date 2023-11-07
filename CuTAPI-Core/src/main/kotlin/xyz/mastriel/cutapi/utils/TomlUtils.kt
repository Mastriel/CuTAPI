package xyz.mastriel.cutapi.utils

import kotlinx.serialization.json.*
import net.peanuuutz.tomlkt.*


/**
 * Converts a TomlTable to a Json object. Used for adding things to resource packs.
 */
fun TomlTable.toJson() : JsonObject {
    return portTable(this)
}


private fun portTable(table: TomlTable) : JsonObject {
    val tomlMap = table.content.toMutableMap()
    val jsonMap = mutableMapOf<String, JsonElement>()
    for ((key, value) in tomlMap) {
        jsonMap[key] = portElement(value)
    }
    return JsonObject(jsonMap)
}

private fun portElement(element: TomlElement) : JsonElement {
    return when (element) {
        is TomlTable -> {
            portTable(element)
        }

        is TomlArray -> {
            JsonArray(buildList {
                for (value in element) {
                    add(portElement(value))
                }
            })
        }

        is TomlLiteral -> {
            when (element.type) {
                TomlLiteral.Type.Boolean -> JsonPrimitive(element.toBoolean())
                TomlLiteral.Type.Integer -> JsonPrimitive(element.toInt())
                TomlLiteral.Type.Float -> JsonPrimitive(element.toFloat())
                TomlLiteral.Type.String -> JsonPrimitive(element.toString())

                // time is just made into strings
                TomlLiteral.Type.LocalDateTime -> JsonPrimitive(element.toLocalDateTime().toString())
                TomlLiteral.Type.OffsetDateTime -> JsonPrimitive(element.toOffsetDateTime().toString())
                TomlLiteral.Type.LocalDate -> JsonPrimitive(element.toLocalDate().toString())
                TomlLiteral.Type.LocalTime -> JsonPrimitive(element.toLocalTime().toString())
            }
        }

        TomlNull -> JsonNull
    }
}

/**
 * Recursively combine 2 TomlTables together. [other] will overwrite literals and arrays of this.
 */
fun TomlTable.combine(other: TomlTable, combineArrays: Boolean = true) : TomlTable {
    val map = this.content.toMutableMap()
    for ((key) in map) {
        if (map[key] is TomlTable && other[key] is TomlTable) {
            map[key] = this[key]!!.asTomlTable().combine(other[key]!!.asTomlTable())
            continue
        }
        if (map[key] is TomlArray && other[key] is TomlArray) {
            val newArray = mutableListOf<TomlElement>()
            val (currentArray, otherArray) = map[key]!!.asTomlArray() to other[key]!!.asTomlArray()
            if (combineArrays) newArray.add(currentArray)
            newArray.add(otherArray)
            continue
        }
        if (other[key] != null) {
            map[key] = other[key]!!
        } else {
            map[key] = this[key]!!
        }
    }
    return TomlTable(map)
}