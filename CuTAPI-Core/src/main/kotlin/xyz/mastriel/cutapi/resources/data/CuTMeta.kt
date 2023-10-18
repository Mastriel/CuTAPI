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
import xyz.mastriel.cutapi.resources.data.minecraft.Animation

@Serializable
open class CuTMeta : Identifiable {
    @SerialName("generate")
    open val generateOptions = listOf<TomlTable>()

    @SerialName("id")
    override val id : Identifier = unknownID()
}