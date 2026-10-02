package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.json.*

public sealed interface MinecraftColor {
    public fun toJson(): JsonElement

    public data class Packed(public val rgb: Int) : MinecraftColor {
        override fun toJson(): JsonElement = JsonPrimitive(rgb)
    }

    public data class Components(public val red: Float, public val green: Float, public val blue: Float) :
        MinecraftColor {
        init {
            require(red in 0f..1f && green in 0f..1f && blue in 0f..1f) {
                "Minecraft color components must be within 0..1."
            }
        }

        override fun toJson(): JsonElement = JsonArray(listOf(red, green, blue).map(::JsonPrimitive))
    }
}

/** Every tint source available to Minecraft 1.21.11 item model nodes. */
public sealed interface ItemModelTint {
    public fun toJson(): JsonObject

    public data class Constant(public val value: MinecraftColor) : ItemModelTint {
        override fun toJson(): JsonObject = typedObject("minecraft:constant", "value" to value.toJson())
    }

    public data class CustomModelData(public val default: MinecraftColor, public val index: Int = 0) : ItemModelTint {
        override fun toJson(): JsonObject = typedObject(
            "minecraft:custom_model_data",
            "index" to JsonPrimitive(index),
            "default" to default.toJson(),
        )
    }

    public data class Dye(public val default: MinecraftColor) : ItemModelTint {
        override fun toJson(): JsonObject = typedObject("minecraft:dye", "default" to default.toJson())
    }

    public data class Grass(public val temperature: Float, public val downfall: Float) : ItemModelTint {
        init {
            require(temperature in 0f..1f && downfall in 0f..1f) {
                "Grass tint temperature and downfall must be within 0..1."
            }
        }

        override fun toJson(): JsonObject = typedObject(
            "minecraft:grass",
            "temperature" to JsonPrimitive(temperature),
            "downfall" to JsonPrimitive(downfall),
        )
    }

    public data class Firework(public val default: MinecraftColor) : ItemModelTint {
        override fun toJson(): JsonObject = typedObject("minecraft:firework", "default" to default.toJson())
    }

    public data class MapColor(public val default: MinecraftColor) : ItemModelTint {
        override fun toJson(): JsonObject = typedObject("minecraft:map_color", "default" to default.toJson())
    }

    public data class Potion(public val default: MinecraftColor) : ItemModelTint {
        override fun toJson(): JsonObject = typedObject("minecraft:potion", "default" to default.toJson())
    }

    public data class Team(public val default: MinecraftColor) : ItemModelTint {
        override fun toJson(): JsonObject = typedObject("minecraft:team", "default" to default.toJson())
    }
}

public enum class MinecraftDyeColor(public val id: String) {
    White("white"), Orange("orange"), Magenta("magenta"), LightBlue("light_blue"), Yellow("yellow"),
    Lime("lime"), Pink("pink"), Gray("gray"), LightGray("light_gray"), Cyan("cyan"), Purple("purple"),
    Blue("blue"), Brown("brown"), Green("green"), Red("red"), Black("black"),
}

public enum class MinecraftWoodType(public val id: String) {
    Oak("oak"), Spruce("spruce"), Birch("birch"), Acacia("acacia"), Cherry("cherry"), Jungle("jungle"),
    DarkOak("dark_oak"), PaleOak("pale_oak"), Mangrove("mangrove"), Bamboo("bamboo"),
    Crimson("crimson"), Warped("warped"),
}

public enum class MinecraftHeadKind(public val id: String) {
    Skeleton("skeleton"), WitherSkeleton("wither_skeleton"), Player("player"), Zombie("zombie"),
    Creeper("creeper"), Piglin("piglin"), Dragon("dragon"),
}

public enum class MinecraftDirection(public val id: String) {
    Down("down"), Up("up"), North("north"), South("south"), West("west"), East("east"),
}

/** Every special-model type available to Minecraft 1.21.11 item model nodes. */
public sealed interface SpecialItemModel {
    public fun toJson(): JsonObject

    public data class Banner(public val color: MinecraftDyeColor) : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject("minecraft:banner", "color" to JsonPrimitive(color.id))
    }

    public data class Bed(public val texture: String) : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject("minecraft:bed", "texture" to JsonPrimitive(texture))
    }

    public data class Chest(public val texture: String, public val openness: Float = 0f) : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject(
            "minecraft:chest",
            "texture" to JsonPrimitive(texture),
            "openness" to JsonPrimitive(openness),
        )
    }

    public data object Conduit : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject("minecraft:conduit")
    }

    public data object DecoratedPot : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject("minecraft:decorated_pot")
    }

    public data class HangingSign(
        public val woodType: MinecraftWoodType,
        public val texture: String? = null,
    ) : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject(
            "minecraft:hanging_sign",
            "wood_type" to JsonPrimitive(woodType.id),
            *optionalJson("texture", texture?.let(::JsonPrimitive)),
        )
    }

    public data class Head(
        public val kind: MinecraftHeadKind,
        public val texture: String? = null,
        public val animation: Float = 0f,
    ) : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject(
            "minecraft:head",
            "kind" to JsonPrimitive(kind.id),
            *optionalJson("texture", texture?.let(::JsonPrimitive)),
            *nonDefaultJson("animation", animation, 0f),
        )
    }

    public data object PlayerHead : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject("minecraft:player_head")
    }

    public data object Shield : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject("minecraft:shield")
    }

    public data class ShulkerBox(
        public val texture: String,
        public val openness: Float = 0f,
        public val orientation: MinecraftDirection = MinecraftDirection.Up,
    ) : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject(
            "minecraft:shulker_box",
            "texture" to JsonPrimitive(texture),
            *nonDefaultJson("openness", openness, 0f),
            *nonDefaultJson("orientation", orientation, MinecraftDirection.Up) { JsonPrimitive(it.id) },
        )
    }

    public data class StandingSign(
        public val woodType: MinecraftWoodType,
        public val texture: String? = null,
    ) : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject(
            "minecraft:standing_sign",
            "wood_type" to JsonPrimitive(woodType.id),
            *optionalJson("texture", texture?.let(::JsonPrimitive)),
        )
    }

    public data object Trident : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject("minecraft:trident")
    }

    public data object SpearInHand : SpecialItemModel {
        override fun toJson(): JsonObject = typedObject("minecraft:spear_in_hand")
    }
}

/** Boolean property selectors for `minecraft:condition`. */
public sealed interface ItemModelBooleanProperty {
    public fun toJson(): JsonObject

    public data object Broken : ItemModelBooleanProperty by propertyObject("minecraft:broken")
    public data object BundleHasSelectedItem : ItemModelBooleanProperty by propertyObject("minecraft:bundle/has_selected_item")
    public data object Carried : ItemModelBooleanProperty by propertyObject("minecraft:carried")
    public data class CustomModelData(public val index: Int = 0) : ItemModelBooleanProperty {
        override fun toJson(): JsonObject = propertyObject("minecraft:custom_model_data", "index" to JsonPrimitive(index)).toJson()
    }
    public data object Damaged : ItemModelBooleanProperty by propertyObject("minecraft:damaged")
    public data object ExtendedView : ItemModelBooleanProperty by propertyObject("minecraft:extended_view")
    public data object FishingRodCast : ItemModelBooleanProperty by propertyObject("minecraft:fishing_rod/cast")
    public data class HasComponent(public val component: String, public val ignoreDefault: Boolean = false) :
        ItemModelBooleanProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:has_component",
            "component" to JsonPrimitive(component),
            *nonDefaultJson("ignore_default", ignoreDefault, false),
        ).toJson()
    }
    public data class KeybindDown(public val keybind: String) : ItemModelBooleanProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:keybind_down",
            "keybind" to JsonPrimitive(keybind),
        ).toJson()
    }
    public data class Component(public val predicate: String, public val value: JsonElement) : ItemModelBooleanProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:component",
            "predicate" to JsonPrimitive(predicate),
            "value" to value,
        ).toJson()
    }
    public data object Selected : ItemModelBooleanProperty by propertyObject("minecraft:selected")
    public data object UsingItem : ItemModelBooleanProperty by propertyObject("minecraft:using_item")
    public data object ViewEntity : ItemModelBooleanProperty by propertyObject("minecraft:view_entity")
}

/** String or component-valued property selectors for `minecraft:select`. */
public sealed interface ItemModelSelectProperty {
    public fun toJson(): JsonObject

    public data class BlockState(public val blockStateProperty: String) : ItemModelSelectProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:block_state",
            "block_state_property" to JsonPrimitive(blockStateProperty),
        ).toJson()
    }
    public data object ChargeType : ItemModelSelectProperty by propertyObject("minecraft:charge_type")
    public data class Component(public val component: String) : ItemModelSelectProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:component",
            "component" to JsonPrimitive(component),
        ).toJson()
    }
    public data object ContextDimension : ItemModelSelectProperty by propertyObject("minecraft:context_dimension")
    public data object ContextEntityType : ItemModelSelectProperty by propertyObject("minecraft:context_entity_type")
    public data class CustomModelData(public val index: Int = 0) : ItemModelSelectProperty {
        override fun toJson(): JsonObject = propertyObject("minecraft:custom_model_data", "index" to JsonPrimitive(index)).toJson()
    }
    public data object DisplayContext : ItemModelSelectProperty by propertyObject("minecraft:display_context")
    public data class LocalTime(
        public val pattern: String,
        public val locale: String? = null,
        public val timeZone: String? = null,
    ) : ItemModelSelectProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:local_time",
            "pattern" to JsonPrimitive(pattern),
            *optionalJson("locale", locale?.let(::JsonPrimitive)),
            *optionalJson("time_zone", timeZone?.let(::JsonPrimitive)),
        ).toJson()
    }
    public data object MainHand : ItemModelSelectProperty by propertyObject("minecraft:main_hand")
    public data object TrimMaterial : ItemModelSelectProperty by propertyObject("minecraft:trim_material")
}

public enum class CompassTarget(public val id: String) {
    None("none"), Spawn("spawn"), Lodestone("lodestone"), Recovery("recovery"),
}

public enum class TimeSource(public val id: String) {
    Daytime("daytime"), MoonPhase("moon_phase"), Random("random"),
}

/** Numeric property selectors for `minecraft:range_dispatch`. */
public sealed interface ItemModelRangeProperty {
    public fun toJson(): JsonObject

    public data object BundleFullness : ItemModelRangeProperty by propertyObject("minecraft:bundle/fullness")
    public data class Compass(public val target: CompassTarget, public val wobble: Boolean = true) : ItemModelRangeProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:compass",
            "target" to JsonPrimitive(target.id),
            *nonDefaultJson("wobble", wobble, true),
        ).toJson()
    }
    public data object Cooldown : ItemModelRangeProperty by propertyObject("minecraft:cooldown")
    public data object CrossbowPull : ItemModelRangeProperty by propertyObject("minecraft:crossbow/pull")
    public data class Count(public val normalize: Boolean = true) : ItemModelRangeProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:count",
            *nonDefaultJson("normalize", normalize, true),
        ).toJson()
    }
    public data class CustomModelData(public val index: Int = 0) : ItemModelRangeProperty {
        override fun toJson(): JsonObject = propertyObject("minecraft:custom_model_data", "index" to JsonPrimitive(index)).toJson()
    }
    public data class Damage(public val normalize: Boolean = true) : ItemModelRangeProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:damage",
            *nonDefaultJson("normalize", normalize, true),
        ).toJson()
    }
    public data class Time(public val source: TimeSource, public val wobble: Boolean = true) : ItemModelRangeProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:time",
            "source" to JsonPrimitive(source.id),
            *nonDefaultJson("wobble", wobble, true),
        ).toJson()
    }
    public data class UseCycle(public val period: Float = 1f) : ItemModelRangeProperty {
        init {
            require(period > 0f) { "Use-cycle period must be positive." }
        }
        override fun toJson(): JsonObject = propertyObject("minecraft:use_cycle", "period" to JsonPrimitive(period)).toJson()
    }
    public data class UseDuration(public val remaining: Boolean = false) : ItemModelRangeProperty {
        override fun toJson(): JsonObject = propertyObject(
            "minecraft:use_duration",
            *nonDefaultJson("remaining", remaining, false),
        ).toJson()
    }
}

private class PropertyObject(private val json: JsonObject) :
    ItemModelBooleanProperty,
    ItemModelSelectProperty,
    ItemModelRangeProperty {
    override fun toJson(): JsonObject = json
}

private fun propertyObject(type: String, vararg fields: Pair<String, JsonElement>): PropertyObject =
    PropertyObject(JsonObject(linkedMapOf("property" to JsonPrimitive(type), *fields)))

private fun typedObject(type: String, vararg fields: Pair<String, JsonElement>): JsonObject =
    JsonObject(linkedMapOf("type" to JsonPrimitive(type), *fields))

private fun optionalJson(name: String, value: JsonElement?): Array<Pair<String, JsonElement>> =
    if (value == null) emptyArray() else arrayOf(name to value)

private fun <T> nonDefaultJson(
    name: String,
    value: T,
    default: T,
    encode: (T) -> JsonElement = { JsonPrimitive(it.toString()) },
): Array<Pair<String, JsonElement>> {
    if (value == default) return emptyArray()
    val encoded = when (value) {
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> encode(value)
    }
    return arrayOf(name to encoded)
}
