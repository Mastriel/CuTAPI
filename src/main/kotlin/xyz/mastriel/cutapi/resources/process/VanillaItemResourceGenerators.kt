package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.*

/** Options accepted by `cutapi:texture_model`; model fields are flattened beside generator controls. */
public data class TextureModelGeneratorOptions(
    public val output: ResourceRef<MinecraftModel>? = null,
    public val parent: String? = "minecraft:item/generated",
    public val textures: Map<String, String> = emptyMap(),
    public val elements: List<Variant> = emptyList(),
    public val ambientOcclusion: Boolean? = null,
    public val guiLight: String? = null,
    public val display: Map<String, Variant> = emptyMap(),
    public val generate: List<GenerateBlock<*>> = emptyList(),
) {
    public companion object : Schema<TextureModelGeneratorOptions> by schema(id("cutapi:texture_model"), {
        strict = false
        property(TextureModelGeneratorOptions::output, VariantSerializer.ResourceRef<MinecraftModel>().nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(TextureModelGeneratorOptions::parent, VariantSerializer.String.nullable()) {
            optional(omitDefaults = true) { "minecraft:item/generated" }
        }
        property(TextureModelGeneratorOptions::textures, VariantSerializer.MapOf(VariantSerializer.String)) {
            optional(omitDefaults = true) { emptyMap() }
        }
        property(TextureModelGeneratorOptions::elements, VariantSerializer.List) {
            optional(omitDefaults = true) { emptyList() }
        }
        property(
            TextureModelGeneratorOptions::ambientOcclusion,
            VariantSerializer.Boolean.nullable(),
            name = "ambientocclusion",
        ) {
            optional(omitDefaults = true) { null }
        }
        property(TextureModelGeneratorOptions::guiLight, VariantSerializer.String.nullable(), name = "gui_light") {
            optional(omitDefaults = true) { null }
        }
        property(TextureModelGeneratorOptions::display, VariantSerializer.Map) {
            optional(omitDefaults = true) { emptyMap() }
        }
        property(TextureModelGeneratorOptions::generate, VariantSerializer.ListOf(GenerateBlock)) {
            optional(omitDefaults = true) { emptyList() }
        }
    })
}

/** Options accepted by `cutapi:item_model`; item-definition fields are flattened beside generator controls. */
public data class ItemModelGeneratorOptions(
    public val output: ResourceRef<ItemModel>? = null,
    public val model: Map<String, Variant>? = null,
    public val handAnimationOnSwap: Boolean = true,
    public val oversizedInGui: Boolean = false,
    public val swapAnimationScale: Float = 1f,
    public val generate: List<GenerateBlock<*>> = emptyList(),
) {
    public companion object : Schema<ItemModelGeneratorOptions> by schema(id("cutapi:item_model"), {
        strict = false
        property(ItemModelGeneratorOptions::output, VariantSerializer.ResourceRef<ItemModel>().nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(ItemModelGeneratorOptions::model, VariantSerializer.Map.nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(
            ItemModelGeneratorOptions::handAnimationOnSwap,
            VariantSerializer.Boolean,
            name = "hand_animation_on_swap",
        ) {
            optional(omitDefaults = true) { true }
        }
        property(ItemModelGeneratorOptions::oversizedInGui, VariantSerializer.Boolean, name = "oversized_in_gui") {
            optional(omitDefaults = true) { false }
        }
        property(ItemModelGeneratorOptions::swapAnimationScale, VariantSerializer.Float, name = "swap_animation_scale") {
            optional(omitDefaults = true) { 1f }
        }
        property(ItemModelGeneratorOptions::generate, VariantSerializer.ListOf(GenerateBlock)) {
            optional(omitDefaults = true) { emptyList() }
        }
    })
}

public val TextureModelGenerator: ResourceGenerator<TextureModelGeneratorOptions> =
    resourceGenerator<Texture2D, TextureModelGeneratorOptions>(TextureModelGeneratorOptions) {
        val outputRef = resolveGeneratorOutput(
            source = resource.ref,
            explicitOutput = options.output,
            subId = generateBlock.subId,
            expectedOutputExtension = "model.json",
            defaultOutput = resource.ref.generatedMinecraftModelRef(generateBlock.subId),
        )
        val defaults = Variant.Map(
            mapOf(
                "parent" to Variant.String("minecraft:item/generated"),
                "textures" to Variant.Map(mapOf("layer0" to Variant.String(resource.ref.toString()))),
            )
        )
        val overlay = generateBlock.authoredOptions.without(*generatorControlFields)
        val merged = mergeVanillaResourceData(defaults, overlay).requireMap()
        val json = merged.toJsonElement().jsonObject
        CuTApiJson.decodeFromJsonElement<MinecraftModelData>(json)
        val metadata = MinecraftModel.Metadata().also { it.generateBlocks = options.generate }
        register(MinecraftModel(outputRef, json, metadata))
    }

public val ItemModelGenerator: ResourceGenerator<ItemModelGeneratorOptions> =
    resourceGenerator<MinecraftModel, ItemModelGeneratorOptions>(ItemModelGeneratorOptions) {
        val outputRef = resolveGeneratorOutput(
            source = resource.ref,
            explicitOutput = options.output,
            subId = generateBlock.subId,
            expectedOutputExtension = "item_model.json",
            defaultOutput = resource.ref.generatedItemModelRef(generateBlock.subId),
        )
        val defaults = Variant.Map(
            mapOf(
                "model" to Variant.Map(
                    mapOf(
                        "type" to Variant.String("minecraft:model"),
                        "model" to Variant.String(resource.ref.toString()),
                    )
                )
            )
        )
        val overlay = generateBlock.authoredOptions.without(*generatorControlFields)
        val merged = mergeVanillaResourceData(defaults, overlay).requireMap()
        val json = merged.toJsonElement().jsonObject.withoutLogicalItemDefaults()
        CuTApiJson.decodeFromJsonElement<ItemModelData>(json)
        val metadata = ItemModel.Metadata().also { it.generateBlocks = options.generate }
        register(ItemModel(outputRef, json, metadata))
    }

public fun ResourceRef<Texture2D>.generatedMinecraftModelRef(subId: String? = null): ResourceRef<MinecraftModel> =
    withResourceExtension("png", "model.json", subId)

public fun ResourceRef<MinecraftModel>.generatedItemModelRef(subId: String? = null): ResourceRef<ItemModel> =
    withResourceExtension("model.json", "item_model.json", subId)

private val generatorControlFields: Array<String> = arrayOf("output", "generate")

private fun <T : Resource> resolveGeneratorOutput(
    source: ResourceRef<*>,
    explicitOutput: ResourceRef<T>?,
    subId: String?,
    expectedOutputExtension: String,
    defaultOutput: ResourceRef<T>,
): ResourceRef<T> {
    require(explicitOutput == null || subId == null) { "Generator options 'output' and 'subId' are mutually exclusive." }
    val output = explicitOutput ?: defaultOutput
    require(output.root == source.root) { "Generated resource $output must use the same resource root as $source." }
    require(output.extension == expectedOutputExtension) {
        "Generated resource $output must end in .$expectedOutputExtension."
    }
    return output
}

/** Recursively merges maps, replaces lists/scalars, and replaces discriminator-changing nodes wholesale. */
internal fun mergeVanillaResourceData(base: Variant, overlay: Variant): Variant {
    if (base !is Variant.Map || overlay !is Variant.Map) return overlay
    val baseType = (base["type"] as? Variant.String)?.value
    val overlayType = (overlay["type"] as? Variant.String)?.value
    if (overlayType != null && baseType != null && overlayType != baseType) return overlay

    val merged = base.value.toMutableMap()
    for ((key, value) in overlay) {
        merged[key] = merged[key]?.let { existing -> mergeVanillaResourceData(existing, value) } ?: value
    }
    return Variant.Map(merged)
}

private fun JsonObject.withoutLogicalItemDefaults(): JsonObject = JsonObject(toMutableMap().also { values ->
    if (values["hand_animation_on_swap"]?.jsonPrimitive?.booleanOrNull == true) {
        values.remove("hand_animation_on_swap")
    }
    if (values["oversized_in_gui"]?.jsonPrimitive?.booleanOrNull == false) {
        values.remove("oversized_in_gui")
    }
    if (values["swap_animation_scale"]?.jsonPrimitive?.floatOrNull == 1f) {
        values.remove("swap_animation_scale")
    }
})
