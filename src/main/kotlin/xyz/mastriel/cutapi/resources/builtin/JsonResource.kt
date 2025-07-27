package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*

/**
 * Represents a resource loaded from a JSON file.
 *
 * @property ref The reference to this JSON resource.
 * @property data The parsed JSON object data.
 */
public open class JsonResource(
    override val ref: ResourceRef<JsonResource>,
    public val data: JsonObject
) : Resource(ref), ByteArraySerializable {

    override fun toBytes(): ByteArray {
        val str = CuTAPI.json.encodeToString(data)
        return str.toByteArray(Charsets.UTF_8)
    }

    public operator fun component1(): JsonObject = data
}

/**
 * Loader for JsonResource, parses JSON files into JsonResource objects.
 */
public val JsonResourceLoader: ResourceFileLoader<JsonResource> = resourceLoader<JsonResource, Nothing>(
    extensions = listOf("json"),
    resourceTypeId = id(Plugin, "json"),
    metadataSerializer = null
) {
    val string = this.data.toString(Charsets.UTF_8)
    try {
        val data = CuTAPI.json.parseToJsonElement(string).jsonObject
        success(JsonResource(ref, data))
    } catch (e: Exception) {
        resError(ref, "Failed to load JSON file. ${e.message}")
    }
}