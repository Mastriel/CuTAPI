package xyz.mastriel.cutapi.gui

import net.kyori.adventure.text.*
import org.bukkit.event.inventory.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import kotlin.reflect.*

/** Immutable GUI template with a caller-supplied open context [C] and concrete view type [V]. */
public class GuiDefinition<C, V : InventoryView> internal constructor(
    override val id: Identifier,
    public val type: GuiType<V>,
    internal val token: GuiDefinitionToken,
    internal val titleProducer: GuiRenderContext<C, V>.() -> Component,
    internal val elements: List<GuiElement<C, V>>,
    internal val stateDeclarations: List<GuiStateDeclaration<C, *>>,
    private val sharedState: MutableMap<GuiSharedStateKey<*>, Any?>,
    internal val openHandlers: List<GuiLifecycleContext<C, V>.() -> Unit>,
    internal val whileOpenHandlers: List<GuiLifecycleContext<C, V>.() -> Unit>,
    internal val closeHandlers: List<GuiCloseContext<C, V>.() -> Unit>,
    internal val inventoryEventHandlers: List<GuiInventoryEventHandler<C, V, *>>,
    public val defaultInteraction: GuiInteractionPolicy,
    public val overlay: GuiOverlaySpec?,
) : Identifiable {
    private val elementsByPort: Map<GuiSlotPort, GuiElement<C, V>> =
        elements.mapNotNull { element -> element.port?.let { it to element } }.toMap()
    public val managedSlots: GuiSlotGroup = GuiSlotGroup(elements.map(GuiElement<C, V>::slot))
    public val slotPorts: Set<GuiSlotPort> = elementsByPort.keys

    internal val requiredSlotPorts: Set<GuiSlotPort> = elements
        .filter(GuiElement<C, V>::requiredPort)
        .mapNotNull(GuiElement<C, V>::port)
        .toSet()

    internal fun element(port: GuiSlotPort): GuiElement<C, V>? = elementsByPort[port]

    @Suppress("UNCHECKED_CAST")
    internal fun <T> readSharedState(key: GuiSharedStateKey<T>): T {
        check(sharedState.containsKey(key)) { "Shared state '${key.propertyName}' is not declared by GUI $id." }
        return sharedState[key] as T
    }

    internal fun <T> writeSharedState(key: GuiSharedStateKey<T>, value: T) {
        check(sharedState.containsKey(key)) { "Shared state '${key.propertyName}' is not declared by GUI $id." }
        sharedState[key] = value
    }

    internal fun clearSharedState() {
        sharedState.clear()
    }
}

@GuiDslMarker
public class GuiBuilder<C, V : InventoryView> internal constructor(
    private val id: Identifier,
    public val type: GuiType<V>,
) {
    internal val token: GuiDefinitionToken = GuiDefinitionToken(id.toString())
    private val elements: MutableList<GuiElement<C, V>> = mutableListOf()
    private val stateDeclarations: MutableList<GuiStateDeclaration<C, *>> = mutableListOf()
    private val sharedState: MutableMap<GuiSharedStateKey<*>, Any?> = linkedMapOf()
    private val openHandlers: MutableList<GuiLifecycleContext<C, V>.() -> Unit> = mutableListOf()
    private val whileOpenHandlers: MutableList<GuiLifecycleContext<C, V>.() -> Unit> = mutableListOf()
    private val closeHandlers: MutableList<GuiCloseContext<C, V>.() -> Unit> = mutableListOf()
    private val inventoryEventHandlers: MutableList<GuiInventoryEventHandler<C, V, *>> = mutableListOf()
    private var titleProducer: GuiRenderContext<C, V>.() -> Component = { Component.empty() }
    private var overlaySpec: GuiOverlaySpec? = null

    public var defaultInteraction: GuiInteractionPolicy = GuiInteractionPolicy.CancelManagedSlots

    public fun <T> state(initialize: GuiStateInitializationContext<C>.() -> T): GuiStateDelegateProvider<C, T> =
        GuiStateDelegateProvider(token, ::registerState, initialize)

    public fun <T> sharedState(initialize: () -> T): GuiSharedStateDelegateProvider<T> =
        GuiSharedStateDelegateProvider(token, ::registerSharedState, initialize)

    public fun title(configure: GuiTitleBuilder<C, V>.() -> Unit) {
        titleProducer = GuiTitleBuilder<C, V>().apply(configure).build()
    }

    public fun overlay(
        texture: ResourceRef<Texture2D>,
        configure: GuiOverlayBuilder.() -> Unit = {},
    ) {
        check(overlaySpec == null) { "GUI $id declares more than one overlay." }
        val builder = GuiOverlayBuilder(type.overlayProfile).apply(configure)
        builder.addPrimary(texture)
        overlaySpec = builder.build()
    }

    public fun overlay(configure: GuiOverlayBuilder.() -> Unit) {
        check(overlaySpec == null) { "GUI $id declares more than one overlay." }
        overlaySpec = GuiOverlayBuilder(type.overlayProfile).apply(configure).build()
    }

    public fun slot(index: Int, configure: GuiSlotBuilder<C, V>.() -> Unit) {
        slot(GuiSlot.Top(index), configure)
    }

    public fun slot(slot: GuiSlot, configure: GuiSlotBuilder<C, V>.() -> Unit) {
        require(type.layout.isValid(slot)) { "GUI $id (${type.id}) references invalid slot $slot." }
        putElement(GuiSlotBuilder<C, V>().apply(configure).build(slot))
    }

    internal fun itemFallback(slot: GuiSlot): GuiRenderContext<C, V>.() -> ItemStack? {
        val rawSlot = type.layout.toRaw(slot)
        val fallback = elements.firstOrNull { type.layout.toRaw(it.slot) == rawSlot }
            ?: return { null }
        return {
            if (fallback.visible(this)) fallback.item?.invoke(this) else null
        }
    }

    public fun boundSlot(
        port: GuiSlotPort,
        slot: Int,
        configure: GuiBoundSlotBuilder<C, V>.() -> Unit = {},
    ) {
        val logicalSlot = GuiSlot.Top(slot)
        require(type.layout.isValid(logicalSlot)) { "GUI $id (${type.id}) references invalid bound slot $slot." }
        putElement(GuiBoundSlotBuilder<C, V>().apply(configure).build(port, logicalSlot))
    }

    private fun putElement(element: GuiElement<C, V>) {
        val rawSlot = type.layout.toRaw(element.slot)
        val existingIndex = elements.indexOfFirst { type.layout.toRaw(it.slot) == rawSlot }
        val duplicatePort = element.port?.let { port ->
            elements.indexOfFirst { it.port == port && type.layout.toRaw(it.slot) != rawSlot }
        } ?: -1
        require(duplicatePort < 0) {
            "GUI $id (${type.id}) declares slot port '${element.port?.name}' at more than one physical slot."
        }
        if (existingIndex >= 0) {
            elements[existingIndex] = element
        } else {
            elements += element
        }
    }

    public fun fill(
        group: GuiSlotGroup = all(),
        configure: GuiSlotBuilder<C, V>.() -> Unit,
    ) {
        for (slot in group) slot(slot, configure)
    }

    public fun slots(vararg slots: Int): GuiSlotGroup = GuiSlotGroup(slots.map { GuiSlot.Top(it) })

    public fun slots(slots: IntRange): GuiSlotGroup = GuiSlotGroup(slots.map { GuiSlot.Top(it) })

    public fun row(row: Int): GuiSlotGroup {
        require(row in 0 until type.layout.rows) { "Row $row is outside GUI $id." }
        val start = row * type.layout.columns
        return GuiSlotGroup((start until (start + type.layout.columns).coerceAtMost(type.layout.topSize)).map(GuiSlot::Top))
    }

    public fun column(column: Int): GuiSlotGroup {
        require(column in 0 until type.layout.columns) { "Column $column is outside GUI $id." }
        return GuiSlotGroup(
            (0 until type.layout.rows)
                .map { it * type.layout.columns + column }
                .filter { it < type.layout.topSize }
                .map(GuiSlot::Top),
        )
    }

    public fun rectangle(from: Int, to: Int): GuiSlotGroup {
        require(from <= to) { "Rectangle start $from must not exceed end $to." }
        require(from >= 0 && to < type.layout.topSize) { "Rectangle $from..$to is outside GUI $id." }
        val fromRow = from / type.layout.columns
        val toRow = to / type.layout.columns
        val fromColumn = from % type.layout.columns
        val toColumn = to % type.layout.columns
        return GuiSlotGroup(
            (fromRow..toRow).flatMap { row ->
                (fromColumn..toColumn).map { column -> GuiSlot.Top(row * type.layout.columns + column) }
            },
        )
    }

    public fun border(): GuiSlotGroup {
        val lastRow = type.layout.rows - 1
        val lastColumn = type.layout.columns - 1
        return GuiSlotGroup(
            (0 until type.layout.topSize).filter { slot ->
                val row = slot / type.layout.columns
                val column = slot % type.layout.columns
                row == 0 || row == lastRow || column == 0 || column == lastColumn
            }.map(GuiSlot::Top),
        )
    }

    /** Returns every top-inventory slot in deterministic raw-slot order. */
    public fun all(): GuiSlotGroup =
        GuiSlotGroup((0 until type.layout.topSize).map(GuiSlot::Top))

    public fun role(role: GuiSlotRole): GuiSlotGroup = type.layout.slots(role)

    public fun <E : Any> paginate(
        key: String,
        slots: GuiSlotGroup,
        configure: GuiPaginationBuilder<C, V, E>.() -> Unit,
    ) {
        require(key.isNotBlank()) { "Pagination key may not be blank." }
        GuiPaginationBuilder<C, V, E>().apply(configure).install(this, key, slots)
    }

    public fun onOpen(handler: GuiLifecycleContext<C, V>.() -> Unit) {
        openHandlers += handler
    }

    /** Runs once per server tick for each active session of this GUI definition. */
    public fun whileOpen(handler: GuiLifecycleContext<C, V>.() -> Unit) {
        whileOpenHandlers += handler
    }

    /** Runs once per server tick for each active session of this GUI definition. */
    @Deprecated("Use whileOpen { ... }.", ReplaceWith("whileOpen(handler)"))
    public fun onTick(handler: GuiLifecycleContext<C, V>.() -> Unit) {
        whileOpen(handler)
    }

    public fun onClose(handler: GuiCloseContext<C, V>.() -> Unit) {
        closeHandlers += handler
    }

    public fun <E : InventoryEvent> onEvent(
        eventType: KClass<E>,
        handler: GuiInventoryEventContext<C, V, E>.() -> Unit,
    ) {
        inventoryEventHandlers += GuiInventoryEventHandler(eventType, handler)
    }

    private fun <T> registerState(
        name: String,
        initialize: GuiStateInitializationContext<C>.() -> T,
    ): GuiStateKey<T> {
        val key = GuiStateKey<T>(GuiStateKeySequence.next(), name)
        stateDeclarations += GuiStateDeclaration(key, initialize)
        return key
    }

    private fun <T> registerSharedState(name: String, initial: T): GuiSharedStateKey<T> {
        val key = GuiSharedStateKey<T>(GuiStateKeySequence.next(), name)
        sharedState[key] = initial
        return key
    }

    internal fun build(): GuiDefinition<C, V> {
        val overlay = overlaySpec
        if (overlay != null) {
            require(type.overlayProfile == null || overlay.profile == type.overlayProfile) {
                "GUI $id overlay profile ${overlay.profile.name} does not match menu type ${type.id}."
            }
            GuiOverlayCatalog.contribute(id, overlay)
        }
        return GuiDefinition(
            id = id,
            type = type,
            token = token,
            titleProducer = titleProducer,
            elements = elements.toList(),
            stateDeclarations = stateDeclarations.toList(),
            sharedState = sharedState.toMutableMap(),
            openHandlers = openHandlers.toList(),
            whileOpenHandlers = whileOpenHandlers.toList(),
            closeHandlers = closeHandlers.toList(),
            inventoryEventHandlers = inventoryEventHandlers.toList(),
            defaultInteraction = defaultInteraction,
            overlay = overlay,
        )
    }
}

@GuiDslMarker
public class GuiTitleBuilder<C, V : InventoryView> internal constructor() {
    private var producer: (GuiRenderContext<C, V>.() -> Component)? = null

    public fun text(produce: GuiRenderContext<C, V>.() -> Component) {
        check(producer == null) { "A GUI title text producer may only be declared once." }
        producer = produce
    }

    internal fun build(): GuiRenderContext<C, V>.() -> Component = producer ?: { Component.empty() }
}

/** Compile-time type token selecting the contextual [gui] factory overload. */
public class GuiContextType<C> internal constructor()

public fun <C> guiContext(): GuiContextType<C> = GuiContextType()

/** Builds a GUI whose open context is [Unit]. */
public fun <V : InventoryView> gui(
    id: Identifier,
    type: GuiType<V>,
    configure: GuiBuilder<Unit, V>.() -> Unit,
): GuiDefinition<Unit, V> = GuiBuilder<Unit, V>(id, type).apply(configure).build()

/** Builds a GUI that requires a context of type [C] whenever it is opened. */
public fun <C, V : InventoryView> gui(
    id: Identifier,
    type: GuiType<V>,
    @Suppress("UNUSED_PARAMETER") context: GuiContextType<C>,
    configure: GuiBuilder<C, V>.() -> Unit,
): GuiDefinition<C, V> = GuiBuilder<C, V>(id, type).apply(configure).build()
