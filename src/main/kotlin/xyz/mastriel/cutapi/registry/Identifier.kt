package xyz.mastriel.cutapi.registry

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import org.bukkit.*
import xyz.mastriel.cutapi.*


@Serializable(with = IdentifierSerializer::class)
@ConsistentCopyVisibility
public data class Identifier internal constructor(val namespace: String, val key: String) : Identifiable {

    override val id: Identifier get() = this

    /**
     * The plugin this [Identifier] points to, or null if it cannot be found.
     * If this is null, no logic will be associated with the identifiable object
     * because a plugin cannot be found to handle its logic.
     */
    val plugin: CuTPlugin?
        get() = try {
            CuTAPI.getPluginFromNamespace(namespace)
        } catch (ex: IllegalStateException) {
            null
        }

    override fun toString(): String {
        return "$namespace:$key"
    }

    public fun append(string: String): Identifier {
        return Identifier(namespace, key + string)
    }

    public fun appendSubId(string: String): Identifier {
        return Identifier(namespace, "$key/$string")
    }

    public operator fun div(string: String): Identifier {
        return Identifier(namespace, "$key/$string")
    }

    public fun toNamespacedKey(): NamespacedKey {
        return NamespacedKey(namespace, key)
    }

    public fun isUnknown(): Boolean {
        return this == unknownID()
    }
}

public fun NamespacedKey.toIdentifier(): Identifier = id("$namespace:$key")

/**
 * An identifier, for any type of registerable object.
 *
 * @param plugin The plugin used for the namespace
 * @param id This must follow the same naming rules as namespaces. See [CuTAPI.registerPlugin] for more info.
 *
 * @see CuTAPI.registerPlugin
 */
public fun id(plugin: CuTPlugin, id: String): Identifier {
    CuTAPI.requireRegistered(plugin)
    CuTAPI.requireValidNamespace(id)

    val namespace = CuTAPI.getDescriptor(plugin).namespace
    return Identifier(namespace, id)
}

/**
 * An identifier, for any type of registerable object.
 *
 * @param stringRepresentation The string representation of the [Identifier] you are trying to get.
 *
 * @see CuTAPI.registerPlugin
 */
public fun id(stringRepresentation: String): Identifier {
    require(":" in stringRepresentation) { "String identifier $stringRepresentation does not follow namespace:id format." }
    val (namespace, id) = stringRepresentation.split(":", limit = 2)
    return Identifier(namespace, id)
}

/**
 * An identifier, for any type of registerable object. You should not use this manually, and instead
 * use the other overload. This should only be used to get an [Identifier] from already stored data.
 *
 * @param stringRepresentation The string representation of the [Identifier] you are trying to get.
 *
 * @see CuTAPI.registerPlugin
 */
public fun idOrNull(stringRepresentation: String): Identifier? {
    val list = stringRepresentation.split(":")
    if (list.getOrNull(0) == null || list.getOrNull(1) == null || list.isEmpty()) return null
    return Identifier(list[0], list[1])
}

public fun unknownID(): Identifier = id(Plugin, "unknown")

public object IdentifierSerializer : KSerializer<Identifier> {

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
