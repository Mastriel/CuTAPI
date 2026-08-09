@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block.nativeblock

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.block.BlockModel
import xyz.mastriel.cutapi.block.BlockTextures
import xyz.mastriel.cutapi.block.BlockVisualKey
import xyz.mastriel.cutapi.block.BlockVisualMethod
import xyz.mastriel.cutapi.block.CustomBlockState
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.resources.builtin.toMinecraftLocator
import xyz.mastriel.cutapi.resources.process.fixInvalidResourcePath
import xyz.mastriel.cutapi.utils.createAndWrite
import java.io.File

internal object NativeBlockResourcePackGenerator {
    fun generate(packRoot: File) {
        val manifest = NativeBlockClientBridge.manifest ?: return
        val definitions = CustomTile.getAllValues().associateBy { it.id }
        val assignments = NativeBlockClientBridge.finiteAssignments()
        val modelLocations = mutableMapOf<BlockVisualKey, String>()

        assignments.values.flatten().forEach { key ->
            val definition = definitions.getValue(key.definitionId)
            val state = definition.descriptor.states.permutations.single {
                it.canonicalValues() == key.canonicalStateValues
            }
            modelLocations[key] = writeOrResolveModel(packRoot, definition, state)
        }

        definitions.values.forEach { definition ->
            definition.descriptor.states.permutations.forEach { state ->
                if (definition.descriptor.visualMethod(state) !is BlockVisualMethod.DisplayEntity) return@forEach
                val modelLocation = writeOrResolveModel(packRoot, definition, state)
                writeDisplayItemModel(packRoot, definition, state, modelLocation)
            }
        }

        writeCarrierBlockstate(
            packRoot,
            "note_block",
            Blocks.NOTE_BLOCK,
            "minecraft:block/note_block",
            assignments,
            modelLocations,
        )
        writeCarrierBlockstate(
            packRoot,
            "red_mushroom_block",
            Blocks.RED_MUSHROOM_BLOCK,
            "minecraft:block/red_mushroom_block",
            assignments,
            modelLocations,
        )
        writeCarrierBlockstate(
            packRoot,
            "brown_mushroom_block",
            Blocks.BROWN_MUSHROOM_BLOCK,
            "minecraft:block/brown_mushroom_block",
            assignments,
            modelLocations,
        )
        writeCarrierBlockstate(
            packRoot,
            "mushroom_stem",
            Blocks.MUSHROOM_STEM,
            "minecraft:block/mushroom_stem",
            assignments,
            modelLocations,
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
        models: Map<BlockVisualKey, String>,
    ) {
        if (block.stateDefinition.possibleStates.none(assignments::containsKey)) return
        val file = File(packRoot, "assets/minecraft/blockstates/$fileName.json")
        check(!file.exists()) {
            "Resource-pack ownership conflict: ${file.relativeTo(packRoot)} is already supplied by another source."
        }
        val variants = linkedMapOf<String, kotlinx.serialization.json.JsonElement>()
        block.stateDefinition.possibleStates.forEach { state ->
            val keys = assignments[state].orEmpty()
            val model = if (keys.isEmpty()) {
                vanillaModel
            } else {
                val candidates = keys.map(models::getValue).toSet()
                check(candidates.size == 1) {
                    "States sharing carrier ${stateString(state)} resolve to incompatible models: $candidates."
                }
                candidates.single()
            }
            variants[stateString(state)] = JsonObject(mapOf("model" to JsonPrimitive(model)))
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
            val stateName = state.canonicalValues().ifBlank { "default" }.fixInvalidResourcePath()
            val path = "block/cutapi/${definition.id.key}/$stateName"
            val file = File(packRoot, "assets/${definition.id.namespace}/models/$path.json")
            val textures = model.textures.getAll()
            val json = JsonObject(
                mapOf(
                    "parent" to JsonPrimitive("minecraft:block/cube"),
                    "textures" to JsonObject(
                        mapOf(
                            "up" to JsonPrimitive(textures.up.toMinecraftLocator()),
                            "down" to JsonPrimitive(textures.down.toMinecraftLocator()),
                            "north" to JsonPrimitive(textures.north.toMinecraftLocator()),
                            "south" to JsonPrimitive(textures.south.toMinecraftLocator()),
                            "west" to JsonPrimitive(textures.west.toMinecraftLocator()),
                            "east" to JsonPrimitive(textures.east.toMinecraftLocator()),
                            "particle" to JsonPrimitive(textures.north.toMinecraftLocator()),
                        ),
                    ),
                ),
            )
            file.parentFile.mkdirs()
            file.createAndWrite(json.toString())
            "${definition.id.namespace}:$path"
        }
    }

    private fun stateString(state: BlockState): String = state.values.entries
        .sortedBy { it.key.name }
        .joinToString(",") { (property, value) -> "${property.name}=${propertyName(property, value)}" }

    @Suppress("UNCHECKED_CAST")
    private fun propertyName(property: Property<*>, value: Comparable<*>): String =
        (property as Property<Comparable<Any>>).getName(value as Comparable<Any>)
}

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
