@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.nativeblock

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.block.BlockItemPolicy
import xyz.mastriel.cutapi.block.BlockModel
import xyz.mastriel.cutapi.block.BlockTextures
import xyz.mastriel.cutapi.block.BlockVisualKey
import xyz.mastriel.cutapi.block.BlockVisualMethod
import xyz.mastriel.cutapi.block.CustomBlockState
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.block.generatedBlockItemModelId
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.ref
import xyz.mastriel.cutapi.resources.minecraftNamespace
import xyz.mastriel.cutapi.resources.builtin.MinecraftModel
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.builtin.toMinecraftLocator
import xyz.mastriel.cutapi.resources.builtin.toMinecraftModelLocator
import xyz.mastriel.cutapi.resources.data.minecraft.AnimationMcMeta
import xyz.mastriel.cutapi.resources.minecraft.MinecraftAssets
import xyz.mastriel.cutapi.resources.process.fixInvalidResourcePath
import xyz.mastriel.cutapi.utils.createAndWrite
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal object NativeBlockResourcePackGenerator {
    private data class BlockModelVariant(
        val model: String,
        val yRotation: Int,
    )

    fun generate(packRoot: File) {
        val manifest = NativeBlockClientBridge.manifest ?: return
        val definitions = CustomTile.getAllValues().associateBy { it.id }
        val assignments = NativeBlockClientBridge.finiteAssignments()
        val modelVariants = mutableMapOf<BlockVisualKey, BlockModelVariant>()

        assignments.values.flatten().forEach { key ->
            val definition = definitions.getValue(key.definitionId)
            val state = definition.descriptor.states.permutations.single {
                it.canonicalValues() == key.canonicalStateValues
            }
            modelVariants[key] = BlockModelVariant(
                model = writeOrResolveModel(packRoot, definition, state),
                yRotation = definition.descriptor.orientation?.modelYRotation(state) ?: 0,
            )
        }

        definitions.values.forEach { definition ->
            definition.descriptor.states.permutations.forEach { state ->
                if (definition.descriptor.visualMethod(state) !is BlockVisualMethod.DisplayEntity) return@forEach
                val modelLocation = writeOrResolveModel(packRoot, definition, state)
                writeDisplayItemModel(packRoot, definition, state, modelLocation)
                writeDisplayBreakingModels(packRoot, definition, state, modelLocation)
            }
        }

        definitions.values.forEach { definition ->
            if (definition.descriptor.itemPolicy !is BlockItemPolicy.Generate) return@forEach
            writeGeneratedBlockItemModel(packRoot, definition)
        }

        writeCarrierBlockstate(
            packRoot,
            "note_block",
            Blocks.NOTE_BLOCK,
            "minecraft:block/note_block",
            assignments,
            modelVariants,
        )
        writeCarrierBlockstate(
            packRoot,
            "red_mushroom_block",
            Blocks.RED_MUSHROOM_BLOCK,
            "minecraft:block/red_mushroom_block",
            assignments,
            modelVariants,
        )
        writeCarrierBlockstate(
            packRoot,
            "brown_mushroom_block",
            Blocks.BROWN_MUSHROOM_BLOCK,
            "minecraft:block/brown_mushroom_block",
            assignments,
            modelVariants,
        )
        writeCarrierBlockstate(
            packRoot,
            "mushroom_stem",
            Blocks.MUSHROOM_STEM,
            "minecraft:block/mushroom_stem",
            assignments,
            modelVariants,
        )
        NativeBlockManifestWriter.write("pending")
    }

    private fun writeDisplayItemModel(
        packRoot: File,
        definition: CustomTile<*>,
        state: CustomBlockState,
        blockModelLocation: String,
    ) {
        val (namespace, path) = displayItemModelLocation(definition, state)
        writeItemModel(packRoot, namespace, path, blockModelLocation)
    }

    private fun writeGeneratedBlockItemModel(
        packRoot: File,
        definition: CustomTile<*>,
    ) {
        val state = definition.descriptor.states.defaultState
        val blockModelLocation = if (definition.descriptor.model(state) != null) {
            writeOrResolveModel(packRoot, definition, state)
        } else {
            vanillaItemModelLocation(definition, state)
        }
        val itemModelId = generatedBlockItemModelId(definition)
        writeItemModel(packRoot, itemModelId.namespace, itemModelId.key, blockModelLocation)
    }

    private fun writeDisplayBreakingModels(
        packRoot: File,
        definition: CustomTile<*>,
        state: CustomBlockState,
        baseModelLocation: String,
    ) {
        val textureRefs = resolveBlockModelTextures(definition, state)
        require(textureRefs.isNotEmpty()) {
            "DisplayEntity model for ${definition.id}[${state.canonicalValues()}] has no resolvable textures."
        }
        val statePath = blockStateModelPath(state)

        for (stage in 0..9) {
            val overlay = requireNotNull(
                ref<Texture2D>(MinecraftAssets, "block/destroy_stage_$stage.png").getResource(),
            ) { "Minecraft destroy-stage texture $stage is not loaded." }
            val crackModelPath = "block/cutapi/block_display/${definition.id.key}/$statePath/breaking_$stage"
            val crackedTextures = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()

            textureRefs.entries.sortedBy { it.key }.forEachIndexed { index, (textureKey, textureRef) ->
                val source = requireNotNull(textureRef.getResource()) {
                    "Block model texture $textureRef is not loaded."
                }
                val texturePath = "$crackModelPath/texture_$index"
                val textureFile = File(
                    packRoot,
                    "assets/${definition.id.namespace}/textures/$texturePath.png",
                )
                writeOwnedResource(
                    packRoot,
                    textureFile,
                    compositeBreakingTexture(source.data, overlay.data).toPngBytes(),
                )
                source.metadata.animation?.let { animation ->
                    writeOwnedResource(
                        packRoot,
                        File("${textureFile.path}.mcmeta"),
                        CuTAPI.json.encodeToString(AnimationMcMeta(animation)).toByteArray(),
                    )
                }
                crackedTextures[textureKey] = JsonPrimitive("${definition.id.namespace}:$texturePath")
            }

            val modelFile = File(
                packRoot,
                "assets/${definition.id.namespace}/models/$crackModelPath.json",
            )
            val modelJson = JsonObject(
                mapOf(
                    "parent" to JsonPrimitive(baseModelLocation),
                    "textures" to JsonObject(crackedTextures),
                ),
            )
            modelFile.parentFile.mkdirs()
            modelFile.createAndWrite(modelJson.toString())

            val (itemNamespace, itemPath) = displayBreakingItemModelLocation(definition, state, stage)
            writeItemModel(
                packRoot,
                itemNamespace,
                itemPath,
                "${definition.id.namespace}:$crackModelPath",
            )
        }
    }

    private fun resolveBlockModelTextures(
        definition: CustomTile<*>,
        state: CustomBlockState,
    ): Map<String, ResourceRef<Texture2D>> = when (val blockModel = requireNotNull(definition.descriptor.model(state))) {
        is BlockModel.Cubic -> blockModel.textures.getAll().let { textures ->
            linkedMapOf(
                "up" to textures.up,
                "down" to textures.down,
                "north" to textures.north,
                "south" to textures.south,
                "west" to textures.west,
                "east" to textures.east,
                "particle" to textures.north,
            )
        }
        is BlockModel.Model -> resolveModelTextures(
            requireNotNull(blockModel.model.getResource()) { "Block model ${blockModel.model} is not loaded." },
        )
    }

    private fun resolveModelTextures(model: MinecraftModel): Map<String, ResourceRef<Texture2D>> {
        val resources = CuTAPI.resourceManager.getAllResources()
        val modelsByLocator = resources.filterIsInstance<MinecraftModel>()
            .associateBy { it.ref.toMinecraftModelLocator() }
        val texturesByLocator = resources.filterIsInstance<Texture2D>()
            .associateBy { it.ref.toMinecraftLocator() }
        val declarations = linkedMapOf<String, String>()
        val visited = mutableSetOf<String>()

        fun collect(current: MinecraftModel) {
            val locator = current.ref.toMinecraftModelLocator()
            if (!visited.add(locator)) return
            val parent = current.json["parent"] as? JsonPrimitive
            if (parent?.isString == true) {
                modelsByLocator[parent.content.toModelLookupLocator()]?.let(::collect)
            }
            (current.json["textures"] as? JsonObject)?.forEach { (key, value) ->
                val primitive = value as? JsonPrimitive
                    ?: error("Model texture $key in ${current.ref} must be a string.")
                declarations[key] = primitive.content
            }
        }
        collect(model)

        fun resolveTexture(key: String, resolving: MutableSet<String>): ResourceRef<Texture2D>? {
            if (!resolving.add(key)) error("Cyclic model texture reference #$key in ${model.ref}.")
            val declaration = declarations[key] ?: return null
            val result = if (declaration.startsWith("#")) {
                resolveTexture(declaration.removePrefix("#"), resolving)
            } else {
                texturesByLocator[declaration.toTextureLookupLocator()]?.ref
            }
            resolving.remove(key)
            return result
        }

        return declarations.keys.associateWith { key ->
            requireNotNull(resolveTexture(key, mutableSetOf())) {
                "Model texture #$key in ${model.ref} does not resolve to a loaded texture."
            }
        }
    }

    private fun vanillaItemModelLocation(
        definition: CustomTile<*>,
        state: CustomBlockState,
    ): String {
        val visual = definition.descriptor.visualMethod(state)
        check(visual is BlockVisualMethod.Vanilla) {
            "Generated block item ${definition.id / "item"} has no model for its default state."
        }
        val material = visual.state.material
        val folder = if (material.isItem) "item" else "block"
        return "${material.key.namespace}:$folder/${material.key.key}"
    }

    private fun writeItemModel(
        packRoot: File,
        namespace: String,
        path: String,
        blockModelLocation: String,
    ) {
        val file = File(packRoot, "assets/$namespace/items/$path.json")
        check(!file.exists()) {
            "Resource-pack ownership conflict: ${file.relativeTo(packRoot)} is already supplied by another source."
        }
        val json = JsonObject(
            mapOf(
                "model" to JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("minecraft:model"),
                        "model" to JsonPrimitive(blockModelLocation),
                    ),
                ),
            ),
        )
        file.parentFile.mkdirs()
        file.createAndWrite(json.toString())
    }

    private fun writeCarrierBlockstate(
        packRoot: File,
        fileName: String,
        block: Block,
        vanillaModel: String,
        assignments: Map<BlockState, List<BlockVisualKey>>,
        models: Map<BlockVisualKey, BlockModelVariant>,
    ) {
        if (block.stateDefinition.possibleStates.none(assignments::containsKey)) return
        val file = File(packRoot, "assets/minecraft/blockstates/$fileName.json")
        check(!file.exists()) {
            "Resource-pack ownership conflict: ${file.relativeTo(packRoot)} is already supplied by another source."
        }
        val variants = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        block.stateDefinition.possibleStates.forEach { state ->
            val keys = assignments[state].orEmpty()
            val variant = if (keys.isEmpty()) {
                BlockModelVariant(vanillaModel, 0)
            } else {
                val candidates = keys.map(models::getValue).toSet()
                check(candidates.size == 1) {
                    "States sharing carrier ${stateString(state)} resolve to incompatible models: $candidates."
                }
                candidates.single()
            }
            variants[stateString(state)] = JsonObject(
                buildMap {
                    put("model", JsonPrimitive(variant.model))
                    if (variant.yRotation != 0) put("y", JsonPrimitive(variant.yRotation))
                },
            )
        }
        file.parentFile.mkdirs()
        file.createAndWrite(JsonObject(mapOf("variants" to JsonObject(variants))).toString())
    }

    private fun writeOrResolveModel(
        packRoot: File,
        definition: CustomTile<*>,
        state: CustomBlockState,
    ): String = when (val model = requireNotNull(definition.descriptor.model(state))) {
        is BlockModel.Model -> {
            val ref = model.model
            ref.toMinecraftModelLocator()
        }
        is BlockModel.Cubic -> {
            val stateName = blockStateModelPath(state)
            val path = "block/cutapi/${definition.id.key}/$stateName"
            val file = File(packRoot, "assets/${definition.id.namespace}/models/$path.json")
            val textures = model.textures.getAll()
            val json = JsonObject(
                mapOf(
                    "parent" to JsonPrimitive("minecraft:block/cube"),
                    "textures" to JsonObject(
                        mapOf(
                            "up" to JsonPrimitive(writeBlockTexture(packRoot, textures.up)),
                            "down" to JsonPrimitive(writeBlockTexture(packRoot, textures.down)),
                            "north" to JsonPrimitive(writeBlockTexture(packRoot, textures.north)),
                            "south" to JsonPrimitive(writeBlockTexture(packRoot, textures.south)),
                            "west" to JsonPrimitive(writeBlockTexture(packRoot, textures.west)),
                            "east" to JsonPrimitive(writeBlockTexture(packRoot, textures.east)),
                            "particle" to JsonPrimitive(writeBlockTexture(packRoot, textures.north)),
                        ),
                    ),
                ),
            )
            file.parentFile.mkdirs()
            file.createAndWrite(json.toString())
            "${definition.id.namespace}:$path"
        }
    }

    private fun writeBlockTexture(
        packRoot: File,
        textureRef: ResourceRef<Texture2D>,
    ): String {
        val texture = requireNotNull(textureRef.getResource()) {
            "Block texture $textureRef is not loaded."
        }
        val texturePath = textureRef.path(
            withExtension = false,
            withNamespaceAsFolder = false,
            fixInvalids = true,
        )
        val ownedPath = blockTextureResourcePath(texturePath)
        val textureFile = File(
            packRoot,
            "assets/${textureRef.minecraftNamespace}/textures/$ownedPath.png",
        )
        writeOwnedResource(packRoot, textureFile, texture.toBytes())

        texture.metadata.animation?.let { animation ->
            val metadataFile = File("${textureFile.path}.mcmeta")
            val metadata = CuTAPI.json.encodeToString(AnimationMcMeta(animation)).toByteArray()
            writeOwnedResource(packRoot, metadataFile, metadata)
        }
        return blockTextureModelLocation(textureRef.minecraftNamespace, texturePath)
    }

    private fun writeOwnedResource(
        packRoot: File,
        file: File,
        contents: ByteArray,
    ) {
        if (file.exists()) {
            check(file.readBytes().contentEquals(contents)) {
                "Resource-pack ownership conflict: ${file.relativeTo(packRoot)} has incompatible contents."
            }
            return
        }
        file.parentFile.mkdirs()
        file.writeBytes(contents)
    }

    private fun stateString(state: BlockState): String = state.values.entries
        .sortedBy { it.key.name }
        .joinToString(",") { (property, value) -> "${property.name}=${propertyName(property, value)}" }

    @Suppress("UNCHECKED_CAST")
    private fun propertyName(property: Property<*>, value: Comparable<*>): String =
        (property as Property<Comparable<Any>>).getName(value as Comparable<Any>)
}

private fun String.toModelLookupLocator(): String = when {
    "://" in this -> ref<MinecraftModel>(this).toMinecraftModelLocator()
    ":" in this -> this
    else -> "minecraft:$this"
}

private fun String.toTextureLookupLocator(): String = when {
    "://" in this -> ref<Texture2D>(this).toMinecraftLocator()
    ":" in this -> this
    else -> "minecraft:$this"
}

internal fun compositeBreakingTexture(source: BufferedImage, overlay: BufferedImage): BufferedImage {
    val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
    val frameSize = source.width.coerceAtLeast(1)
    for (y in 0 until source.height) {
        val frameStart = (y / frameSize) * frameSize
        val frameHeight = minOf(frameSize, source.height - frameStart)
        val overlayY = ((y - frameStart) * overlay.height / frameHeight).coerceAtMost(overlay.height - 1)
        for (x in 0 until source.width) {
            val sourceArgb = source.getRGB(x, y)
            val sourceAlpha = sourceArgb ushr 24
            if (sourceAlpha == 0) {
                result.setRGB(x, y, 0)
                continue
            }

            val overlayX = (x * overlay.width / source.width).coerceAtMost(overlay.width - 1)
            val overlayArgb = overlay.getRGB(overlayX, overlayY)
            val overlayAlpha = overlayArgb ushr 24
            // rendertype_crumbling discards alpha below 0.1. Vanilla's white background has
            // alpha 1/255, so it must not brighten the entire model.
            if (overlayAlpha / 255.0 < 0.1) {
                result.setRGB(x, y, sourceArgb)
                continue
            }

            // Vanilla uses DST_COLOR + SRC_COLOR, or 2 * base * overlay per channel. Preserve
            // both the dark crack centers and their bright edges, and keep the model's alpha.
            val red = blendBreakingChannel((sourceArgb ushr 16) and 0xFF, (overlayArgb ushr 16) and 0xFF)
            val green = blendBreakingChannel((sourceArgb ushr 8) and 0xFF, (overlayArgb ushr 8) and 0xFF)
            val blue = blendBreakingChannel(sourceArgb and 0xFF, overlayArgb and 0xFF)
            result.setRGB(x, y, sourceAlpha shl 24 or (red shl 16) or (green shl 8) or blue)
        }
    }
    return result
}

private fun blendBreakingChannel(base: Int, overlay: Int): Int =
    (2.0 * base * overlay / 255.0).roundToInt().coerceIn(0, 255)

private fun BufferedImage.toPngBytes(): ByteArray = ByteArrayOutputStream().use { output ->
    check(ImageIO.write(this, "png", output)) { "No PNG writer is available." }
    output.toByteArray()
}

internal fun blockTextureResourcePath(texturePath: String): String = "block/cutapi/$texturePath"

internal fun blockTextureModelLocation(namespace: String, texturePath: String): String =
    "$namespace:${blockTextureResourcePath(texturePath)}"

internal object NativeBlockManifestWriter {
    fun write(resourcePackHash: String) {
        val manifest = NativeBlockClientBridge.manifest ?: return
        val assignments = manifest.assignments.entries.associate { (key, value) ->
            "${key.definitionId}[${key.canonicalStateValues}]" to JsonPrimitive(value.toString())
        }
        val json = JsonObject(
            mapOf(
                "minecraftVersion" to JsonPrimitive(manifest.minecraftVersion),
                "resourcePackHash" to JsonPrimitive(resourcePackHash),
                "capacity" to JsonObject(manifest.capacity.mapKeys { it.key.name }.mapValues { JsonPrimitive(it.value) }),
                "assignments" to JsonObject(assignments),
            ),
        )
        Plugin.dataFolder.mkdirs()
        File(Plugin.dataFolder, "block-visual-allocations.json")
            .createAndWrite(json.toString())
    }
}
