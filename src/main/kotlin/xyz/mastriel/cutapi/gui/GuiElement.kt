package xyz.mastriel.cutapi.gui

import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

internal data class GuiInputSpec<C, V : InventoryView>(
    val accepts: GuiInputAcceptanceContext<C, V>.(ItemStack) -> Boolean,
    val onChange: List<GuiInputChangeContext<C, V>.() -> Unit>,
)

internal data class GuiElement<C, V : InventoryView>(
    val slot: GuiSlot,
    val port: GuiSlotPort?,
    val requiredPort: Boolean,
    val boundSlotAccess: GuiBoundSlotAccess?,
    val sessionBackingInitializer: (GuiSlotBackingInitializationContext<C>.() -> ItemStack?)?,
    val portAcceptance: (GuiPortAcceptanceContext<C, V>.(ItemStack) -> Boolean)?,
    val key: String?,
    val item: (GuiRenderContext<C, V>.() -> ItemStack?)?,
    val visible: GuiRenderContext<C, V>.() -> Boolean,
    val clickHandlers: List<GuiInteractionContext<C, V>.() -> Unit>,
    val slotUpdateHandlers: List<GuiBoundSlotUpdateContext<C, V>.() -> Unit>,
    val input: GuiInputSpec<C, V>?,
)

@GuiDslMarker
public class GuiSlotBuilder<C, V : InventoryView> internal constructor() {
    public var key: String? = null
    private var itemProducer: (GuiRenderContext<C, V>.() -> ItemStack?)? = null
    private var visibility: GuiRenderContext<C, V>.() -> Boolean = { true }
    private val clickHandlers: MutableList<GuiInteractionContext<C, V>.() -> Unit> = mutableListOf()
    private var inputSpec: GuiInputSpec<C, V>? = null

    public fun item(produce: GuiRenderContext<C, V>.() -> ItemStack?) {
        check(itemProducer == null) { "A GUI slot item producer may only be declared once." }
        itemProducer = produce
    }

    public fun visibleWhen(predicate: GuiRenderContext<C, V>.() -> Boolean) {
        visibility = predicate
    }

    public fun onClick(handler: GuiInteractionContext<C, V>.() -> Unit) {
        clickHandlers += handler
    }

    public fun input(configure: GuiInputBuilder<C, V>.() -> Unit) {
        check(inputSpec == null) { "A GUI input may only be declared once per slot." }
        inputSpec = GuiInputBuilder<C, V>().apply(configure).build()
    }

    internal fun build(slot: GuiSlot): GuiElement<C, V> = GuiElement(
        slot = slot,
        port = null,
        requiredPort = false,
        boundSlotAccess = null,
        sessionBackingInitializer = null,
        portAcceptance = null,
        key = key,
        item = itemProducer,
        visible = visibility,
        clickHandlers = clickHandlers.toList(),
        slotUpdateHandlers = emptyList(),
        input = inputSpec,
    )
}

@GuiDslMarker
public class GuiBoundSlotBuilder<C, V : InventoryView> internal constructor() {
    /** Whether every block presentation using this GUI must bind this port. */
    public var required: Boolean = true

    /** Overrides this port's default player permissions for every backing source. */
    public var playerAccess: GuiBoundSlotAccess = GuiBoundSlotAccess.Default

    public var key: String? = null

    private var placeholderProducer: (GuiRenderContext<C, V>.() -> ItemStack?)? = null
    private var sessionBackingInitializer: (GuiSlotBackingInitializationContext<C>.() -> ItemStack?)? = null
    private var acceptance: (GuiPortAcceptanceContext<C, V>.(ItemStack) -> Boolean)? = null
    private val slotUpdateHandlers: MutableList<GuiBoundSlotUpdateContext<C, V>.() -> Unit> = mutableListOf()

    /** Creates an independent mutable backing for every GUI session not given a higher-priority binding. */
    public fun sessionBacking(initialize: GuiSlotBackingInitializationContext<C>.() -> ItemStack?) {
        check(sessionBackingInitializer == null) { "A bound GUI slot may only declare one session backing." }
        sessionBackingInitializer = initialize
    }

    /** Adds a presentation-level insertion predicate for every backing used by this port. */
    public fun accepts(predicate: GuiPortAcceptanceContext<C, V>.(ItemStack) -> Boolean) {
        check(acceptance == null) { "A bound GUI slot may only declare one acceptance predicate." }
        acceptance = predicate
    }

    /** Renders when this port is standalone, unbound, or bound to an empty logical storage slot. */
    public fun placeholder(produce: GuiRenderContext<C, V>.() -> ItemStack?) {
        check(placeholderProducer == null) { "A bound GUI slot may only declare one placeholder producer." }
        placeholderProducer = produce
    }

    /** Runs for each active session after this port's canonical backing contents actually change. */
    public fun onSlotUpdate(handler: GuiBoundSlotUpdateContext<C, V>.() -> Unit) {
        slotUpdateHandlers += handler
    }

    internal fun build(port: GuiSlotPort, slot: GuiSlot.Top): GuiElement<C, V> = GuiElement(
        slot = slot,
        port = port,
        requiredPort = required,
        boundSlotAccess = playerAccess,
        sessionBackingInitializer = sessionBackingInitializer,
        portAcceptance = acceptance,
        key = key,
        item = placeholderProducer,
        visible = { true },
        clickHandlers = emptyList(),
        slotUpdateHandlers = slotUpdateHandlers.toList(),
        input = null,
    )
}

/** Player permissions for canonical contents projected through a GUI port. */
public enum class GuiBoundSlotAccess(
    internal val permitsInsertionOverride: Boolean?,
    internal val permitsExtractionOverride: Boolean?,
) {
    /** Uses the backing's default: standalone ports are read/write and block ports derive it from their role. */
    Default(null, null),

    /** Players may both insert and extract items. */
    ReadWrite(true, true),

    /** Players may insert items but may not extract them. */
    InsertOnly(true, false),

    /** Players may extract items but may not insert them. */
    ExtractOnly(false, true),

    /** Players may view the slot but may neither insert nor extract items. */
    ReadOnly(false, false),
}

@GuiDslMarker
public class GuiInputBuilder<C, V : InventoryView> internal constructor() {
    private var acceptance: (GuiInputAcceptanceContext<C, V>.(ItemStack) -> Boolean)? = null
    private val changeHandlers: MutableList<GuiInputChangeContext<C, V>.() -> Unit> = mutableListOf()

    public fun accepts(predicate: GuiInputAcceptanceContext<C, V>.(ItemStack) -> Boolean) {
        check(acceptance == null) { "An input acceptance predicate may only be declared once." }
        acceptance = predicate
    }

    public fun onChange(handler: GuiInputChangeContext<C, V>.() -> Unit) {
        changeHandlers += handler
    }

    internal fun build(): GuiInputSpec<C, V> = GuiInputSpec(
        accepts = requireNotNull(acceptance) { "Every GUI input slot must declare accepts { ... }." },
        onChange = changeHandlers.toList(),
    )
}
