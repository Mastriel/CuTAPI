package xyz.mastriel.cutapi.gui

import org.bukkit.entity.*
import org.bukkit.event.inventory.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.block.*

public open class GuiContext<C, V : InventoryView> internal constructor(
    public val session: GuiSession<C, V>,
) {
    public val definition: GuiDefinition<C, V> get() = session.definition
    public val context: C get() = session.context
    public val viewer: Player get() = session.viewer
    public val view: V get() = session.view
    public val subject: GuiSubject get() = session.subject
    public val tileEntityOrNull: CuTPlacedTileEntity? get() = (subject as? GuiSubject.Block)?.tile

    public fun requireTileEntity(): CuTPlacedTileEntity =
        tileEntityOrNull ?: error("GUI ${definition.id} is not open for a custom tile entity.")

    public fun invalidate(): Unit = session.invalidate()

    public fun invalidate(slot: GuiSlot): Unit = session.invalidate(slot)

    public fun port(port: GuiSlotPort): GuiPortHandle = session.port(port)

    public fun portOrNull(port: GuiSlotPort): GuiPortHandle? = session.portOrNull(port)
}

public class GuiRenderContext<C, V : InventoryView> internal constructor(
    session: GuiSession<C, V>,
) : GuiContext<C, V>(session)

public open class GuiLifecycleContext<C, V : InventoryView> internal constructor(
    session: GuiSession<C, V>,
) : GuiContext<C, V>(session) {
    public fun launch(block: suspend GuiSession<C, V>.() -> Unit): kotlinx.coroutines.Job = session.launch(block)
}

public class GuiCloseContext<C, V : InventoryView> internal constructor(
    session: GuiSession<C, V>,
    public val reason: GuiCloseReason,
) : GuiLifecycleContext<C, V>(session)

public class GuiInteractionContext<C, V : InventoryView> internal constructor(
    session: GuiSession<C, V>,
    public val slot: GuiSlot,
    public val rawSlot: Int,
    public val clickedInventory: Inventory?,
    public val currentItem: ItemStack?,
    public val cursor: ItemStack?,
    public val clickType: ClickType,
    public val action: InventoryAction,
    public val event: InventoryClickEvent,
) : GuiContext<C, V>(session) {
    public val isCancelled: Boolean get() = event.isCancelled

    public fun cancel() {
        event.isCancelled = true
    }

    public fun allowVanilla() {
        event.isCancelled = false
    }

    public fun <OV : InventoryView> open(definition: GuiDefinition<Unit, OV>) {
        session.manager.runNextTick { viewer.openGui(definition) }
    }

    public fun <OC, OV : InventoryView> open(definition: GuiDefinition<OC, OV>, context: OC) {
        session.manager.runNextTick { viewer.openGui(definition, context) }
    }

    public fun close() {
        session.manager.runNextTick { session.close() }
    }
}

public class GuiInputAcceptanceContext<C, V : InventoryView> internal constructor(
    session: GuiSession<C, V>,
    public val slot: GuiSlot,
) : GuiContext<C, V>(session)

public class GuiPortAcceptanceContext<C, V : InventoryView> internal constructor(
    session: GuiSession<C, V>,
    public val port: GuiSlotPort,
    public val slot: GuiSlot,
) : GuiContext<C, V>(session)

public class GuiInputChangeContext<C, V : InventoryView> internal constructor(
    session: GuiSession<C, V>,
    public val slot: GuiSlot,
    previous: ItemStack?,
    item: ItemStack?,
    public val interaction: InventoryClickEvent?,
) : GuiContext<C, V>(session) {
    public val previous: ItemStack? = previous?.clone()
    public val item: ItemStack? = item?.clone()
}

public class GuiBoundSlotUpdateContext<C, V : InventoryView> internal constructor(
    session: GuiSession<C, V>,
    public val backing: GuiSlotBacking,
    public val port: GuiSlotPort,
    public val storageSlot: Int?,
    public val guiSlot: GuiSlot,
    previous: ItemStack?,
    item: ItemStack?,
    public val source: GuiSlotUpdateSource,
) : GuiContext<C, V>(session) {
    /** The owning tile for a block-backed port update; throws for standalone backings. */
    public val block: CuTPlacedTileEntity get() = requireTileEntity()
    public val previous: ItemStack? = previous?.clone()
    public val item: ItemStack? = item?.clone()
}

public class GuiInventoryEventContext<C, V : InventoryView, E : InventoryEvent> internal constructor(
    session: GuiSession<C, V>,
    public val event: E,
) : GuiContext<C, V>(session)

internal data class GuiInventoryEventHandler<C, V : InventoryView, E : InventoryEvent>(
    val eventType: kotlin.reflect.KClass<E>,
    val handler: GuiInventoryEventContext<C, V, E>.() -> Unit,
)
