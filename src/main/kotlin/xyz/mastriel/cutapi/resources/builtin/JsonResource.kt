package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

/**
 * Represents a resource loaded from a JSON file.
 *
 * @property ref The reference to this JSON resource.
 * @property data The parsed JSON object data.
 */
public open class JsonResource(
    override val ref: ResourceRef<JsonResource>,
    public val data: JsonObject,
    override val metadata: Metadata = Metadata()
) : Resource(ref, metadata), ByteArraySerializable {
    public class Metadata : CuTMeta() {
        public companion object : Schema<Metadata> by schema(id("cutapi:json"), {
            extends { CuTMeta }
        })
    }

    override fun toBytes(): ByteArray {
        val str = CuTAPI.json.encodeToString(data)
        return str.toByteArray(Charsets.UTF_8)
    }

    public operator fun component1(): JsonObject = data
}

/**
 * Loader for JsonResource, parses JSON files into JsonResource objects.
 */
public val JsonResourceLoader: ResourceFileLoader<JsonResource> = resourceLoader<JsonResource, JsonResource.Metadata>(
    extensions = listOf("json"),
    metadataSchema = JsonResource.Metadata
) {
    val string = this.data.toString(Charsets.UTF_8)
    try {
        val data = CuTAPI.json.parseToJsonElement(string).jsonObject
        success(JsonResource(ref, data, metadata ?: JsonResource.Metadata()))
    } catch (e: Exception) {
        resError(ref, "Failed to load JSON file. ${e.message}")
    }
}
