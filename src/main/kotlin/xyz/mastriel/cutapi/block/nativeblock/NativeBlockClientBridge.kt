@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)
@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi.block.nativeblock

import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.craftbukkit.CraftChunk
import org.bukkit.craftbukkit.block.data.CraftBlockData
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.block.BlockModel
import xyz.mastriel.cutapi.block.BlockVisualAllocation
import xyz.mastriel.cutapi.block.BlockVisualAllocationManifest
import xyz.mastriel.cutapi.block.BlockVisualAllocationRequest
import xyz.mastriel.cutapi.block.BlockVisualAllocator
import xyz.mastriel.cutapi.block.BlockVisualCarrier
import xyz.mastriel.cutapi.block.BlockVisualKey
import xyz.mastriel.cutapi.block.BlockVisualMethod
import xyz.mastriel.cutapi.block.CustomBlockState
import xyz.mastriel.cutapi.block.CustomTile
import xyz.mastriel.cutapi.block.ResolvedBlockVisual
import xyz.mastriel.cutapi.block.VirtualItemDisplayDefinition
import xyz.mastriel.cutapi.item.nativeitem.NativeItemNetworkContext
import xyz.mastriel.cutapi.item.nativeitem.NetworkDirection
import java.util.IdentityHashMap

public object NativeBlockClientBridge {
    private val visuals: MutableMap<BlockState, ResolvedBlockVisual> = IdentityHashMap()
    private val canonicalVanillaStates: MutableMap<BlockState, BlockState> = IdentityHashMap()
    private val keysByClientState: MutableMap<BlockState, MutableList<BlockVisualKey>> = IdentityHashMap()

    public var manifest: BlockVisualAllocationManifest? = null
        private set

    internal fun bind(definitions: Collection<CustomTile<*>>, minecraftVersion: String, resourcePackHash: String) {
        check(visuals.isEmpty()) { "Native block visuals were bound more than once." }
        val requests = definitions.flatMap { definition ->
            definition.descriptor.states.permutations.map { state ->
                val method = definition.descriptor.visualMethod(state)
                validateModel(definition, state, method)
                BlockVisualAllocationRequest(
                    key = BlockVisualKey(definition.id, state.canonicalValues()),
                    method = method,
                    visualIdentity = definition.descriptor.visualIdentity(state),
                )
            }
        }
        val allocationManifest = BlockVisualAllocator.allocate(requests, minecraftVersion, resourcePackHash)
        val noteStates = customNoteStates()
        val mushroomStates = customMushroomStates()

        for (definition in definitions) {
            for (customState in definition.descriptor.states.permutations) {
                val key = BlockVisualKey(definition.id, customState.canonicalValues())
                val allocation = allocationManifest.assignments.getValue(key)
                val method = definition.descriptor.visualMethod(customState)
                val resolved = resolve(definition, customState, allocation, method, noteStates, mushroomStates)
                val nativeState = NativeBlockTypes.state(definition, customState)
                check(visuals.put(nativeState, resolved) == null) { "Duplicate native visual for $key." }
                if (allocation is BlockVisualAllocation.Carrier) {
                    keysByClientState.getOrPut(resolved.clientBlockState, ::mutableListOf) += key
                }
            }
        }

        canonicalizeVanillaCarriers(allocationManifest)
        manifest = allocationManifest
    }

    @JvmStatic
    public fun encodeState(value: Any?): Any? {
        val state = value as? BlockState ?: return value
        val call = NativeItemNetworkContext.current() ?: return state
        if (call.direction != NetworkDirection.Clientbound) return state
        return project(state)
    }

    @JvmStatic
    public fun projectValue(value: Any?): Any? =
        (value as? BlockState)?.let(::project) ?: value

    public fun project(state: BlockState): BlockState =
        visuals[state]?.clientBlockState ?: canonicalVanillaStates[state] ?: state

    public fun resolve(state: BlockState): ResolvedBlockVisual? = visuals[state]

    /**
     * Exercises Paper's exact-size chunk buffer against any custom palettes already loaded from disk.
     * This turns a projection-size instrumentation regression into a startup failure instead of a
     * world-tick crash when the first player begins tracking the affected chunk.
     */
    internal fun verifyLoadedChunkSerialization(): Int {
        var verified = 0
        Bukkit.getWorlds().asSequence()
            .flatMap { it.loadedChunks.asSequence() }
            .map { (it as CraftChunk).getHandle(ChunkStatus.FULL) as LevelChunk }
            .filter { chunk ->
                chunk.sections.any { section ->
                    section.maybeHas { state -> resolve(state) != null }
                }
            }
            .forEach { chunk ->
                verifyChunkProjection(chunk)
                verified++
            }
        return verified
    }

    @Suppress("DEPRECATION")
    private fun verifyChunkProjection(chunk: LevelChunk) {
        val buffer = ClientboundLevelChunkPacketData(chunk).readBuffer
        chunk.sections.forEachIndexed { sectionIndex, authoritativeSection ->
            val decodedSection = authoritativeSection.copy().apply { read(buffer) }
            for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                val authoritative = authoritativeSection.getBlockState(x, y, z)
                val expected = project(authoritative)
                val decoded = decodedSection.getBlockState(x, y, z)
                check(decoded === expected) {
                    "Chunk ${chunk.pos} section ${chunk.getSectionYFromSectionIndex(sectionIndex)} " +
                        "decoded block ($x,$y,$z) as $decoded; expected projected state $expected " +
                        "for authoritative state $authoritative."
                }
            }
        }
        check(!buffer.isReadable) {
            "Chunk ${chunk.pos} projection left ${buffer.readableBytes()} unread section byte(s)."
        }
    }

    /** Expands one authoritative custom block ID to every vanilla carrier used by its states. */
    internal fun projectTagBlock(block: net.minecraft.world.level.block.Block): List<net.minecraft.world.level.block.Block> {
        val definition = NativeBlockTypes.definition(block.defaultBlockState()) ?: return listOf(block)
        return definition.descriptor.states.permutations
            .map { state -> project(NativeBlockTypes.state(definition, state)).block }
            .distinct()
    }

    internal fun finiteAssignments(): Map<BlockState, List<BlockVisualKey>> =
        keysByClientState.mapValues { it.value.toList() }

    private fun resolve(
        definition: CustomTile<*>,
        customState: CustomBlockState,
        allocation: BlockVisualAllocation,
        method: BlockVisualMethod,
        noteStates: List<BlockState>,
        mushroomStates: List<BlockState>,
    ): ResolvedBlockVisual = when (allocation) {
        is BlockVisualAllocation.Vanilla -> ResolvedBlockVisual(
            definition.descriptor.orientCarrier(allocation.state.nmsState(), customState),
        )
        is BlockVisualAllocation.Carrier -> {
            val state = when (allocation.carrier) {
                BlockVisualCarrier.NoteBlock -> noteStates[allocation.slot]
                BlockVisualCarrier.Mushroom -> mushroomStates[allocation.slot]
            }
            ResolvedBlockVisual(definition.descriptor.orientCarrier(state, customState))
        }
        is BlockVisualAllocation.DisplayEntity -> {
            val displayMethod = method as BlockVisualMethod.DisplayEntity
            val item = ItemStack(Material.PAPER).apply {
                itemMeta = itemMeta.apply {
                    val location = displayItemModelLocation(definition, customState)
                    itemModel = NamespacedKey(location.first, location.second)
                }
            }
            ResolvedBlockVisual(
                definition.descriptor.orientCarrier(allocation.carrier.nmsState(), customState),
                VirtualItemDisplayDefinition(
                    item,
                    displayMethod.transform,
                    definition.descriptor.orientation?.modelYRotation(customState) ?: 0,
                ),
            )
        }
    }

    private fun validateModel(
        definition: CustomTile<*>,
        state: CustomBlockState,
        method: BlockVisualMethod,
    ) {
        val model = definition.descriptor.model(state)
        when (method) {
            is BlockVisualMethod.Vanilla -> require(model == null) {
                "Vanilla visual ${definition.id}[${state.canonicalValues()}] cannot define a custom model."
            }
            BlockVisualMethod.NoteBlock, BlockVisualMethod.Mushroom -> require(model != null) {
                "${method::class.simpleName} visual ${definition.id}[${state.canonicalValues()}] requires a block model."
            }
            is BlockVisualMethod.DisplayEntity -> require(model != null) {
                "DisplayEntity visual ${definition.id}[${state.canonicalValues()}] requires a block model."
            }
        }
    }

    private fun customNoteStates(): List<BlockState> {
        val states = Blocks.NOTE_BLOCK.stateDefinition.possibleStates
        check(states.size == 1_150) { "Minecraft note-block capacity changed from 1,150 to ${states.size}." }
        return states.filterNot { it === Blocks.NOTE_BLOCK.defaultBlockState() }
    }

    private fun customMushroomStates(): List<BlockState> {
        val carriers = listOf(Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM)
        val states = carriers.flatMap { block ->
            check(block.stateDefinition.possibleStates.size == 64) {
                "Minecraft mushroom carrier ${block.descriptionId} no longer has 64 states."
            }
            block.stateDefinition.possibleStates.filterNot { it === block.defaultBlockState() }
        }
        check(states.size == 189) { "Minecraft mushroom capacity changed from 189 to ${states.size}." }
        return states
    }

    private fun canonicalizeVanillaCarriers(manifest: BlockVisualAllocationManifest) {
        val carriers = manifest.assignments.values.filterIsInstance<BlockVisualAllocation.Carrier>()
            .map(BlockVisualAllocation.Carrier::carrier)
            .toSet()
        if (BlockVisualCarrier.NoteBlock in carriers) {
            Blocks.NOTE_BLOCK.stateDefinition.possibleStates.forEach {
                canonicalVanillaStates[it] = Blocks.NOTE_BLOCK.defaultBlockState()
            }
        }
        if (BlockVisualCarrier.Mushroom in carriers) {
            listOf(Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM).forEach { block ->
                block.stateDefinition.possibleStates.forEach { canonicalVanillaStates[it] = block.defaultBlockState() }
            }
        }
    }
}

internal fun displayItemModelLocation(
    definition: CustomTile<*>,
    state: CustomBlockState,
): Pair<String, String> {
    val stateName = blockStateModelPath(state)
    return definition.id.namespace to "cutapi/block_display/${definition.id.key}/$stateName"
}

internal fun blockStateModelPath(state: CustomBlockState): String = state.canonicalValues()
    .ifBlank { "default" }
    .replace(",", "__")
    .replace("=", "-")

internal fun org.bukkit.block.data.BlockData.nmsState(): BlockState =
    (this as? CraftBlockData)?.state
        ?: error("Block visual $this was not created by CraftBukkit.")
