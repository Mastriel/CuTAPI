# Inventory GUIs

CuTAPI GUI definitions are immutable values. They are not registered and may be opened directly for a player. Each open view receives an independent session, inventory view, session-state store, render cache, and coroutine scope.

```kotlin
val ProfileGui = gui(
    id = id(MyPlugin, "profile"),
    type = GuiType.Chest(rows = 6),
) {
    var page by state<Int> { 0 }
    var totalOpens by sharedState<Int> { 0 }

    title {
        text { Component.text("${viewer.name}'s Profile") }
    }

    slot(13) {
        item { profileIcon(viewer, page) }
        onClick { page += 1 }
    }

    onOpen { totalOpens += 1 }

    whileOpen {
        // Runs once per server tick while this viewer has this GUI open.
        page = currentPageFor(viewer)
    }
}

player.openGui(ProfileGui)
```

`state` initializes once per session. `sharedState` initializes once for that exact definition object and is shared across all of its active sessions. Assigning either delegate invalidates the appropriate sessions; in-place mutation must be followed by reassignment or `invalidate()`.

## Typed open contexts

Every definition has an open-context type. The ordinary `gui(...)` overload uses `Unit`, so context-free GUIs retain the concise `player.openGui(ProfileGui)` syntax. Use `guiContext<C>()` when a definition requires caller-supplied data:

```kotlin
data class ProfileContext(
    val target: UUID,
    val initialPage: Int,
)

val ContextualProfileGui = gui(
    id = id(MyPlugin, "contextual_profile"),
    type = GuiType.Chest(6),
    context = guiContext<ProfileContext>(),
) {
    var page by state { context.initialPage }

    title {
        text { Component.text("Profile: ${context.target}") }
    }

    slot(13) {
        item { profileIcon(context.target, page) }
        onClick { page += 1 }
    }

    onOpen { loadProfile(context.target) }
    whileOpen { refreshProfile(context.target) }
}

player.openGui(
    ContextualProfileGui,
    ProfileContext(
        target = targetPlayer.uniqueId,
        initialPage = 2,
    ),
)
```

The inferred type is `GuiDefinition<ProfileContext, InventoryView>`. The same `context` value is available from state and backing initializers, titles, item renderers, click/input/update callbacks, lifecycle callbacks, specialized inventory events, reusable components, and session coroutines.

A non-`Unit` definition has no context-free `openGui` overload, so omitting its context is a compile error. Unit definitions alone receive the convenience overload that supplies `Unit` automatically.

Slot declarations use last-declaration-wins semantics. This makes it possible to fill a GUI and then replace selected slots:

```kotlin
fill {
    item { backgroundItem() }
}

slot(5) {
    item { actionItem() }
}
```

Paginator navigation controls are conditional replacements. When a previous or next page exists, the control item wins. When that page does not exist, the control falls through to the item renderer that previously occupied its slot, so a background fill can act as its placeholder.

Named slot ports form a reusable contract between a GUI layout and canonical item storage. Ports are opaque: callers must use the exact `GuiSlotPort` object declared by the GUI, so keep shared ports in a constants object. A port may receive its backing from a block presentation, an open-time binding, or a session-local backing. When the selected backing is empty—or no backing exists—the placeholder is rendered instead.

`boundSlot` participates in the same last-declaration-wins rules as `slot` and `fill`. Declare it after a background fill when the port should replace that background position. A later ordinary `slot` declaration at the same position removes the port again.

## Standalone and externally backed ports

A GUI can own fresh port contents for each viewer without being attached to a block:

```kotlin
object ProcessorPorts {
    val Input = GuiSlotPort("input")
    val Output = GuiSlotPort("output")
}

val ProcessorGui = gui(
    id = id(MyPlugin, "processor"),
    type = GuiType.Chest(3),
) {
    boundSlot(ProcessorPorts.Input, 10) {
        sessionBacking { null }
        accepts { stack -> stack.type == Material.COAL }
        placeholder { emptyInputItem() }
    }
    boundSlot(ProcessorPorts.Output, 16) {
        sessionBacking { null }
        playerAccess = GuiBoundSlotAccess.ExtractOnly
        placeholder { emptyOutputItem() }
    }

    onOpen {
        port(ProcessorPorts.Input).setItem(ItemStack(Material.COAL))
    }

    whileOpen {
        val input = port(ProcessorPorts.Input)
        val output = port(ProcessorPorts.Output)
        if (input.item?.type == Material.COAL && output.isEmpty) {
            input.tryExtract(1)
            output.tryInsert(ItemStack(Material.GOLD_INGOT))
        }
    }
}
```

`port(...)` returns a session-bound `GuiPortHandle`. Its `item` is a defensive snapshot; mutate canonical contents through `setItem`, `clear`, `update`, `tryInsert`, or `tryExtract`.

An opener can supply longer-lived or shared storage with a `GuiSlotBacking`. This binding overrides the port's `sessionBacking`:

```kotlin
val externalInput = MutableGuiSlotBacking { null }
val externalOutput = MutableGuiSlotBacking { null }

player.openGui(ProcessorGui) {
    bind(ProcessorPorts.Input) { externalInput }
    bind(ProcessorPorts.Output) { externalOutput }
}
```

For a contextual definition, pass the context before the binding block. Backing producers see that typed value:

```kotlin
player.openGui(ContextualProcessorGui, processorContext) {
    bind(ProcessorPorts.Input) {
        context.inputBacking
    }
}
```

The complete precedence is block presentation binding, then open-time binding, then `sessionBacking`, then an unbound placeholder. A block presentation therefore replaces a GUI's session-local contents at the same port without changing the GUI definition.

## Overlays

An overlay uses a dedicated font and never contributes providers to `minecraft:default`:

```kotlin
val MachineGui = gui(
    id = id(MyPlugin, "machine"),
    type = GuiType.Chest(rows = 3),
) {
    overlay(ref<Texture2D>(MyPlugin, "gui/machine.png")) {
        profile = GuiOverlayProfile.chest(rows = 3)
    }
}
```

Overlay definitions must be constructed during startup, before resource-pack processing begins. Ordinary definitions without overlays may be constructed at any time. Identical overlay declarations may share an identifier; conflicting declarations fail immediately.

Generic 9×1 through 9×6 inventories use a transparent vanilla container texture. CuTAPI automatically prepends the matching bundled `ui/base_container/generic_9xN.png` background to outgoing inventory titles, beneath any custom overlay. This also preserves the background for ordinary chests, barrels, ender chests, and inventories opened by other plugins. Other menu types keep their vanilla textures.

Set `showBaseTexture = false` to leave the base transparent, so transparent parts of your custom texture show through:

```kotlin
overlay(ref<Texture2D>(MyPlugin, "gui/machine.png")) {
    showBaseTexture = false
}
```

The flag defaults to `true`. To hide the background without adding any custom layers, use `overlay { showBaseTexture = false }`. Visible title text keeps its normal position in either mode.

## Progress-arrow items

`CustomItem.ProgressArrow` renders the bundled 24×16 furnace-style arrow. Control it by replacing its `CustomItem.Progress` attachment with a normalized value:

```kotlin
slot(13) {
    item {
        CustomItem.ProgressArrow.createItemStack().also { arrow ->
            arrow.setAttachment(CustomItem.Progress(machineProgress.toFloat()))
        }.vanilla()
    }
}
```

Values are clamped to `0f..1f`. The resource generator combines `progress_arrow_empty.png` and `progress_arrow_full.png` into 25 pixel-aligned stages and selects the first nonempty stage for any positive progress, matching the furnace GUI's fill behavior. Generated models disable swap animation and opt into oversized GUI rendering so the arrow remains 24×16 rather than being squeezed into a 16×16 item icon.

## Tile inventories

Only a `CustomTileEntity` can own persistent storage. Logical storage is distinct from its GUI presentation, so decorative items are never persisted, dropped, placed into a content-bearing block item, or exposed to hoppers.

```kotlin
object MachinePorts {
    val Input = GuiSlotPort("input")
    val Buffer = GuiSlotPort("buffer")
    val Output = GuiSlotPort("output")
}

val MachineGui = gui(
    id = id(MyPlugin, "machine"),
    type = GuiType.Chest(3),
) {
    fill {
        item { inventoryBackgroundItem() }
    }

    boundSlot(MachinePorts.Input, slot = 10) {
        placeholder { emptyInputItem() }

        onSlotUpdate {
            // `block` is the placed tile; this receiver is the viewer's GUI context.
            updateMachineDisplay(block, viewer, previous, item, source)
        }
    }
    boundSlot(MachinePorts.Buffer, slot = 13) {
        required = false
        playerAccess = GuiBoundSlotAccess.ExtractOnly
        placeholder { emptyBufferItem() }
    }
    boundSlot(MachinePorts.Output, slot = 16) {
        placeholder { emptyOutputItem() }
    }
}

val Machine = customTileEntity(
    id = id(MyPlugin, "machine"),
) {
    inventory(size = 3) {
        breakPolicy = BlockInventoryBreakPolicy.KeepContents

        slot(0, BlockInventorySlotAccess.Input) {
            accepts { candidate.type == Material.IRON_INGOT }
            insertFaces(BlockFace.UP)
        }
        slot(1, BlockInventorySlotAccess.Storage)
        slot(2, BlockInventorySlotAccess.Output) {
            extractFaces(BlockFace.DOWN)
        }

        presentation(gui = { MachineGui }) {
            bind(storageSlot = 0, to = MachinePorts.Input)
            bind(storageSlot = 1, to = MachinePorts.Buffer)
            bind(storageSlot = 2, to = MachinePorts.Output)
        }
    }
}
```

A block presentation for a contextual definition must provide a per-open context producer as a required argument:

```kotlin
presentation(
    gui = { ContextualMachineGui },
    context = {
        MachineContext(
            viewerId = player.uniqueId,
            tile = tile,
            inventory = inventory,
        )
    },
) {
    bind(storageSlot = 0, to = MachinePorts.Input)
    bind(storageSlot = 2, to = MachinePorts.Output)
}
```

The producer receiver exposes `player`, `tile`, and the canonical block `inventory`. The context argument is part of the contextual `presentation` overload rather than an optional builder call. Omitting it cannot match the Unit-only overload and therefore fails at compile time. Unit-context presentations retain `presentation(gui = { MachineGui }) { ... }`.

Opening `MachineGui` directly creates a normal standalone GUI. Its ports use their session backings when declared, otherwise they render their placeholders. Calling `placedMachine.openInventory(player)`, or right-clicking it when `openOnRightClick` is enabled, projects logical storage through the named ports and overrides any session backing. Required ports must be bound by every block presentation; ports marked `required = false` may remain unbound and continue showing their placeholder.

Automatic opening skips sneaking players and respects denied block interactions. An inventory interaction consumes the click before held items can place blocks or activate, even if opening the GUI is rejected. Off-hand interactions are consumed without opening the GUI a second time. Calling `placedMachine.openInventory(player)` directly remains an explicit opening request.

For players, `Input` and `Storage` slots allow both insertion and extraction by default; `Output` slots allow extraction only. Override the player-facing behavior per port with `playerAccess = GuiBoundSlotAccess.ReadWrite`, `InsertOnly`, `ExtractOnly`, or `ReadOnly`. These overrides do not change automation: hoppers still treat `Input` as insertion-only, `Output` as extraction-only, and `Storage` as unrestricted, with the declared predicates and face filters.

`onSlotUpdate` runs after canonical backing contents actually change, not when a placeholder or decoration merely rerenders. It runs once for every active session subscribed to that backing and provides its normal typed GUI context together with `port`, nullable `storageSlot`, `guiSlot`, cloned `previous` and `item` stacks, and the mutation `source`. `tileEntityOrNull` identifies the block for block-backed ports, and the `block` convenience property requires that one is present.

All committed contents use the versioned `cutapi:block_contents` attachment. `KeepContents` moves a nonempty snapshot to one unstackable placement item; `DropContents` drops only real contents; `DeleteContents` discards them.
