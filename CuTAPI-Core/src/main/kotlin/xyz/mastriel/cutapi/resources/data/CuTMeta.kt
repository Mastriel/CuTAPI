package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlTable
import xyz.mastriel.cutapi.pdc.tags.TagContainer
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.resources.ResourceGenerator
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.minecraft.Animation

@Serializable
open class CuTMeta {
    @SerialName("generate")
    open val generateOptions = listOf<GenerateOptions>()

    @SerialName("res_type")
    val resourceType : Identifier = unknownID()

    @SerialName("\$extends")
    val extends : List<ResourceRef<*>> = emptyList()
}

@Serializable
open class GenerateOptions {
    @SerialName("gen_id")
    open val generatorId = unknownID()

    @SerialName("res_subid")
    open val resourceSubId : String? = null


}

@Serializable
open class TexturePostprocessGenerateOptions : GenerateOptions() {
    @SerialName("options")
    open val generateOptions : TomlTable = TomlTable()

    open val postProcessId : Identifier = unknownID()
}