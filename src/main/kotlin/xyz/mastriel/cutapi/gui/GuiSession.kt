package xyz.mastriel.cutapi.gui

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.Plugin
import java.util.UUID

internal enum class GuiSessionStatus {
    Creating,
    Open,
    Replacing,
    Closed,
}

public class GuiSession<C, V : InventoryView> internal constructor(
    public val definition: GuiDefinition<C, V>,
    public val viewer: Player,
    public val subject: GuiSubject,
    /** The exact context value supplied for this open session. */
    public val context: C,
    internal val manager: GuiManager,
) {
    public val id: UUID = UUID.randomUUID()

    private var boundView: V? = null
    public val view: V
        get() = boundView ?: error("GUI session $id has not created its inventory view yet.")

    internal var status: GuiSessionStatus = GuiSessionStatus.Creating
    public val isClosed: Boolean get() = status == GuiSessionStatus.Closed
    public var closeReason: GuiCloseReason? = null
        private set

    public var renderCount: Long = 0
        internal set
    public var lastHandlerFailure: Throwable? = null
        internal set

    internal val renderedItems: MutableMap<Int, ItemStack?> = mutableMapOf()
    internal val placeholderRawSlots: MutableSet<Int> = mutableSetOf()
    internal val pendingSlots: MutableSet<GuiSlot> = linkedSetOf()
    internal var fullInvalidation: Boolean = false
    internal var renderScheduled: Boolean = false
    internal var rendering: Boolean = false
    internal var renderLoopPasses: Int = 0
    private val stateValues: MutableMap<GuiStateKey<*>, Any?> = linkedMapOf()
    private val resolvedPorts: MutableMap<GuiSlotPort, GuiResolvedPort> = linkedMapOf()
    private val resolvedPortsByRawSlot: MutableMap<Int, GuiResolvedPort> = mutableMapOf()
    private val portSubscriptions: MutableList<GuiSlotBackingSubscription> = mutableListOf()

    private var coroutineScope: CoroutineScope? = null

    internal fun bindView(view: V) {
        check(boundView == null) { "GUI session $id already has a view." }
        boundView = view
    }

    internal fun installPorts(ports: Collection<GuiResolvedPort>) {
        check(resolvedPorts.isEmpty()) { "GUI session $id initialized its slot ports more than once." }
        ports.forEach { resolved ->
            check(resolvedPorts.put(resolved.port, resolved) == null) {
                "GUI session $id resolved slot port '${resolved.port.name}' more than once."
            }
            check(resolvedPortsByRawSlot.put(resolved.rawSlot, resolved) == null) {
                "GUI session $id resolved more than one slot port at raw slot ${resolved.rawSlot}."
            }
            resolved.backing?.subscribe { update ->
                manager.handlePortBackingUpdate(this, resolved, update)
            }?.let(portSubscriptions::add)
        }
    }

    public fun port(port: GuiSlotPort): GuiPortHandle = portOrNull(port) ?: error(
        "GUI ${definition.id} does not declare slot port '${port.name}'.",
    )

    public fun portOrNull(port: GuiSlotPort): GuiPortHandle? {
        GuiExecution.requirePrimaryThread()
        check(!isClosed) { "Cannot access GUI slot ports after session $id closed." }
        return resolvedPorts[port]?.let { GuiPortHandle(this, it) }
    }

    internal fun resolvedPortAtRaw(rawSlot: Int): GuiResolvedPort? =
        resolvedPortsByRawSlot[rawSlot]

    internal fun resolvedPorts(): List<GuiResolvedPort> = resolvedPorts.values.toList()

    internal fun initializeState() {
        val initializationContext = GuiStateInitializationContext(viewer, subject, context)
        val execution = GuiExecutionContext(this, GuiExecutionPhase.Initializing)
        for (declaration in definition.stateDeclarations) {
            initializeDeclaration(declaration, initializationContext, execution)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> initializeDeclaration(
        declaration: GuiStateDeclaration<C, T>,
        context: GuiStateInitializationContext<C>,
        execution: GuiExecutionContext,
    ) {
        check(!stateValues.containsKey(declaration.key)) { "GUI state ${declaration.key.propertyName} initialized twice." }
        val value = GuiExecution.with(execution) { declaration.initialize(context) }
        stateValues[declaration.key] = value
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T> readState(key: GuiStateKey<T>): T {
        check(stateValues.containsKey(key)) { "State '${key.propertyName}' is not initialized for session $id." }
        return stateValues[key] as T
    }

    internal fun <T> writeState(key: GuiStateKey<T>, value: T) {
        check(stateValues.containsKey(key)) { "State '${key.propertyName}' is not initialized for session $id." }
        stateValues[key] = value
    }

    public fun invalidate() {
        manager.invalidate(this)
    }

    public fun invalidate(slot: GuiSlot) {
        manager.invalidate(this, slot)
    }

    public fun close(reason: GuiCloseReason = GuiCloseReason.Programmatic) {
        manager.close(this, reason)
    }

    public fun launch(block: suspend GuiSession<C, V>.() -> Unit): Job {
        check(!isClosed) { "Cannot launch work for closed GUI session $id." }
        val execution = GuiExecutionContext(this, GuiExecutionPhase.Handling)
        val scope = coroutineScope ?: run {
            val failureHandler = CoroutineExceptionHandler { _, failure ->
                manager.handleFailure(this, "coroutine", null, failure)
            }
            CoroutineScope(SupervisorJob() + Plugin.minecraftDispatcher + failureHandler)
                .also { coroutineScope = it }
        }
        return scope.launch(GuiExecutionContextElement(execution)) { block(this@GuiSession) }
    }

    internal fun markClosed(reason: GuiCloseReason) {
        if (status == GuiSessionStatus.Closed) return
        closeReason = reason
        status = GuiSessionStatus.Closed
        coroutineScope?.cancel()
        coroutineScope = null
        portSubscriptions.forEach(GuiSlotBackingSubscription::close)
        portSubscriptions.clear()
        resolvedPorts.clear()
        resolvedPortsByRawSlot.clear()
        stateValues.clear()
        renderedItems.clear()
        placeholderRawSlots.clear()
        pendingSlots.clear()
    }
}
