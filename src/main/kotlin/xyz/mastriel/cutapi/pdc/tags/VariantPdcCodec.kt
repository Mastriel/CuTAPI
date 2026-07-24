package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.persistence.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*

internal object VariantPdcCodec {
    private val TypeKey = id("cutapi:variant_type").toNamespacedKey()
    private val ValueKey = id("cutapi:variant_value").toNamespacedKey()
    private val SizeKey = id("cutapi:variant_size").toNamespacedKey()

    fun encode(context: PersistentDataAdapterContext, variant: Variant): PersistentDataContainer {
        val container = context.newPersistentDataContainer()
        when (variant) {
            Variant.Null -> container.setType(VariantSerializer.Null)
            is Variant.String -> {
                container.setType(VariantSerializer.String)
                container.set(ValueKey, PersistentDataType.STRING, variant.value)
            }

            is Variant.Boolean -> {
                container.setType(VariantSerializer.Boolean)
                container.set(ValueKey, PersistentDataType.BYTE, if (variant.value) 1 else 0)
            }

            is Variant.Byte -> {
                container.setType(VariantSerializer.Byte)
                container.set(ValueKey, PersistentDataType.BYTE, variant.value)
            }

            is Variant.Short -> {
                container.setType(VariantSerializer.Short)
                container.set(ValueKey, PersistentDataType.SHORT, variant.value)
            }

            is Variant.Int -> {
                container.setType(VariantSerializer.Int)
                container.set(ValueKey, PersistentDataType.INTEGER, variant.value)
            }

            is Variant.Long -> {
                container.setType(VariantSerializer.Long)
                container.set(ValueKey, PersistentDataType.LONG, variant.value)
            }

            is Variant.Float -> {
                container.setType(VariantSerializer.Float)
                container.set(ValueKey, PersistentDataType.FLOAT, variant.value)
            }

            is Variant.Double -> {
                container.setType(VariantSerializer.Double)
                container.set(ValueKey, PersistentDataType.DOUBLE, variant.value)
            }

            is Variant.Char -> {
                container.setType(VariantSerializer.Char)
                container.set(ValueKey, PersistentDataType.STRING, variant.value.toString())
            }

            is Variant.Identifier -> {
                container.setType(VariantSerializer.Id)
                container.set(ValueKey, PersistentDataType.STRING, variant.value.toString())
            }

            is Variant.ResourceRef -> {
                container.setType(VariantSerializer.ResourceRef)
                container.set(ValueKey, PersistentDataType.STRING, variant.value.toString())
            }

            is Variant.List -> {
                container.setType(VariantSerializer.List)
                container.set(SizeKey, PersistentDataType.INTEGER, variant.value.size)
                variant.value.forEachIndexed { index, value ->
                    container.set(entryKey(index), PersistentDataType.TAG_CONTAINER, encode(context, value))
                }
            }

            is Variant.Map -> {
                container.setType(VariantSerializer.Map)
                container.set(SizeKey, PersistentDataType.INTEGER, variant.value.size)
                variant.value.entries.forEachIndexed { index, (key, value) ->
                    container.set(mapKey(index, "key"), PersistentDataType.TAG_CONTAINER, encode(context, key))
                    container.set(mapKey(index, "value"), PersistentDataType.TAG_CONTAINER, encode(context, value))
                }
            }
        }
        return container
    }

    fun decode(container: PersistentDataContainer): Variant {
        return when (val type = container.get(TypeKey, PersistentDataType.STRING)) {
            typeOf(VariantSerializer.Null) -> Variant.Null
            typeOf(VariantSerializer.String) -> Variant.String(container.get(ValueKey, PersistentDataType.STRING)!!)
            typeOf(VariantSerializer.Boolean) -> Variant.Boolean(
                container.get(
                    ValueKey,
                    PersistentDataType.BYTE
                ) == 1.toByte()
            )

            typeOf(VariantSerializer.Byte) -> Variant.Byte(container.get(ValueKey, PersistentDataType.BYTE)!!)
            typeOf(VariantSerializer.Short) -> Variant.Short(container.get(ValueKey, PersistentDataType.SHORT)!!)
            typeOf(VariantSerializer.Int) -> Variant.Int(container.get(ValueKey, PersistentDataType.INTEGER)!!)
            typeOf(VariantSerializer.Long) -> Variant.Long(container.get(ValueKey, PersistentDataType.LONG)!!)
            typeOf(VariantSerializer.Float) -> Variant.Float(container.get(ValueKey, PersistentDataType.FLOAT)!!)
            typeOf(VariantSerializer.Double) -> Variant.Double(container.get(ValueKey, PersistentDataType.DOUBLE)!!)
            typeOf(VariantSerializer.Char) -> Variant.Char(
                container.get(ValueKey, PersistentDataType.STRING)!!.single()
            )

            typeOf(VariantSerializer.Id) -> Variant.Identifier(id(container.get(ValueKey, PersistentDataType.STRING)!!))
            typeOf(VariantSerializer.ResourceRef) -> Variant.ResourceRef(
                ref<Resource>(
                    container.get(
                        ValueKey,
                        PersistentDataType.STRING
                    )!!
                )
            )

            typeOf(VariantSerializer.List) -> {
                val size = container.get(SizeKey, PersistentDataType.INTEGER) ?: 0
                Variant.List((0 until size).map { index ->
                    decode(container.get(entryKey(index), PersistentDataType.TAG_CONTAINER)!!)
                })
            }

            typeOf(VariantSerializer.Map) -> {
                val size = container.get(SizeKey, PersistentDataType.INTEGER) ?: 0
                Variant.Map((0 until size).associate { index ->
                    val key = decode(container.get(mapKey(index, "key"), PersistentDataType.TAG_CONTAINER)!!)
                    val value = decode(container.get(mapKey(index, "value"), PersistentDataType.TAG_CONTAINER)!!)
                    key to value
                })
            }

            else -> throw DataSerializationException("Unsupported PDC variant type '$type'")
        }
    }

    private fun entryKey(index: Int) = id("cutapi:variant_entry/$index").toNamespacedKey()

    private fun mapKey(index: Int, part: String) = id("cutapi:variant_entry/$index/$part").toNamespacedKey()

    private fun PersistentDataContainer.setType(serializer: TaggedSerializer<*>) {
        set(TypeKey, PersistentDataType.STRING, typeOf(serializer))
    }

    private fun typeOf(serializer: TaggedSerializer<*>): String = serializer.id.toString()
}
