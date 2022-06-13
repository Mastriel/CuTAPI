package xyz.mastriel.cutapi.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin


@Serializable(with = IdentifierSerializer::class)
data class Identifier internal constructor(val namespace: String, val id: String) {

    /**
     * The plugin this [Identifier] points to, or null if it cannot be found.
     * If this is null, no logic will be associated with the identifiable object
     * because a plugin cannot be found to handle its logic.
     */
    val plugin get() = try {
        CuTAPI.getPluginFromNamespace(namespace)
    } catch (ex: IllegalStateException) {
        null
    }

    override fun toString(): String {
        return "$namespace:$id"
    }
}

/**
 * An identifier, for any type of registrable object. If an identifier is not able to resolve,
 * it will automatically be `cutapi:unknown`.
 *
 * @param plugin The plugin used for the namespace
 * @param id This must follow the same naming rules as namespaces. See [CuTAPI.registerPlugin] for more info.
 *
 * @see CuTAPI.registerPlugin
 */
fun id(plugin: Plugin, id: String) : Identifier {
    CuTAPI.requireRegistered(plugin)
    CuTAPI.requireValidNamespace(id)

    val namespace = CuTAPI.getDescriptor(plugin).namespace
    return Identifier(namespace, id)
}

object IdentifierSerializer : KSerializer<Identifier> {

    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(this::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Identifier {
        val (namespace, id) = decoder.decodeString().split(":")
        return Identifier(namespace, id)
    }

    override fun serialize(encoder: Encoder, value: Identifier) {
        encoder.encodeString(value.toString())
    }


}
