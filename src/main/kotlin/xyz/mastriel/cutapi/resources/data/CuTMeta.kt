package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.asTomlTable
import net.peanuuutz.tomlkt.encodeToTomlElement
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.unknownID
import xyz.mastriel.cutapi.resources.builtin.SerializableTemplateMetadataRef
import xyz.mastriel.cutapi.utils.combine

@Serializable
open class CuTMeta {
    @SerialName("generate")
    open val generateBlock = listOf<GenerateBlock>()

    /**
     * Clone blocks will create a whole new resource,
     * using the same base resource, and overlay the base resource's
     * CuTMeta onto any metadata written in this block.
     */
    @SerialName("clone")
    open val cloneBlock = listOf<CuTMeta>()

    @SerialName("res_type")
    val resourceType: Identifier = unknownID()

    @SerialName("extends")
    val extends: List<SerializableTemplateMetadataRef> = emptyList()


    /**
     * Create a new CuTMeta, using this as a base,
     * and applying properties from [other] as needed.
     */
    fun <T : CuTMeta> apply(other: T, otherSerializer: KSerializer<T>): T {
        val thisSerial = CuTAPI.toml.encodeToTomlElement(this).asTomlTable()
        val otherSerial = CuTAPI.toml.encodeToTomlElement(otherSerializer, other).asTomlTable()

        val newTable = thisSerial.combine(otherSerial, combineArrays = true)

        return CuTAPI.toml.decodeFromTomlElement(otherSerializer, newTable)
    }
}


@Serializable
open class GenerateBlock {
    @SerialName("gen_id")
    open val generatorId = unknownID()

    @SerialName("options")
    open val options: TomlTable = TomlTable()
}


