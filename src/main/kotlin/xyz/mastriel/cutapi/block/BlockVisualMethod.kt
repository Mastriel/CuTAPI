@file:OptIn(xyz.mastriel.cutapi.nms.UsesNMS::class)

package xyz.mastriel.cutapi.block

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier as MinecraftIdentifier
import org.bukkit.block.data.BlockData
import org.bukkit.Material
import org.bukkit.entity.Display.Brightness
import org.bukkit.craftbukkit.block.data.CraftBlockData
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import xyz.mastriel.cutapi.nms.UsesNMS
import xyz.mastriel.cutapi.registry.Identifier

/** Selects the explicit vanilla-client representation of a native custom block state. */
public sealed interface BlockVisualMethod {
    /**
     * Reuses an existing vanilla appearance and needs no generated blockstate resource. The carrier
     * also defines the client's collision, outline, occlusion, particles, prediction, and F3 identity,
     * so it should have the same shape as the authoritative native state. It cannot provide a custom
     * texture or model.
     */
    public data class Vanilla(val state: BlockData) : BlockVisualMethod

    /**
     * Uses one of 1,149 resource-pack-backed note-block slots. It is intended for dense full cubes;
     * clients predict a note-block shape, show a note block without the pack, and conflict with other
     * owners of the note-block blockstate resource.
     */
    public data object NoteBlock : BlockVisualMethod

    /**
     * Uses one of 189 resource-pack-backed huge-mushroom slots. It is intended for dense full cubes;
     * clients predict a full-cube shape, vanilla huge mushrooms are canonicalized, and exact connected
     * mushroom faces are sacrificed. Other owners of the mushroom blockstate resources conflict.
     */
    public data object Mushroom : BlockVisualMethod

    /**
     * Keeps a vanilla carrier in the chunk and sends one packet-only ItemDisplay per visible block and
     * player. This supports transforms and animation without finite carrier slots, but is unsuitable for
     * dense terrain. Collision remains on the carrier; generated item models provide breaking visuals.
     * The default [ItemDisplayTransform.NONE] renders model coordinates at their authored block scale
     * instead of applying inherited item transforms such as `block/block`'s half-sized `fixed` transform.
     */
    public data class DisplayEntity(
        val carrier: BlockData,
        val transform: ItemDisplayTransform = ItemDisplayTransform.NONE,
        /** Optional fixed light levels. Null samples the light incident on the carrier. */
        val brightness: Brightness? = null,
    ) : BlockVisualMethod
}

/**
 * Creates carrier data without Bukkit's server singleton. In deferred definitions, evaluate this
 * through `visual { ... }` or `visuals { ... }`; their producers run during the STARTUP commit after
 * vanilla block registration has completed.
 */
@UsesNMS
public fun blockVisualData(material: Material): BlockData =
    CraftBlockData.fromData(
        checkNotNull(
            BuiltInRegistries.BLOCK.getOptional(
                MinecraftIdentifier.fromNamespaceAndPath(material.key.namespace, material.key.key),
            ).orElse(null),
        ) { "Minecraft block ${material.key} is not registered during STARTUP." }.defaultBlockState(),
    )

/** Packet-only display payload paired with a projected carrier block state. */
public data class VirtualItemDisplayDefinition(
    val item: ItemStack,
    val transform: ItemDisplayTransform,
    /** Clockwise rotation around the block's vertical axis. */
    val yRotationDegrees: Int = 0,
    /** Optional fixed light levels. Null samples the light incident on the carrier. */
    val brightness: Brightness? = null,
    /** Generated item models containing the vanilla break overlay for stages 0 through 9. */
    val breakingItemModels: List<NamespacedKey> = emptyList(),
)

/** The single client representation resolved for one authoritative native state. */
@UsesNMS
public data class ResolvedBlockVisual(
    val clientBlockState: BlockState,
    val displayEntity: VirtualItemDisplayDefinition? = null,
)

/** Stable definition/state key used by both packet projection and pack generation. */
public data class BlockVisualKey(
    val definitionId: Identifier,
    val canonicalStateValues: String,
) : Comparable<BlockVisualKey> {
    override fun compareTo(other: BlockVisualKey): Int =
        compareValuesBy(this, other, { it.definitionId.toString() }, { it.canonicalStateValues })
}

public enum class BlockVisualCarrier(public val capacity: Int) {
    NoteBlock(1_149),
    Mushroom(189),
}

public sealed interface BlockVisualAllocation {
    public data class Vanilla(val state: BlockData) : BlockVisualAllocation

    public data class Carrier(
        val carrier: BlockVisualCarrier,
        val slot: Int,
    ) : BlockVisualAllocation

    public data class DisplayEntity(
        val carrier: BlockData,
        val transform: ItemDisplayTransform,
        val brightness: Brightness? = null,
    ) : BlockVisualAllocation
}

public data class BlockVisualAllocationRequest(
    val key: BlockVisualKey,
    val method: BlockVisualMethod,
    /** States with the same non-null visual identity intentionally share one finite carrier slot. */
    val visualIdentity: String? = null,
)

public data class BlockVisualAllocationManifest(
    val minecraftVersion: String,
    val resourcePackHash: String,
    val capacity: Map<BlockVisualCarrier, Int>,
    val assignments: Map<BlockVisualKey, BlockVisualAllocation>,
)

/** Pure deterministic allocator; native-state conversion and resource writing are adapters. */
public object BlockVisualAllocator {
    public fun allocate(
        requests: Collection<BlockVisualAllocationRequest>,
        minecraftVersion: String,
        resourcePackHash: String,
    ): BlockVisualAllocationManifest {
        val byKey = requests.groupBy(BlockVisualAllocationRequest::key)
        val duplicate = byKey.entries.firstOrNull { it.value.size != 1 }
        require(duplicate == null) { "Duplicate visual assignment for ${duplicate?.key}." }

        val finiteAssignments = mutableMapOf<Pair<BlockVisualCarrier, String>, Int>()
        val nextSlot = mutableMapOf(
            BlockVisualCarrier.NoteBlock to 0,
            BlockVisualCarrier.Mushroom to 0,
        )
        val assignments = linkedMapOf<BlockVisualKey, BlockVisualAllocation>()

        for (request in requests.sortedBy(BlockVisualAllocationRequest::key)) {
            val allocation = when (val method = request.method) {
                is BlockVisualMethod.Vanilla -> BlockVisualAllocation.Vanilla(method.state)
                is BlockVisualMethod.DisplayEntity -> BlockVisualAllocation.DisplayEntity(
                    method.carrier,
                    method.transform,
                    method.brightness,
                )
                BlockVisualMethod.NoteBlock -> allocateFinite(
                    request,
                    BlockVisualCarrier.NoteBlock,
                    finiteAssignments,
                    nextSlot,
                )
                BlockVisualMethod.Mushroom -> allocateFinite(
                    request,
                    BlockVisualCarrier.Mushroom,
                    finiteAssignments,
                    nextSlot,
                )
            }
            assignments[request.key] = allocation
        }

        return BlockVisualAllocationManifest(
            minecraftVersion = minecraftVersion,
            resourcePackHash = resourcePackHash,
            capacity = BlockVisualCarrier.entries.associateWith(BlockVisualCarrier::capacity),
            assignments = assignments,
        )
    }

    private fun allocateFinite(
        request: BlockVisualAllocationRequest,
        carrier: BlockVisualCarrier,
        shared: MutableMap<Pair<BlockVisualCarrier, String>, Int>,
        nextSlot: MutableMap<BlockVisualCarrier, Int>,
    ): BlockVisualAllocation.Carrier {
        val identity = request.visualIdentity ?: request.key.toString()
        val poolKey = carrier to identity
        val slot = shared[poolKey] ?: nextSlot.getValue(carrier).also { candidate ->
            check(candidate < carrier.capacity) {
                "${carrier.name} visual capacity exhausted at ${carrier.capacity} assignments."
            }
            shared[poolKey] = candidate
            nextSlot[carrier] = candidate + 1
        }
        return BlockVisualAllocation.Carrier(carrier, slot)
    }
}
