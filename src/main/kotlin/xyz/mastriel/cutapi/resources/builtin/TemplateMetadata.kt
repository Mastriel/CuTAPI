package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import net.peanuuutz.tomlkt.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*


public class TemplateResource(
    ref: ResourceRef<TemplateResource>,
    metadata: Metadata,
    private val stringData: String
) : Resource(ref, metadata) {

    @Serializable
    public class Metadata : CuTMeta()

    public fun getPatchedTable(map: Map<String, TomlLiteral>): TomlTable {
        var stringTable = stringData
        for ((key, value) in map) {
            stringTable = stringTable.replace("{{${key}}}", value.toString())
        }
        return CuTAPI.toml.parseToTomlTable(stringTable)
    }
}


public val TemplateResourceLoader: ResourceFileLoader<TemplateResource> =
    resourceLoader(
        listOf("template"),
        id(Plugin, "template"),
        TemplateResource.Metadata.serializer()
    ) {
        success(TemplateResource(ref.cast(), metadata ?: TemplateResource.Metadata(), dataAsString))
    }


public typealias SerializableTemplateRef = ResourceRef<@Contextual TemplateResource>


