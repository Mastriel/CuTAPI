package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import java.awt.image.*
import kotlin.math.*

/**
 * Configures horizontal progress frames generated from aligned empty and full images.
 *
 * The resource carrying this generator is the empty image. [fullTexture] supplies the completed
 * image. Both images must have identical dimensions and should use neutral grayscale pixels so
 * client-side custom-model-data colors can tint them. When [frameCount] is omitted, one transition
 * is generated for every source-image column.
 */
public data class HorizontalProgressTextureGeneratorOptions(
    public val fullTexture: ResourceRef<Texture2D>,
    public val frameCount: Int? = null,
    public val backgroundModel: ResourceRef<MinecraftModel>? = null,
) {
    public companion object : Schema<HorizontalProgressTextureGeneratorOptions> by schema(
        id("cutapi:horizontal_progress"),
        {
            property(
                HorizontalProgressTextureGeneratorOptions::fullTexture,
                VariantSerializer.ResourceRef<Texture2D>(),
            )
            property(
                HorizontalProgressTextureGeneratorOptions::frameCount,
                VariantSerializer.Int.nullable(),
            ) {
                optional(omitDefaults = true) { null }
            }
            property(
                HorizontalProgressTextureGeneratorOptions::backgroundModel,
                VariantSerializer.ResourceRef<MinecraftModel>().nullable(),
            ) {
                optional(omitDefaults = true) { null }
            }
        },
    )
}

/** Generates tintable frame models and one custom-model-data-driven client item definition. */
public val HorizontalProgressTextureGenerator: ResourceGenerator<HorizontalProgressTextureGeneratorOptions> =
    resourceGenerator<Texture2D, HorizontalProgressTextureGeneratorOptions>(
        optionsSchema = HorizontalProgressTextureGeneratorOptions,
        stage = ResourceGenerationStage.BeforeProcessors,
    ) {
        val emptyTexture = resource
        val fullTexture = options.fullTexture.getResource()
            ?: error(
                "Horizontal progress generator for ${emptyTexture.ref} cannot find full texture " +
                    "${options.fullTexture}."
            )

        require(emptyTexture.data.width == fullTexture.data.width) {
            "Horizontal progress textures must have the same width: ${emptyTexture.ref} is " +
                "${emptyTexture.data.width}px, but ${fullTexture.ref} is ${fullTexture.data.width}px."
        }
        require(emptyTexture.data.height == fullTexture.data.height) {
            "Horizontal progress textures must have the same height: ${emptyTexture.ref} is " +
                "${emptyTexture.data.height}px, but ${fullTexture.ref} is ${fullTexture.data.height}px."
        }

        val backgroundModel = options.backgroundModel?.let { backgroundRef ->
            backgroundRef.getResource()
                ?: error(
                    "Horizontal progress generator for ${emptyTexture.ref} cannot find background model " +
                        "$backgroundRef."
                )
        }

        createHorizontalProgressResources(
            emptyTexture = emptyTexture,
            fullTexture = fullTexture,
            generatedBase = ref,
            frameCount = options.frameCount ?: emptyTexture.data.width,
            backgroundModel = backgroundModel,
        ).forEach(register)
    }

/** Converts a normalized progress value to an inclusive frame in `0..frameCount`. */
public fun horizontalProgressFrameIndex(progress: Float, frameCount: Int): Int {
    require(frameCount > 0) { "Horizontal progress frameCount must be positive, found $frameCount." }
    if (progress.isNaN()) return 0
    return ceil(progress.coerceIn(0f, 1f) * frameCount).toInt().coerceIn(0, frameCount)
}

/** Returns the model generated for [frame] from a generator output base reference. */
public fun horizontalProgressFrameModelRef(
    generatedBase: ResourceRef<*>,
    frame: Int,
): ResourceRef<MinecraftModel> {
    require(frame >= 0) { "Horizontal progress frame cannot be negative, found $frame." }
    return ref(
        generatedBase.root,
        "${generatedBase.path(withExtension = false)}_model_$frame.model.json",
    )
}

/** Returns the model generated from [source] using [generationSubId] for [frame]. */
public fun horizontalProgressFrameModelRef(
    source: ResourceRef<Texture2D>,
    generationSubId: String,
    frame: Int,
): ResourceRef<MinecraftModel> = horizontalProgressFrameModelRef(source.generatedSubId(generationSubId), frame)

/** Returns the single client item definition generated for all progress frames. */
public fun horizontalProgressItemModelRef(
    source: ResourceRef<Texture2D>,
    generationSubId: String,
): ResourceRef<ItemModel> = horizontalProgressItemModelRef(source.generatedSubId(generationSubId))

internal fun horizontalProgressItemModelRef(generatedBase: ResourceRef<*>): ResourceRef<ItemModel> = ref(
    generatedBase.root,
    "${generatedBase.path(withExtension = false)}.item_model.json",
)

internal fun horizontalProgressEmptyTextureRef(
    generatedBase: ResourceRef<*>,
): ResourceRef<Texture2D> = ref(
    generatedBase.root,
    "${generatedBase.path(withExtension = false)}_empty.png",
)

internal fun horizontalProgressFillTextureRef(
    generatedBase: ResourceRef<*>,
    frame: Int,
): ResourceRef<Texture2D> {
    require(frame >= 0) { "Horizontal progress frame cannot be negative, found $frame." }
    return ref(
        generatedBase.root,
        "${generatedBase.path(withExtension = false)}_fill_$frame.png",
    )
}

internal fun horizontalProgressBackgroundModelRef(
    generatedBase: ResourceRef<*>,
    side: HorizontalProgressBackgroundSide,
): ResourceRef<MinecraftModel> = ref(
    generatedBase.root,
    "${generatedBase.path(withExtension = false)}_background_${side.name.lowercase()}.model.json",
)

internal enum class HorizontalProgressBackgroundSide {
    Left,
    Right,
}

/** Normalizes a tint mask so its brightest visible channel is white while preserving relative shading. */
internal fun normalizeProgressTintMask(source: BufferedImage): BufferedImage {
    var brightestChannel = 0
    for (y in 0 until source.height) {
        for (x in 0 until source.width) {
            val argb = source.getRGB(x, y)
            if (argb ushr 24 == 0) continue
            brightestChannel = max(
                brightestChannel,
                max((argb ushr 16) and 0xFF, max((argb ushr 8) and 0xFF, argb and 0xFF)),
            )
        }
    }

    if (brightestChannel == 0 || brightestChannel == 0xFF) {
        return BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB).also { result ->
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    result.setRGB(x, y, source.getRGB(x, y))
                }
            }
        }
    }

    val scale = 0xFF.toFloat() / brightestChannel
    return BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB).also { result ->
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getRGB(x, y)
                val alpha = argb ushr 24
                val red = (((argb ushr 16) and 0xFF) * scale).roundToInt().coerceIn(0, 0xFF)
                val green = (((argb ushr 8) and 0xFF) * scale).roundToInt().coerceIn(0, 0xFF)
                val blue = ((argb and 0xFF) * scale).roundToInt().coerceIn(0, 0xFF)
                result.setRGB(x, y, (alpha shl 24) or (red shl 16) or (green shl 8) or blue)
            }
        }
    }
}

internal fun composeHorizontalProgressFillFrame(
    fullMask: BufferedImage,
    frame: Int,
    frameCount: Int,
): BufferedImage {
    require(frameCount > 0) { "Horizontal progress frameCount must be positive, found $frameCount." }
    require(frame in 0..frameCount) { "Horizontal progress frame must be in 0..$frameCount, found $frame." }

    val filledWidth = ceil(frame.toDouble() / frameCount * fullMask.width).toInt().coerceIn(0, fullMask.width)
    return BufferedImage(fullMask.width, fullMask.height, BufferedImage.TYPE_INT_ARGB).also { result ->
        for (y in 0 until fullMask.height) {
            for (x in 0 until filledWidth) {
                result.setRGB(x, y, fullMask.getRGB(x, y))
            }
        }
    }
}

internal fun createHorizontalProgressResources(
    emptyTexture: Texture2D,
    fullTexture: Texture2D,
    generatedBase: ResourceRef<*>,
    frameCount: Int,
    backgroundModel: MinecraftModel? = null,
): List<Resource> {
    require(emptyTexture.data.width == fullTexture.data.width) {
        "Horizontal progress textures must have the same width: ${emptyTexture.ref} is " +
            "${emptyTexture.data.width}px, but ${fullTexture.ref} is ${fullTexture.data.width}px."
    }
    require(emptyTexture.data.height == fullTexture.data.height) {
        "Horizontal progress textures must have the same height: ${emptyTexture.ref} is " +
            "${emptyTexture.data.height}px, but ${fullTexture.ref} is ${fullTexture.data.height}px."
    }
    require(frameCount > 0) { "Horizontal progress frameCount must be positive, found $frameCount." }

    return buildList {
        val emptyMask = normalizeProgressTintMask(emptyTexture.data)
        val fullMask = normalizeProgressTintMask(fullTexture.data)
        val emptyTextureRef = horizontalProgressEmptyTextureRef(generatedBase)
        add(
            Texture2D(
                ref = emptyTextureRef,
                data = emptyMask,
                metadata = Texture2D.Metadata(fontSettings = FontSettings.Disabled),
            )
        )

        val itemModelBackgrounds: List<ResourceRef<MinecraftModel>> = if (backgroundModel == null) {
            emptyList()
        } else {
            val leftBackground = createHorizontalProgressBackgroundModel(
                backgroundModel = backgroundModel,
                ref = horizontalProgressBackgroundModelRef(
                    generatedBase,
                    HorizontalProgressBackgroundSide.Left,
                ),
                translationX = -GuiSlotPixelPitch,
            )
            val rightBackground = createHorizontalProgressBackgroundModel(
                backgroundModel = backgroundModel,
                ref = horizontalProgressBackgroundModelRef(
                    generatedBase,
                    HorizontalProgressBackgroundSide.Right,
                ),
                translationX = GuiSlotPixelPitch,
            )
            add(leftBackground)
            add(rightBackground)
            listOf(leftBackground.ref, backgroundModel.ref, rightBackground.ref)
        }

        for (frame in 0..frameCount) {
            val fillTextureRef = horizontalProgressFillTextureRef(generatedBase, frame)
            add(
                Texture2D(
                    ref = fillTextureRef,
                    data = composeHorizontalProgressFillFrame(
                        fullMask = fullMask,
                        frame = frame,
                        frameCount = frameCount,
                    ),
                    metadata = Texture2D.Metadata(
                        fontSettings = FontSettings.Disabled,
                    ),
                )
            )
            add(
                MinecraftModel(
                    ref = horizontalProgressFrameModelRef(generatedBase, frame),
                    data = horizontalProgressItemModel(
                        emptyTexture = emptyTextureRef,
                        fillTexture = fillTextureRef,
                        pixelWidth = emptyTexture.data.width,
                        pixelHeight = emptyTexture.data.height,
                        hasBackground = itemModelBackgrounds.isNotEmpty(),
                    ),
                    metadata = MinecraftModel.Metadata(),
                )
            )
        }

        val frameNodes = (0..frameCount).map { frame ->
            ModelItemModelNode(
                model = horizontalProgressFrameModelRef(generatedBase, frame),
                tints = listOf(
                    ItemModelTint.CustomModelData(
                        default = MinecraftColor.Packed(DefaultUnfilledColor),
                        index = 0,
                    ),
                    ItemModelTint.CustomModelData(
                        default = MinecraftColor.Packed(DefaultFilledColor),
                        index = 1,
                    ),
                ),
            )
        }
        val progressNode = RangeDispatchItemModelNode(
            property = ItemModelRangeProperty.CustomModelData(index = 0),
            entries = frameNodes.mapIndexed { frame, model ->
                RangeDispatchItemModelEntry(frame.toFloat(), model)
            },
            fallback = frameNodes.first(),
        )
        val rootNode = if (itemModelBackgrounds.isEmpty()) {
            progressNode
        } else {
            ConditionItemModelNode(
                property = ItemModelBooleanProperty.CustomModelData(index = 0),
                onTrue = CompositeItemModelNode(
                    itemModelBackgrounds.map(::ModelItemModelNode) + progressNode,
                ),
                onFalse = progressNode,
            )
        }
        add(
            ItemModel(
                ref = horizontalProgressItemModelRef(generatedBase),
                data = ItemModelData(
                    model = rootNode.toJson(),
                    handAnimationOnSwap = false,
                    oversizedInGui = true,
                ),
            )
        )
    }
}

private fun createHorizontalProgressBackgroundModel(
    backgroundModel: MinecraftModel,
    ref: ResourceRef<MinecraftModel>,
    translationX: Float,
): MinecraftModel {
    val originalGuiDisplay = backgroundModel.data.display[MinecraftModelDisplayType.Gui]
        ?: MinecraftModelDisplay()
    val originalTranslation = originalGuiDisplay.translation ?: VoxelVector(0f, 0f, 0f)
    val translatedGuiDisplay = originalGuiDisplay.copy(
        translation = VoxelVector(
            x = originalTranslation.x + translationX,
            y = originalTranslation.y,
            z = originalTranslation.z,
        )
    )

    return MinecraftModel(
        ref = ref,
        data = backgroundModel.data.copy(
            display = backgroundModel.data.display +
                (MinecraftModelDisplayType.Gui to translatedGuiDisplay),
        ),
        metadata = MinecraftModel.Metadata(),
    )
}

private fun horizontalProgressItemModel(
    emptyTexture: ResourceRef<Texture2D>,
    fillTexture: ResourceRef<Texture2D>,
    pixelWidth: Int,
    pixelHeight: Int,
    hasBackground: Boolean,
): MinecraftModelData = MinecraftModelData(
    parent = "minecraft:item/generated",
    textures = mapOf(
        "layer0" to emptyTexture.toMinecraftLocator(),
        "layer1" to fillTexture.toMinecraftLocator(),
    ),
    guiLight = "front",
    display = mapOf(
        MinecraftModelDisplayType.Gui to MinecraftModelDisplay(
            scale = VoxelVector(
                x = pixelWidth / 16f,
                y = pixelHeight / 16f,
                z = 1f,
            ),
            translation = if (hasBackground) {
                VoxelVector(
                    x = 0f,
                    y = 0f,
                    z = ProgressForegroundTranslationZ,
                )
            } else {
                null
            },
        )
    ),
)

private const val GuiSlotPixelPitch: Float = 18f
private const val ProgressForegroundTranslationZ: Float = 12f
private const val DefaultUnfilledColor: Int = 0x8B8B8B
private const val DefaultFilledColor: Int = 0xFFFFFF
