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
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.data.minecraft.AnimationMcMeta
import xyz.mastriel.cutapi.resources.process.fixInvalidResourcePath
import xyz.mastriel.cutapi.utils.createAndWrite
import java.io.File

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
            "${ref.namespace}:${ref.path(withExtension = false, fixInvalids = true)}"
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
            "assets/${textureRef.namespace}/textures/$ownedPath.png",
        )
        writeOwnedResource(packRoot, textureFile, texture.toBytes())

        texture.metadata.animation?.let { animation ->
            val metadataFile = File("${textureFile.path}.mcmeta")
            val metadata = CuTAPI.json.encodeToString(AnimationMcMeta(animation)).toByteArray()
            writeOwnedResource(packRoot, metadataFile, metadata)
        }
        return blockTextureModelLocation(textureRef.namespace, texturePath)
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
