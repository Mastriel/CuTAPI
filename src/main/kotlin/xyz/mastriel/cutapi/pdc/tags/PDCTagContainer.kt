package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*

public open class PDCTagContainer(public var container: PersistentDataContainer) : TagContainer {

    override fun <P : Any, C : Any> set(id: Identifier, complexValue: C?, converter: TagConverter<P, C>) {
        if (complexValue == null) return container.remove(id.toNamespacedKey())

        val primitiveValue = converter.toPrimitive(complexValue)

        container.setPrimitiveValue(converter.primitiveClass, id, primitiveValue)
    }

    override fun <P : Any, C : Any> get(id: Identifier, converter: TagConverter<P, C>): C? {
        if (!container.has(id.toNamespacedKey())) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, id)
        return converter.fromPrimitive(value!!)
    }

    override fun has(id: Identifier): Boolean {
        return container.has(id.toNamespacedKey())
    }


    override fun isNull(id: Identifier): Boolean {
        return checkNull(container, id.toNamespacedKey())
    }


    public companion object {
        public fun checkNull(container: PersistentDataContainer, namespacedKey: NamespacedKey): Boolean {
            if (container.has(namespacedKey)) {
                try {
                    return container.get(namespacedKey, PersistentDataType.STRING) == Tag.NULL

                    // an IllegalArgumentException is thrown if the type isn't String.
                } catch (ex: IllegalArgumentException) {
                    return false
                }
            }
            return false
        }
    }
}