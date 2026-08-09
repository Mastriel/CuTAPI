package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.*
import org.bukkit.persistence.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import java.nio.charset.*

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
                for ((key, value) in variant.value) {
                    container.set(mapKey(key), PersistentDataType.TAG_CONTAINER, encode(context, value))
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
                if (container.has(SizeKey)) {
                    throw DataSerializationException(
                        "Legacy indexed Variant maps must be migrated before they can be decoded"
                    )
                }
                Variant.Map(
                    container.keys
                        .asSequence()
                        .filter(::isMapKey)
                        .sortedBy { it.key }
                        .associateTo(linkedMapOf()) { key ->
                            val value = container.get(key, PersistentDataType.TAG_CONTAINER)
                                ?: throw DataSerializationException("Variant map entry '$key' is not a container")
                            decodeMapKey(key) to decode(value)
                        }
                )
            }

            else -> throw DataSerializationException("Unsupported PDC variant type '$type'")
        }
    }

    private fun entryKey(index: Int) = id("cutapi:variant_entry/$index").toNamespacedKey()

    private fun mapKey(key: String) = id("cutapi:$MapEntryPrefix${key.encodeToHex()}").toNamespacedKey()

    private fun isMapKey(key: NamespacedKey): Boolean =
        key.namespace == "cutapi" && key.key.startsWith(MapEntryPrefix)

    private fun decodeMapKey(key: NamespacedKey): String {
        val encoded = key.key.removePrefix(MapEntryPrefix)
        if (encoded.length % 2 != 0) {
            throw DataSerializationException("Invalid Variant map key '$key'")
        }
        val bytes = ByteArray(encoded.length / 2) { index ->
            encoded.substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte()
                ?: throw DataSerializationException("Invalid Variant map key '$key'")
        }
        return try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (exception: CharacterCodingException) {
            throw DataSerializationException("Invalid UTF-8 Variant map key '$key'", exception)
        }
    }

    private fun String.encodeToHex(): String = buildString(length * 2) {
        for (byte in this@encodeToHex.encodeToByteArray()) {
            val unsigned = byte.toInt() and 0xff
            append(HexDigits[unsigned ushr 4])
            append(HexDigits[unsigned and 0x0f])
        }
    }

    private fun PersistentDataContainer.setType(kind: VariantKind) {
        set(TypeKey, PersistentDataType.STRING, typeOf(kind))
    }

    private fun typeOf(kind: VariantKind): String = kind.id.toString()

    private const val MapEntryPrefix = "variant_map/"
    private const val HexDigits = "0123456789abcdef"
}
