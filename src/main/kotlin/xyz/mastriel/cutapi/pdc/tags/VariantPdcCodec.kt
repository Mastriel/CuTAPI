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
            Variant.Null -> container.setType(VariantKind.Null)
            is Variant.String -> {
                container.setType(VariantKind.String)
                container.set(ValueKey, PersistentDataType.STRING, variant.value)
            }

            is Variant.Boolean -> {
                container.setType(VariantKind.Boolean)
                container.set(ValueKey, PersistentDataType.BYTE, if (variant.value) 1 else 0)
            }

            is Variant.Byte -> {
                container.setType(VariantKind.Byte)
                container.set(ValueKey, PersistentDataType.BYTE, variant.value)
            }

            is Variant.Short -> {
                container.setType(VariantKind.Short)
                container.set(ValueKey, PersistentDataType.SHORT, variant.value)
            }

            is Variant.Int -> {
                container.setType(VariantKind.Int)
                container.set(ValueKey, PersistentDataType.INTEGER, variant.value)
            }

            is Variant.Long -> {
                container.setType(VariantKind.Long)
                container.set(ValueKey, PersistentDataType.LONG, variant.value)
            }

            is Variant.Float -> {
                container.setType(VariantKind.Float)
                container.set(ValueKey, PersistentDataType.FLOAT, variant.value)
            }

            is Variant.Double -> {
                container.setType(VariantKind.Double)
                container.set(ValueKey, PersistentDataType.DOUBLE, variant.value)
            }

            is Variant.Char -> {
                container.setType(VariantKind.Char)
                container.set(ValueKey, PersistentDataType.STRING, variant.value.toString())
            }

            is Variant.Identifier -> {
                container.setType(VariantKind.Identifier)
                container.set(ValueKey, PersistentDataType.STRING, variant.value.toString())
            }

            is Variant.ResourceRef -> {
                container.setType(VariantKind.ResourceRef)
                container.set(ValueKey, PersistentDataType.STRING, variant.value.toString())
            }

            is Variant.List -> {
                container.setType(VariantKind.List)
                container.set(SizeKey, PersistentDataType.INTEGER, variant.value.size)
                variant.value.forEachIndexed { index, value ->
                    container.set(entryKey(index), PersistentDataType.TAG_CONTAINER, encode(context, value))
                }
            }

            is Variant.Map -> {
                container.setType(VariantKind.Map)
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
            typeOf(VariantKind.Null) -> Variant.Null
            typeOf(VariantKind.String) -> Variant.String(container.get(ValueKey, PersistentDataType.STRING)!!)
            typeOf(VariantKind.Boolean) -> Variant.Boolean(
                container.get(
                    ValueKey,
                    PersistentDataType.BYTE
                ) == 1.toByte()
            )

            typeOf(VariantKind.Byte) -> Variant.Byte(container.get(ValueKey, PersistentDataType.BYTE)!!)
            typeOf(VariantKind.Short) -> Variant.Short(container.get(ValueKey, PersistentDataType.SHORT)!!)
            typeOf(VariantKind.Int) -> Variant.Int(container.get(ValueKey, PersistentDataType.INTEGER)!!)
            typeOf(VariantKind.Long) -> Variant.Long(container.get(ValueKey, PersistentDataType.LONG)!!)
            typeOf(VariantKind.Float) -> Variant.Float(container.get(ValueKey, PersistentDataType.FLOAT)!!)
            typeOf(VariantKind.Double) -> Variant.Double(container.get(ValueKey, PersistentDataType.DOUBLE)!!)
            typeOf(VariantKind.Char) -> Variant.Char(
                container.get(ValueKey, PersistentDataType.STRING)!!.single()
            )

            typeOf(VariantKind.Identifier) -> Variant.Identifier(id(container.get(ValueKey, PersistentDataType.STRING)!!))
            typeOf(VariantKind.ResourceRef) -> Variant.ResourceRef(
                ref<Resource>(
                    container.get(
                        ValueKey,
                        PersistentDataType.STRING
                    )!!
                )
            )

            typeOf(VariantKind.List) -> {
                val size = container.get(SizeKey, PersistentDataType.INTEGER) ?: 0
                Variant.List((0 until size).map { index ->
                    decode(container.get(entryKey(index), PersistentDataType.TAG_CONTAINER)!!)
                })
            }

            typeOf(VariantKind.Map) -> {
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

    private fun PersistentDataContainer.setType(kind: VariantKind) {
        set(TypeKey, PersistentDataType.STRING, typeOf(kind))
    }

    private fun typeOf(kind: VariantKind): String = kind.id.toString()
}
