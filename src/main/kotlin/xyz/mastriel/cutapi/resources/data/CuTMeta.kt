package xyz.mastriel.cutapi.resources.data

import kotlinx.serialization.*
import net.peanuuutz.tomlkt.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*

@Serializable
public open class CuTMeta {
    @SerialName("generate")
    public open val generateBlocks: List<GenerateBlock> = listOf()

    /**
     * Clone blocks will create a whole new resource,
     * using the same base resource, and overlay the base resource's
     * CuTMeta onto any metadata written in this block.
     */
    @SerialName("clone")
    public open val cloneBlocks: List<@Contextual TomlTable> = listOf()

    @SerialName("id")
    public open val resourceType: Identifier = unknownID()

    @SerialName("extends")
    public val extends: Map<SerializableTemplateRef, List<Map<String, @Contextual TomlLiteral>>> = emptyMap()


    /**
     * Create a new CuTMeta, using this as a base,
     * and applying properties from [other] as needed.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : CuTMeta> apply(other: T, serializer: KSerializer<T>): T {
        val thisSerial = CuTAPI.toml.encodeToTomlElement(serializer, this as T).asTomlTable()
        val otherSerial = CuTAPI.toml.encodeToTomlElement(serializer, other).asTomlTable()

        val newTable = thisSerial.combine(otherSerial, combineArrays = true)

        return CuTAPI.toml.decodeFromTomlElement(serializer, newTable)
    }


    public fun toToml(): TomlTable {
        return CuTAPI.toml.encodeToTomlElement(this).asTomlTable()
    }
}


@Serializable
public open class GenerateBlock {
    @SerialName("gen_id")
    public open val generatorId: Identifier = unknownID()

    @SerialName("sub_id")
    public open val subId: String? = null

    @SerialName("options")
    public open val options: TomlTable = TomlTable()
}


