package xyz.mastriel.cutapi.registry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


/**
 * An interface that marks a class as identifiable using an [Identifier].
 */
interface Identifiable {
    val id: Identifier
}


abstract class IdentifiableSerializer<T: Identifiable>(val serialName: String, val map: IdentifierMap<T>) : KSerializer<T> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): T {
        val identifier = IdentifierSerializer.deserialize(decoder)
        return map.get(identifier)
    }

    override fun serialize(encoder: Encoder, value: T) {
        val identifier = value.id.toString()
        encoder.encodeString(identifier)
    }
}

/**
 * A map of [Identifiable] to [T]. This is used in keeping a registry of all items, blocks, etc.
 *
 * @param T The identifiable that is being tracked.
 */
open class IdentifierMap<T: Identifiable> {
    private val values = mutableMapOf<Identifier, T>()

    /**
     * Register an object with this map to allow for it to be identified.
     *
     * @param item The object which the association is being made for.
     */
    open fun register(item: T) {
        values[item.id] = item
    }

    /**
     * Get a [T] based on its corresponding [Identifier].
     *
     * @param id The [Identifier] associated with this [T]
     * @returns The object
     * @throws IllegalStateException If this could not be found.
     */
    fun get(id: Identifier) : T {
        return getOrNull(id) ?: error("Identifier points to no available identifiable object.")
    }

    /**
     * Get a [T] based on its corresponding [Identifier], or null.
     *
     * @param id The [Identifier] associated with this [T]
     * @returns The object, or null if it could not be found.
     */
    fun getOrNull(id: Identifier) : T? {
        return values[id]
    }
}