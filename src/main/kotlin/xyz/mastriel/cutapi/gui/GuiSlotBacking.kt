package xyz.mastriel.cutapi.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

public enum class GuiSlotUpdateSource {
    Player,
    Programmatic,
    Automation,
}

public class GuiSlotBackingUpdate(
    previous: ItemStack?,
    item: ItemStack?,
    public val source: GuiSlotUpdateSource,
) {
    private val previousSnapshot: ItemStack? = previous?.clone()
    private val itemSnapshot: ItemStack? = item?.clone()

    public val previous: ItemStack? get() = previousSnapshot?.clone()
    public val item: ItemStack? get() = itemSnapshot?.clone()
}

public fun interface GuiSlotBackingSubscription : AutoCloseable {
    override fun close()

    public companion object {
        public val Empty: GuiSlotBackingSubscription = GuiSlotBackingSubscription {}
    }
}

/** Canonical item storage that can be projected through a named GUI slot port. */
public interface GuiSlotBacking {
    /** Returns a defensive snapshot of the current item. */
    public val item: ItemStack?

    /** Replaces the canonical item. Implementations must retain a defensive clone. */
    public fun setItem(
        item: ItemStack?,
        source: GuiSlotUpdateSource = GuiSlotUpdateSource.Programmatic,
    )

    /** Inserts as much as possible and returns a defensive remainder. */
    public fun tryInsert(
        item: ItemStack,
        source: GuiSlotUpdateSource = GuiSlotUpdateSource.Programmatic,
    ): ItemStack

    /** Extracts at most [amount], or returns `null` when empty. */
    public fun tryExtract(
        amount: Int,
        source: GuiSlotUpdateSource = GuiSlotUpdateSource.Programmatic,
    ): ItemStack?

    /** Registers a listener for actual canonical content changes. */
    public fun subscribe(listener: (GuiSlotBackingUpdate) -> Unit): GuiSlotBackingSubscription =
        GuiSlotBackingSubscription.Empty
}

/** A primary-thread mutable backing suitable for standalone and externally supplied GUI slots. */
public class MutableGuiSlotBacking(
    initialize: () -> ItemStack? = { null },
) : GuiSlotBacking {
    private var storedItem: ItemStack? = normalize(initialize())
    private val listeners: MutableSet<(GuiSlotBackingUpdate) -> Unit> = linkedSetOf()

    override val item: ItemStack?
        get() {
            GuiExecution.requirePrimaryThread()
            return storedItem?.clone()
        }

    override fun setItem(item: ItemStack?, source: GuiSlotUpdateSource) {
        GuiExecution.requirePrimaryThread()
        val next = normalize(item)
        val previous = storedItem?.clone()
        if (sameStack(previous, next)) return
        storedItem = next?.clone()
        notifyListeners(previous, next, source)
    }

    override fun tryInsert(item: ItemStack, source: GuiSlotUpdateSource): ItemStack {
        GuiExecution.requirePrimaryThread()
        val input = item.clone()
        if (input.type.isAir || input.amount <= 0) return input
        val current = storedItem
        if (current != null && !current.isSimilar(input)) return input
        val maximum = minOf(input.maxStackSize, current?.maxStackSize ?: input.maxStackSize)
        val room = maximum - (current?.amount ?: 0)
        if (room <= 0) return input
        val moved = minOf(room, input.amount)
        val resulting = (current?.clone() ?: input.clone().also { it.amount = 0 })
            .also { it.amount += moved }
        setItem(resulting, source)
        return input.also { it.amount -= moved }
    }

    override fun tryExtract(amount: Int, source: GuiSlotUpdateSource): ItemStack? {
        GuiExecution.requirePrimaryThread()
        require(amount > 0) { "Extraction amount must be positive." }
        val current = storedItem ?: return null
        val moved = minOf(amount, current.amount)
        val extracted = current.clone().also { it.amount = moved }
        val remaining = current.amount - moved
        setItem(current.clone().takeIf { remaining > 0 }?.also { it.amount = remaining }, source)
        return extracted
    }

    override fun subscribe(listener: (GuiSlotBackingUpdate) -> Unit): GuiSlotBackingSubscription {
        GuiExecution.requirePrimaryThread()
        listeners += listener
        return GuiSlotBackingSubscription { listeners -= listener }
    }

    private fun notifyListeners(
        previous: ItemStack?,
        item: ItemStack?,
        source: GuiSlotUpdateSource,
    ) {
        listeners.toList().forEach { listener ->
            listener(GuiSlotBackingUpdate(previous, item, source))
        }
    }

    private fun normalize(item: ItemStack?): ItemStack? =
        item?.takeUnless { it.type.isAir || it.amount <= 0 }?.clone()?.also { stack ->
            require(stack.amount <= stack.maxStackSize) {
                "GUI slot backing received ${stack.amount} ${stack.type}, above its stack limit ${stack.maxStackSize}."
            }
        }
}

public class GuiSlotBackingInitializationContext<C> internal constructor(
    public val viewer: Player,
    public val subject: GuiSubject,
    public val port: GuiSlotPort,
    public val context: C,
)

@GuiDslMarker
public class GuiOpenBuilder<C> internal constructor(
    private val definition: GuiDefinition<C, *>,
) {
    internal val bindings: MutableMap<GuiSlotPort, GuiSlotBackingInitializationContext<C>.() -> GuiSlotBacking> =
        linkedMapOf()

    public fun bind(
        port: GuiSlotPort,
        backing: GuiSlotBackingInitializationContext<C>.() -> GuiSlotBacking,
    ) {
        require(port in definition.slotPorts) {
            "GUI ${definition.id} does not declare slot port '${port.name}'."
        }
        require(port !in bindings) {
            "GUI ${definition.id} open configuration binds slot port '${port.name}' more than once."
        }
        bindings[port] = backing
    }
}

internal data class GuiResolvedPort(
    val port: GuiSlotPort,
    val slot: GuiSlot,
    val rawSlot: Int,
    val backing: GuiSlotBacking?,
    val permitsPlayerInsertion: Boolean,
    val permitsPlayerExtraction: Boolean,
    val storageSlot: Int?,
)

public class GuiPortHandle internal constructor(
    private val session: GuiSession<*, *>,
    internal val resolved: GuiResolvedPort,
) {
    public val port: GuiSlotPort get() = resolved.port
    public val isBound: Boolean get() = resolved.backing != null
    public val isEmpty: Boolean get() = item == null

    /** Returns a defensive snapshot, or `null` for an empty or unbound port. */
    public val item: ItemStack?
        get() {
            requireUsableSession()
            return resolved.backing?.item?.clone()
        }

    public fun setItem(item: ItemStack?) {
        requireUsableSession()
        val backing = requireBacking()
        if (item != null && !item.type.isAir && item.amount > 0) {
            require(session.manager.acceptsPort(session, resolved, item)) {
                "GUI ${session.definition.id} slot port '${port.name}' rejected ${item.type}."
            }
        }
        backing.setItem(item?.clone(), GuiSlotUpdateSource.Programmatic)
    }

    public fun clear() {
        setItem(null)
    }

    public fun tryInsert(item: ItemStack): ItemStack {
        requireUsableSession()
        val input = item.clone()
        if (!session.manager.acceptsPort(session, resolved, input)) return input
        return requireBacking().tryInsert(input, GuiSlotUpdateSource.Programmatic).clone()
    }

    public fun tryExtract(amount: Int): ItemStack? {
        requireUsableSession()
        return requireBacking().tryExtract(amount, GuiSlotUpdateSource.Programmatic)?.clone()
    }

    public fun update(transform: (ItemStack?) -> ItemStack?) {
        setItem(transform(item?.clone()))
    }

    private fun requireBacking(): GuiSlotBacking = resolved.backing ?: error(
        "GUI ${session.definition.id} slot port '${port.name}' has no backing in session ${session.id}.",
    )

    private fun requireUsableSession() {
        GuiExecution.requirePrimaryThread()
        check(!session.isClosed) { "GUI slot port '${port.name}' was accessed after session ${session.id} closed." }
    }
}

private fun sameStack(first: ItemStack?, second: ItemStack?): Boolean {
    if (first == null || first.type.isAir) return second == null || second.type.isAir
    if (second == null || second.type.isAir) return false
    return first.amount == second.amount && first.isSimilar(second)
}
