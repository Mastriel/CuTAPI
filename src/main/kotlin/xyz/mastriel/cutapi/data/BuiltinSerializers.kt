@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.data

import net.kyori.adventure.text.Component as ComponentValue
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.attribute.Attribute as AttributeValue
import org.bukkit.inventory.EquipmentSlotGroup as EquipmentSlotGroupValue
import org.bukkit.inventory.ItemType as ItemTypeValue
import xyz.mastriel.cutapi.item.attachments.ToolSpeed as ToolSpeedValue
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.registry.toIdentifier
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.utils.Color as ColorValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/** Built-in domain serializers, initialized independently as they are first used. */
public object BuiltinSerializers {
    public val Component: TaggedSerializer<ComponentValue> by lazy {
        VariantSerializer.mapped(
            serializer = VariantSerializer.String,
            id = id("cutapi:gson_kyori_component"),
            serialize = { GsonComponentSerializer.gson().serialize(it) },
            deserialize = { GsonComponentSerializer.gson().deserialize(it) },
        )
    }

    public val Color: Serializer<ColorValue> by lazy {
        VariantSerializer.mapped(
            serializer = VariantSerializer.String,
            serialize = ColorValue::toString,
            deserialize = ColorValue::of,
        )
    }

    public val ItemType: Serializer<ItemTypeValue> by lazy {
        VariantSerializer.mapped(
            serializer = VariantSerializer.String,
            serialize = { type -> type.key.toString() },
            deserialize = { value ->
                val key = NamespacedKey.fromString(value) ?: error("Invalid ItemType key: $value")
                Registry.ITEM.get(key) ?: error("Unknown ItemType: $value")
            },
        )
    }

    public val EquipmentSlotGroup: Serializer<EquipmentSlotGroupValue> by lazy {
        VariantSerializer.mapped(
            serializer = VariantSerializer.String,
            serialize = { it.toString() },
            deserialize = { name ->
                EquipmentSlotGroupValue.getByName(name)
                    ?: throw DataSerializationException("Unknown equipment slot group '$name'")
            },
        )
    }

    public val Attribute: Serializer<AttributeValue> by lazy {
        VariantSerializer.mapped(
            serializer = VariantSerializer.Id,
            serialize = { it.key.toIdentifier() },
            deserialize = { identifier ->
                Registry.ATTRIBUTE.get(identifier.toNamespacedKey())
                    ?: throw DataSerializationException("Unknown attribute '$identifier'")
            },
        )
    }

    public val ToolSpeed: Serializer<ToolSpeedValue> by lazy {
        VariantSerializer.mapped(
            serializer = VariantSerializer.Float,
            id = id("cutapi:tool_speed"),
            serialize = { it.speed },
            deserialize = { ToolSpeedValue(it) },
        )
    }

    /** Represents a duration as a floating-point number of seconds. */
    public val DurationSeconds: Serializer<Duration> by lazy {
        VariantSerializer.mapped(
            serializer = VariantSerializer.Float,
            serialize = { duration -> duration.toDouble(DurationUnit.SECONDS).toFloat() },
            deserialize = { seconds -> seconds.toDouble().seconds },
        )
    }

    public val SpecificTextureMetadata: Serializer<MutableMap<Int, Texture2D.Metadata>> by lazy {
        VariantSerializer.mapped(
            VariantSerializer.MapOf(Texture2D.Metadata.embedded()),
            serialize = { values -> values.mapKeys { (key, _) -> key.toString() } },
            deserialize = { values ->
                values.mapKeysTo(mutableMapOf()) { (key, _) ->
                    key.toIntOrNull()
                        ?: throw IllegalArgumentException("Specific metadata key '$key' must be an integer.")
                }
            },
        )
    }
}
