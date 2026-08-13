# Native custom blocks

CuTAPI registers each custom block under its own ID in Minecraft's native block registry. A custom
tile entity also receives a native `BlockEntityType`. The authoritative ID and typed state values are
therefore stored in chunk palettes instead of a carrier block's persistent data.

Definitions are restart-only, but they use the same `DeferredRegistry` API as custom items. A provider
must load in Paper's `STARTUP` phase and commit each block registry synchronously from `onEnable()`.
CuTAPI installs each committed batch immediately, then closes native registration after every STARTUP
plugin has enabled and before Paper loads worlds.

```kotlin
object CopperLampStates {
    val Lit = BlockStateType.Boolean("lit")
}

object ExampleTileEntities :
    DeferredRegistry<CustomTileEntity<*>> by CustomTileEntity.defer() {

    val CopperLamp by registerCustomTileEntity(
        id("example:copper_lamp"),
    ) {
        states {
            define(CopperLampStates.Lit) { false }
            define(HorizontalFacingState) { HorizontalFacingState.North }
        }

        orientation {
            horizontal()
            // Optional; this is the default.
            placementFacing(PlacementFacing.TowardsPlacer)
        }

        settings {
            hardness = 3.0f
            explosionResistance = 6.0f
            effectiveTools = setOf(ToolCategory.Pickaxe)
            minimumToolTier = ToolTier.Stone
            requiresCorrectToolForDrops = true
        }

        visual(BlockVisualMethod.NoteBlock)
        models { state ->
            val suffix = if (state[CopperLampStates.Lit]) "on" else "off"
            BlockModel.Model("example://block/copper_lamp_$suffix.json")
        }

        drops { context ->
            if (context.correctToolUsed) listOf(context.requirePlacementItem()) else emptyList()
        }
        experience { context -> if (context.correctToolUsed) 3 else 0 }
        attach(CopperLampData(energy = 0))
    }
}

class ExamplePlugin : JavaPlugin() {
    override fun onEnable() {
        CuTAPI.registerPlugin(this, "example")
        registerExampleSystems()
        ExampleTileEntities.commitToRegistry()

        // Deferred references, including ExampleTileEntities.CopperLamp, are available here.
    }
}
```

CuTAPI provides `HorizontalFacingState` for the built-in four-way `facing` property and `FacingState`
for its six-way counterpart. Their companion objects are block-state types, giving the concise
`define(HorizontalFacingState) { HorizontalFacingState.North }` form. Use the preferred reified
`BlockStateType.Enum<HorizontalFacingState>("custom_facing")` form when a definition needs a custom
property name. When only a `KClass` is available, use
`BlockStateType.Enum(HorizontalFacingState::class, "custom_facing")`.

The provider's `paper-plugin.yml` must put it after CuTAPI within the same STARTUP phase:

```yaml
name: Example
main: example.ExamplePlugin
api-version: '1.21'
load: STARTUP
dependencies:
  server:
    CuTAPI:
      load: BEFORE
      required: true
      join-classpath: true
```

`commitToRegistry()` materializes every producer once. The exact returned object is installed in the
Minecraft registry and later contributed to the priority-ordered CuTAPI identifier registry. Early
access through a delegated property or `Deferred.get()` materializes that same object without
installing a second instance. An uncommitted non-empty block registry, an asynchronous or POSTWORLD
commit, and any commit after the STARTUP boundary fail server startup.

### Migration from bootstrap submission

Replace bootstrap-time submission:

```kotlin
val Lamp = NativeBlockBootstrap.submit(customBlock(id("example:lamp")) { /* ... */ })
```

with a standard deferred registry and a STARTUP `onEnable()` commit:

```kotlin
object ExampleBlocks : DeferredRegistry<CustomBlock<*>> by CustomBlock.defer() {
    val Lamp by registerCustomBlock(id("example:lamp")) { /* ... */ }
}

override fun onEnable() {
    ExampleBlocks.commitToRegistry()
}
```

There is no compatibility bridge for bootstrap submission. Update the provider metadata, remove its
block `PluginBootstrap`, and restart the server.

Each `define` call declares the property and creates its default atomically. CuTAPI invokes default
producers independently and creates a real NMS state for every permutation. Every permutation must
resolve to one client visual. Set `visualIdentity { ... }` when multiple permutations intentionally
reuse the same finite carrier slot.

## Generated placement items

The default `BlockItemPolicy.Generate()` creates a native custom item backed authoritatively by
`minecraft:glistering_melon_slice`, but projects it to vanilla clients as
`minecraft:pig_spawn_egg`. The plain server backing has no use-on-block behavior. The spawn-egg
projection gives use-on-block interactions a hand swing without predicting a temporary block, while
CuTAPI strips its entity data and handles placement from the authoritative custom item. CuTAPI
assigns the item a deterministic model ID matching its generated item ID and writes that item model
to the resource pack:

```kotlin
itemPolicy = BlockItemPolicy.Generate {
    display {
        name = "Copper Lamp".colored
    }
}

// Generated item and item-model ID: example:copper_lamp/item
```

The item model references the block definition's model for its default state. Stateful blocks
therefore use the model returned by `models { ... }` for `states.defaultState`; later placed-state
changes do not alter an item stack already in an inventory. A `Vanilla` visual without a custom
`BlockModel` uses the projected material's vanilla item model, or its block model when that material
has no item form. Use `BlockItemPolicy.Item { ExistingItems.CopperLamp }` when an existing custom item
should place the block; existing-item policies keep that item's backing type and model.

## Horizontal orientation

An orientation declaration gives semantic meaning to an enum state instead of treating it as an
unrelated set of values. A horizontal enum must contain exactly `North`, `East`, `South`, and `West`.
Models are authored facing north; CuTAPI supplies the carrier blockstate or display-entity rotation
for the other three values.

```kotlin
orientation {
    horizontal()
}
```

Placement faces the block towards its placer by default. Use the other built-in policy to point in
the same direction as the placer:

```kotlin
orientation {
    horizontal()
    placementFacing(PlacementFacing.AwayFromPlacer)
}
```

`PlacementFacing` is a fun interface, so a definition can choose a horizontal `BlockFace` from the
placer and destination block:

```kotlin
orientation {
    horizontal()
    placementFacing(PlacementFacing { context ->
        if (context.block.getRelative(BlockFace.DOWN).type.isSolid) {
            context.placer.facing.oppositeFace
        } else {
            BlockFace.NORTH
        }
    })
}
```

The chosen value is installed in the authoritative native state before placement events observe the
block. Native structure rotation and mirroring update the same facing state. `Vanilla` carriers,
finite carrier models, `BlockModel.Cubic`, and packet-only displays use the corresponding horizontal
rotation automatically. Carrier collision and outline limitations still apply; note-block and
mushroom visuals remain full cubes on the client.

## Client visual methods

The server never sends the custom registry state ID to a vanilla client. One mapping drives both the
generated resource pack and packet projection for chunk palettes, individual and section block
updates, block particles, world events, carried states, and displays.

### Vanilla

```kotlin
visual {
    BlockVisualMethod.Vanilla(blockVisualData(Material.COPPER_BLOCK))
}
```

This projects directly to an existing vanilla state and needs no generated blockstate resource.
Definitions may freely reuse it, but it cannot provide a custom texture or model. The carrier controls
client collision, outline, occlusion, particles, prediction, and F3 identity, so its shape should match
the native state.

The producer form delays Bukkit `BlockData` resolution until the STARTUP `onEnable()` commit, when the
vanilla registry is ready.

### NoteBlock

```kotlin
visual(BlockVisualMethod.NoteBlock)
model(BlockModel.Model("example://block/copper_lamp.json"))
```

This allocates a generated note-block model and is efficient for dense full-cube terrain. Minecraft
1.21.11 exposes 1,150 note states; CuTAPI reserves one for real note blocks, leaving 1,149 global
custom slots. Every distinct visual permutation consumes a slot. It requires the generated pack,
conflicts with another owner of the note-block blockstate file, always predicts a full-cube shape, and
appears as a note block without the pack.

### Mushroom

```kotlin
visual(BlockVisualMethod.Mushroom)
model(BlockModel.Model("example://block/copper_lamp.json"))
```

This allocates red-mushroom, brown-mushroom, and mushroom-stem states. Their 192 states leave 189
global custom slots after one reserved state per carrier. CuTAPI canonicalizes real huge mushrooms to
those reserved states, so vanilla mushrooms lose exact connected inner/outer faces. Like note blocks,
this method is pack-dependent, full-cube-predicted, and conflicts with another owner of its carrier
blockstate files. Without the pack, it appears as an unusual mushroom state.

### DisplayEntity

```kotlin
visual {
    BlockVisualMethod.DisplayEntity(
        carrier = blockVisualData(Material.BARRIER),
        transform = ItemDisplay.ItemDisplayTransform.FIXED,
    )
}
model(BlockModel.Model("example://block/copper_lamp.json"))
```

This sends a packet-only `ItemDisplay` while keeping the chosen carrier in the chunk. It consumes no
note or mushroom capacity and supports transforms, interpolation, oversized models, and animation.
The block model is required and is connected to a deterministic generated item-model resource.
It requires one visible virtual entity per block per player, making it unsuitable for dense terrain.
Collision, selection, pathfinding, occlusion, and crack overlays belong to the carrier; lighting,
shadows, ambient occlusion, and culling differ from chunk models. Without the pack, its item model is
missing or falls back. Tracking, reconnects, teleports, pistons, and state changes are explicitly
synchronized by CuTAPI.

All finite allocation is deterministic by definition ID and canonical state values. Startup fails for
missing visuals/models, finite-capacity exhaustion, incompatible models, duplicate assignments, or
carrier resource ownership conflicts; CuTAPI never silently changes methods. The generated diagnostic
manifest records Minecraft version, resource-pack hash, capacities, and assignments.

Vanilla clients only know the projected carrier. CuTAPI therefore resolves middle-click from the
server position. Only `Vanilla` renders correctly without a pack, and visual models never define
hardness, collision, sounds, drops, redstone, or tile behavior. Packet libraries that bypass the
normal connection pipeline can still leak unknown native state IDs.
Because protocol block tags contain block IDs rather than states, a tagged custom definition expands
to all of its carrier block IDs; client-only tag behavior can therefore also apply to real carrier
blocks.

## Block systems and tile systems

A `BlockSystem` handles behavior common to regular blocks and tile entities. A `TileSystem` extends
it, is type-gated to `CuTPlacedTileEntity`, and adds persisted-data, load, save, tick, attachment,
unload, and removal callbacks.

```kotlin
data class CopperLampData(val energy: Int) : BlockAttachment {
    companion object : Schema<CopperLampData> by schema(id("example:copper_lamp_data"), {
        property(CopperLampData::energy, VariantSerializer.Int)
    })
}

object CopperLampSystem : TileSystem {
    override val id = id("example:copper_lamp_system")
    override val priority = RegistryPriority.High

    override fun tilePrerequisite(tile: CuTPlacedTileEntity): Boolean =
        tile.hasAttachment(CopperLampData)

    override fun onRightClick(context: BlockInteractContext) {
        val data = context.attachment(CopperLampData)
        (context.tile as CuTPlacedTileEntity).setAttachment(data.copy(energy = data.energy + 1))
    }

    override fun onTick(context: TileTickContext) {
        val data = context.tileEntity.getAttachment(CopperLampData)
        // Perform native block-entity work here.
    }

    override fun onBeforeSave(context: TileSaveContext) {
        // Last chance to replace persisted attachment values before NBT is written.
    }
}

fun registerExampleSystems() {
    Schema.modifyRegistry { register(CopperLampData) }
    BlockSystem.modifyRegistry { register(CopperLampSystem) }
}
```

Both system types share one priority-ordered registry. Definitions expose immutable intrinsic
attachments. Only a placed native tile entity can add, replace, suppress, or remove persisted overlay
attachments with `setAttachment`, `addAttachment`, and `removeAttachment`; changes invoke
`TileSystem.onAttachmentChanged`.

`BlockSystem` also exposes placement, left/right interaction, neighbor change, state change,
pre-break, drop, post-break, and explosion callbacks. The native block and block entity route these
lifecycles; `TileSystem.onTick` runs from Minecraft's block-entity ticker.

CraftBukkit does not have a public wrapper factory for third-party block-entity types. CuTAPI's native
and system APIs operate normally, but direct calls such as `Block#getState()` or
`Chunk#getTileEntities()` on a custom tile entity are not currently supported. Use
`CustomBlockManager.getPlacedTile(...)` or the system context instead.

## Mining runtime

Custom mining is server-authoritative. CuTAPI intercepts only custom `START_DESTROY_BLOCK`, validates
normal reach/protection/adventure rules, runs the Paper interaction path, and creates at most one
session per player. Vanilla block packets and mining remain unchanged.

Progress is recalculated every tick from all `Tool` attachments (or inferred vanilla tool data), the
current item, haste/fatigue, real attributes, water, grounded state, hardness, and tool correctness.
Negative hardness is unbreakable, zero is instant, and positive hardness accumulates fractional
progress. Early or repeated client stop packets cannot complete the block faster.

For the active miner, CuTAPI projects `BLOCK_BREAK_SPEED` as zero without modifying its authoritative
server value. It preserves this projection through real attribute updates, restores the real snapshot
on every exit path, and acknowledges every intercepted sequence. Progress becomes vanilla crack
stages `0..9`; `ClientboundBlockDestructionPacket` is sent only when the stage changes to the miner and
observers within 32 blocks. Each session uses a unique synthetic breaker ID, separate from the miner's
entity ID, so the client's local first-stage prediction cannot overwrite the authoritative stage and
concurrent overlays stay independent.

Abort, replacement, invalidation, cancellation, completion, disconnect, death, teleport, world or
game-mode changes, range loss, block changes, pistons, explosions, chunk unload, and timeout clear the
crack with stage `-1`. The first successful concurrent miner invalidates the rest. Completion follows
the Paper break event, block-system pre-break, native removal, durability, produced drops/experience,
drop event, post-break, sound/particle, cleanup order and disables native automatic loot to prevent
duplicates. Cancelled completion retains and resends the projected block.

Mining visuals have up to one tick of latency and depend on explicit crack packets. `DisplayEntity`
cracks cover only its carrier. Another plugin independently spoofing `BLOCK_BREAK_SPEED` cannot be
merged with unknown client-only state. Commands, editing plugins, explosions, pistons, and API removal
use their own removal contexts rather than a player mining session, and redundant start/abort traffic
is rate-limited.
