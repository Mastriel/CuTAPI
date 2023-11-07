package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.*

open class JsonResource(
    override val ref: ResourceRef<JsonResource>,
    val data: JsonObject
) : Resource(ref), ByteArraySerializable {

    override fun toBytes(): ByteArray {
        val str = CuTAPI.json.encodeToString(data)
        return str.toByteArray(Charsets.UTF_8)
    }

}

val JsonResourceLoader = resourceLoader<JsonResource, Nothing>(
    extensions = listOf("json"),
    resourceTypeId = id(Plugin, "json"),
    metadataSerializer = null
) {
    val string = this.data.toString(Charsets.UTF_8)
    try {
        val data = CuTAPI.json.parseToJsonElement(string).jsonObject
        JsonResource(ref, data)
    } catch (e: Exception) {
        resError(ref, "Failed to load JSON file. ${e.message}")
    }
}