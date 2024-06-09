package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.pdc.*
import xyz.mastriel.cutapi.pdc.tags.converters.*

public open class PDCTagContainer(public var container: PersistentDataContainer) : TagContainer {

    override fun <P: Any, C: Any> set(key: String, complexValue: C?, converter: TagConverter<P, C>) {
        val namespacedKey = NamespacedKey(Plugin, key)
        if (complexValue == null) return container.remove(namespacedKey)

        val primitiveValue = converter.toPrimitive(complexValue)

        container.setPrimitiveValue(converter.primitiveClass, key, primitiveValue)
    }

    override fun <P: Any, C: Any> get(key: String, converter: TagConverter<P, C>) : C? {
        val namespacedKey = NamespacedKey(Plugin, key)
        if (!container.has(namespacedKey)) return null

        val value = container.getPrimitiveValue(converter.primitiveClass, key)
        return converter.fromPrimitive(value!!)
    }

    override fun has(key: String): Boolean {
        return container.has(NamespacedKey(Plugin, key))
    }


    override fun isNull(key: String): Boolean {
        val namespacedKey = NamespacedKey(Plugin, key)
        return checkNull(container, namespacedKey)
    }


    public companion object {
        public fun checkNull(container: PersistentDataContainer, namespacedKey: NamespacedKey) : Boolean {
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