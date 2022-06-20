package xyz.mastriel.cutapi.items.components

import de.tr7zw.changeme.nbtapi.NBTCompound
import xyz.mastriel.cutapi.items.CustomItemStack
import xyz.mastriel.cutapi.registry.Identifier
import kotlin.reflect.KClass

/**
 * A serializer for [ItemComponent]s. Typically, this should be put in the [ItemComponent]'s companion object,
 * although it can technically be put anywhere.
 *
 * All [ItemComponent]'s [ComponentSerializer]s should be registered with [ComponentSerializer.register],
 * otherwise they will not have an ID, which will cause errors.
 *
 * @param T The commponent this serializer is being registered for.
 * @param kclass The class reference to [T]. This cannot be inferred naturally, so it must be specified.
 * @param id The ID of this serializer, and also the ID of the component ([T]).
 * @see ItemComponent
 */
abstract class ComponentSerializer<T: ItemComponent>(private val kclass: KClass<T>, val id: Identifier) {

    /**
     * Transforms a [T] into a [NBTCompound].
     *
     * This should always return a value, and never throw an exception, as this will not be handled automatically.
     *
     * @param component The ItemComponent being serialized.
     * @return A NBTCompound that can be stored in an item.
     */
    abstract fun toCompound(component: T) : NBTCompound

    /**
     * Transforms a [NBTCompound] to a [T].
     *
     * This can throw any [Exception] if the stored data is not formatted properly. If an exception is thrown, this
     * will not be deserialized and will be ignored in [CustomItemStack.fromVanilla]/[CustomItemStack.fromVanillaOrNull].
     *
     * @param compound The NBTCompound being deserialized.
     * @return A NBTCompound that can be stored in an item.
     */
    abstract fun fromCompound(compound: NBTCompound) : T

    fun getClass() = kclass

    companion object {
        val values = mutableMapOf<KClass<out ItemComponent>, ComponentSerializer<ItemComponent>>()
        val idValues = mutableMapOf<Identifier, KClass<out ItemComponent>>()
        val kclassToId = mutableMapOf<KClass<out ItemComponent>, Identifier>()

        @Suppress("UNCHECKED_CAST")
        inline fun <reified T: ItemComponent> register(serializer: ComponentSerializer<T>) {
            values[T::class] = serializer as ComponentSerializer<ItemComponent>
            idValues[serializer.id] = T::class
            kclassToId[T::class] = serializer.id
        }

        fun <T: ItemComponent> get(kclass: KClass<T>) : ComponentSerializer<ItemComponent> {
            return getOrNull(kclass) ?: error("ComponentSerializer not found for ${kclass.qualifiedName}.")
        }

        fun <T: ItemComponent> getOrNull(kclass: KClass<T>) : ComponentSerializer<ItemComponent>? {
            return values[kclass]
        }

        fun get(id: Identifier) : ComponentSerializer<ItemComponent> {
            return getOrNull(id) ?: error("ComponentSerializer not found for $id.")
        }
        fun getOrNull(id: Identifier) : ComponentSerializer<ItemComponent>? {
            val kclass = idValues[id] ?: return null
            return getOrNull(kclass)
        }
    }
}