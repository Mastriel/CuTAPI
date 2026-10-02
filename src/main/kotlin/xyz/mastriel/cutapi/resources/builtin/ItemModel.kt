@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

/** A client item definition stored in Minecraft's `assets/<namespace>/items` namespace. */
public class ItemModel(
    override val ref: ResourceRef<ItemModel>,
    public val json: JsonObject,
    override val metadata: Metadata = Metadata(),
) : Resource(ref, metadata), ByteArraySerializable {

    public constructor(
        ref: ResourceRef<ItemModel>,
        data: ItemModelData,
        metadata: Metadata = Metadata(),
    ) : this(ref, CuTApiJson.encodeToJsonElement(data).jsonObject, metadata)

    /** Typed view of the standard top-level fields; [json] remains authoritative. */
    public val data: ItemModelData by lazy { CuTApiJson.decodeFromJsonElement(json) }

    init {
        val model = json["model"] as? JsonObject
            ?: throw IllegalArgumentException("An item definition must contain an object 'model'.")
        validateItemModelNode(model)
        inspector.single("Model Type") { model["type"]?.jsonPrimitive?.content ?: "<missing>" }
    }

    public class Metadata : CuTMeta() {
        public companion object : Schema<Metadata> by schema(id("cutapi:minecraft_item_model"), {
            extends { CuTMeta }
            constructs { Metadata() }
        })
    }

    public fun toPackData(): JsonObject = JsonObject(json + ("model" to data.model.resolveLocalResourceRefs()))

    override fun toBytes(): ByteArray = CuTApiJson.encodeToString(json).toByteArray(Charsets.UTF_8)
}

@kotlinx.serialization.Serializable
public data class ItemModelData(
    public val model: JsonObject,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("hand_animation_on_swap") public val handAnimationOnSwap: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("oversized_in_gui") public val oversizedInGui: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("swap_animation_scale") public val swapAnimationScale: Float = 1f,
)

public val ItemModelResourceLoader: ResourceFileLoader<ItemModel> = resourceLoader(
    extensions = listOf("item_model.json"),
    metadataSchema = ItemModel.Metadata,
    dependencies = listOf(MinecraftModelResourceLoader),
) {
    try {
        val parsed = CuTApiJson.parseToJsonElement(dataAsString).jsonObject
        val standardFields = CuTApiJson.decodeFromJsonElement<ItemModelData>(parsed)
        validateItemModelNode(standardFields.model)
        success(ItemModel(ref, parsed, metadata ?: ItemModel.Metadata()))
    } catch (exception: Exception) {
        failure(exception)
    }
}

/** Converts an item-model resource ref to the ID stored in the `minecraft:item_model` component. */
public fun ResourceRef<ItemModel>.toMinecraftItemModelLocator(): String =
    "$minecraftNamespace:${sanitizeResourcePath(path(withExtension = false))}"

/** A typed authoring surface for every Minecraft 1.21.11 item-model node family. */
public sealed interface ItemModelNode {
    public fun toJson(): JsonObject
}

public data class ModelItemModelNode(
    public val model: String,
    public val tints: List<JsonObject> = emptyList(),
) : ItemModelNode {
    public constructor(model: ResourceRef<MinecraftModel>, tints: List<JsonObject> = emptyList()) :
        this(model.toMinecraftModelLocator(), tints)

    public constructor(model: String, tints: Collection<ItemModelTint>) : this(model, tints.map(ItemModelTint::toJson))

    public constructor(model: ResourceRef<MinecraftModel>, tints: Collection<ItemModelTint>) :
        this(model.toMinecraftModelLocator(), tints.map(ItemModelTint::toJson))

    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "minecraft:model")
        put("model", model)
        if (tints.isNotEmpty()) put("tints", JsonArray(tints))
    }
}

public data class SpecialItemModelNode(
    public val base: String,
    public val model: JsonObject,
) : ItemModelNode {
    public constructor(base: ResourceRef<MinecraftModel>, model: JsonObject) : this(base.toMinecraftModelLocator(), model)
    public constructor(base: String, model: SpecialItemModel) : this(base, model.toJson())
    public constructor(base: ResourceRef<MinecraftModel>, model: SpecialItemModel) :
        this(base.toMinecraftModelLocator(), model.toJson())

    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "minecraft:special")
        put("base", base)
        put("model", model)
    }
}

public data class CompositeItemModelNode(public val models: List<ItemModelNode>) : ItemModelNode {
    override fun toJson(): JsonObject = buildJsonObject {
        put("type", "minecraft:composite")
        put("models", JsonArray(models.map(ItemModelNode::toJson)))
    }
}

public data class ConditionItemModelNode(
    public val property: JsonObject,
    public val onTrue: ItemModelNode,
    public val onFalse: ItemModelNode,
) : ItemModelNode {
    public constructor(
        property: ItemModelBooleanProperty,
        onTrue: ItemModelNode,
        onFalse: ItemModelNode,
    ) : this(property.toJson(), onTrue, onFalse)

    override fun toJson(): JsonObject = buildJsonObject {
        property.forEach(::put)
        put("type", "minecraft:condition")
        put("on_true", onTrue.toJson())
        put("on_false", onFalse.toJson())
    }
}

public data class SelectItemModelCase(public val whenValue: JsonElement, public val model: ItemModelNode)

public data class SelectItemModelNode(
    public val property: JsonObject,
    public val cases: List<SelectItemModelCase>,
    public val fallback: ItemModelNode? = null,
) : ItemModelNode {
    public constructor(
        property: ItemModelSelectProperty,
        cases: List<SelectItemModelCase>,
        fallback: ItemModelNode? = null,
    ) : this(property.toJson(), cases, fallback)

    override fun toJson(): JsonObject = buildJsonObject {
        property.forEach(::put)
        put("type", "minecraft:select")
        put("cases", JsonArray(cases.map { case ->
            buildJsonObject {
                put("when", case.whenValue)
                put("model", case.model.toJson())
            }
        }))
        fallback?.let { put("fallback", it.toJson()) }
    }
}

public data class RangeDispatchItemModelEntry(public val threshold: Float, public val model: ItemModelNode)

public data class RangeDispatchItemModelNode(
    public val property: JsonObject,
    public val entries: List<RangeDispatchItemModelEntry>,
    public val scale: Float = 1f,
    public val fallback: ItemModelNode? = null,
) : ItemModelNode {
    public constructor(
        property: ItemModelRangeProperty,
        entries: List<RangeDispatchItemModelEntry>,
        scale: Float = 1f,
        fallback: ItemModelNode? = null,
    ) : this(property.toJson(), entries, scale, fallback)

    override fun toJson(): JsonObject = buildJsonObject {
        property.forEach(::put)
        put("type", "minecraft:range_dispatch")
        if (scale != 1f) put("scale", scale)
        put("entries", JsonArray(entries.map { entry ->
            buildJsonObject {
                put("threshold", entry.threshold)
                put("model", entry.model.toJson())
            }
        }))
        fallback?.let { put("fallback", it.toJson()) }
    }
}

public data object EmptyItemModelNode : ItemModelNode {
    override fun toJson(): JsonObject = buildJsonObject { put("type", "minecraft:empty") }
}

public data object BundleSelectedItemModelNode : ItemModelNode {
    override fun toJson(): JsonObject = buildJsonObject { put("type", "minecraft:bundle/selected_item") }
}

private val supportedItemModelNodeTypes: Set<String> = setOf(
    "minecraft:model",
    "minecraft:special",
    "minecraft:composite",
    "minecraft:condition",
    "minecraft:select",
    "minecraft:range_dispatch",
    "minecraft:empty",
    "minecraft:bundle/selected_item",
)

private val supportedTintTypes: Set<String> = setOf(
    "minecraft:constant",
    "minecraft:custom_model_data",
    "minecraft:dye",
    "minecraft:grass",
    "minecraft:firework",
    "minecraft:map_color",
    "minecraft:potion",
    "minecraft:team",
)

private val supportedSpecialModelTypes: Set<String> = setOf(
    "minecraft:banner",
    "minecraft:bed",
    "minecraft:chest",
    "minecraft:conduit",
    "minecraft:decorated_pot",
    "minecraft:hanging_sign",
    "minecraft:head",
    "minecraft:player_head",
    "minecraft:shield",
    "minecraft:shulker_box",
    "minecraft:standing_sign",
    "minecraft:trident",
    "minecraft:spear_in_hand",
)

private val supportedBooleanProperties: Set<String> = setOf(
    "minecraft:broken",
    "minecraft:bundle/has_selected_item",
    "minecraft:carried",
    "minecraft:component",
    "minecraft:custom_model_data",
    "minecraft:damaged",
    "minecraft:extended_view",
    "minecraft:fishing_rod/cast",
    "minecraft:has_component",
    "minecraft:keybind_down",
    "minecraft:selected",
    "minecraft:using_item",
    "minecraft:view_entity",
)

private val supportedSelectProperties: Set<String> = setOf(
    "minecraft:block_state",
    "minecraft:charge_type",
    "minecraft:component",
    "minecraft:context_dimension",
    "minecraft:context_entity_type",
    "minecraft:custom_model_data",
    "minecraft:display_context",
    "minecraft:local_time",
    "minecraft:main_hand",
    "minecraft:trim_material",
)

private val supportedRangeProperties: Set<String> = setOf(
    "minecraft:bundle/fullness",
    "minecraft:compass",
    "minecraft:cooldown",
    "minecraft:crossbow/pull",
    "minecraft:count",
    "minecraft:custom_model_data",
    "minecraft:damage",
    "minecraft:time",
    "minecraft:use_cycle",
    "minecraft:use_duration",
)

internal fun validateItemModelNode(node: JsonObject) {
    val type = node["type"]?.jsonPrimitive?.content
        ?: throw IllegalArgumentException("An item model node must declare a string 'type'.")
    require(type in supportedItemModelNodeTypes) { "Unsupported Minecraft 1.21.11 item model node type '$type'." }
    when (type) {
        "minecraft:model" -> {
            require(node["model"] is JsonPrimitive) { "minecraft:model requires 'model'." }
            (node["tints"] as? JsonArray)?.forEach { tint ->
                val tintType = tint.jsonObject.requireString("type")
                require(tintType in supportedTintTypes) { "Unsupported Minecraft 1.21.11 tint type '$tintType'." }
            }
        }
        "minecraft:special" -> {
            require(node["base"] is JsonPrimitive) { "minecraft:special requires 'base'." }
            val special = node["model"] as? JsonObject
                ?: throw IllegalArgumentException("minecraft:special requires a special-model object.")
            val specialType = special.requireString("type")
            require(specialType in supportedSpecialModelTypes) {
                "Unsupported Minecraft 1.21.11 special model type '$specialType'."
            }
        }
        "minecraft:composite" -> node.requireArray("models").forEach { validateItemModelNode(it.jsonObject) }
        "minecraft:condition" -> {
            val property = node.requireString("property")
            require(property in supportedBooleanProperties) {
                "Unsupported Minecraft 1.21.11 boolean item-model property '$property'."
            }
            validateItemModelNode(node.requireObject("on_true"))
            validateItemModelNode(node.requireObject("on_false"))
        }
        "minecraft:select" -> {
            val property = node.requireString("property")
            require(property in supportedSelectProperties) {
                "Unsupported Minecraft 1.21.11 select item-model property '$property'."
            }
            node.requireArray("cases").forEach { validateItemModelNode(it.jsonObject.requireObject("model")) }
            (node["fallback"] as? JsonObject)?.let(::validateItemModelNode)
        }
        "minecraft:range_dispatch" -> {
            val property = node.requireString("property")
            require(property in supportedRangeProperties) {
                "Unsupported Minecraft 1.21.11 range item-model property '$property'."
            }
            node.requireArray("entries").forEach { validateItemModelNode(it.jsonObject.requireObject("model")) }
            (node["fallback"] as? JsonObject)?.let(::validateItemModelNode)
        }
    }
}

private fun JsonObject.requireArray(name: String): JsonArray = this[name] as? JsonArray
    ?: throw IllegalArgumentException("Item model node requires array '$name'.")

private fun JsonObject.requireObject(name: String): JsonObject = this[name] as? JsonObject
    ?: throw IllegalArgumentException("Item model node requires object '$name'.")

private fun JsonObject.requireString(name: String): String {
    val primitive = this[name] as? JsonPrimitive
        ?: throw IllegalArgumentException("Item model object requires string '$name'.")
    require(primitive.isString) { "Item model object requires string '$name'." }
    return primitive.content
}

private fun JsonObject.resolveLocalResourceRefs(): JsonObject = JsonObject(mapValues { (key, value) ->
    when (value) {
        is JsonObject -> value.resolveLocalResourceRefs()
        is JsonArray -> JsonArray(value.map { element ->
            if (element is JsonObject) element.resolveLocalResourceRefs() else element
        })
        is JsonPrimitive -> value.resolveLocalResourceRef(key)
    }
})

private fun JsonPrimitive.resolveLocalResourceRef(key: String): JsonPrimitive {
    if (!isString || "://" !in content) return this
    val resolved = when {
        content.endsWith(".model.json") && key in setOf("model", "base") ->
            ref<MinecraftModel>(content).toMinecraftModelLocator()
        content.endsWith(".png") && key == "texture" -> ref<Texture2D>(content).toMinecraftLocator()
        else -> return this
    }
    return JsonPrimitive(resolved)
}
