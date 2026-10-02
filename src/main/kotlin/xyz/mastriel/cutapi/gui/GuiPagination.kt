package xyz.mastriel.cutapi.gui

import org.bukkit.Material
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack

@GuiDslMarker
public class GuiPaginationBuilder<C, V : InventoryView, E : Any> internal constructor() {
    private var entriesProducer: (GuiRenderContext<C, V>.() -> List<E>)? = null
    private var itemProducer: (GuiRenderContext<C, V>.(E) -> ItemStack?)? = null
    private var clickHandler: (GuiInteractionContext<C, V>.(E) -> Unit)? = null
    private var previousSlot: Int? = null
    private var nextSlot: Int? = null
    private var previousItem: GuiRenderContext<C, V>.() -> ItemStack? = { ItemStack(Material.ARROW) }
    private var nextItem: GuiRenderContext<C, V>.() -> ItemStack? = { ItemStack(Material.ARROW) }

    public fun entries(produce: GuiRenderContext<C, V>.() -> List<E>) {
        check(entriesProducer == null) { "Pagination entries may only be declared once." }
        entriesProducer = produce
    }

    public fun item(produce: GuiRenderContext<C, V>.(E) -> ItemStack?) {
        check(itemProducer == null) { "Pagination item rendering may only be declared once." }
        itemProducer = produce
    }

    public fun onClick(handler: GuiInteractionContext<C, V>.(E) -> Unit) {
        check(clickHandler == null) { "Pagination click handling may only be declared once." }
        clickHandler = handler
    }

    public fun previous(
        slot: Int,
        item: GuiRenderContext<C, V>.() -> ItemStack? = { ItemStack(Material.ARROW) },
    ) {
        check(previousSlot == null) { "A paginator may only declare one previous control." }
        previousSlot = slot
        previousItem = item
    }

    public fun next(
        slot: Int,
        item: GuiRenderContext<C, V>.() -> ItemStack? = { ItemStack(Material.ARROW) },
    ) {
        check(nextSlot == null) { "A paginator may only declare one next control." }
        nextSlot = slot
        nextItem = item
    }

    internal fun install(owner: GuiBuilder<C, V>, key: String, slots: GuiSlotGroup) {
        val entrySlots = slots.toList()
        require(entrySlots.isNotEmpty()) { "Paginator '$key' requires at least one entry slot." }
        require(entrySlots.all { it is GuiSlot.Top }) { "Paginator '$key' only supports top-inventory slots." }
        val previous = previousSlot
        val next = nextSlot
        val renderPrevious = previousItem
        val renderNext = nextItem
        val renderPreviousFallback = previous?.let { owner.itemFallback(GuiSlot.Top(it)) }
        val renderNextFallback = next?.let { owner.itemFallback(GuiSlot.Top(it)) }
        require(previous == null || GuiSlot.Top(previous) !in slots) {
            "Paginator '$key' previous control overlaps its entries."
        }
        require(next == null || GuiSlot.Top(next) !in slots) {
            "Paginator '$key' next control overlaps its entries."
        }
        require(previous == null || next == null || previous != next) {
            "Paginator '$key' navigation controls overlap."
        }
        val entries = requireNotNull(entriesProducer) { "Paginator '$key' must declare entries { ... }." }
        val renderItem = requireNotNull(itemProducer) { "Paginator '$key' must declare item { entry -> ... }." }
        val click = clickHandler
        var currentPage by owner.state<Int> { 0 }

        entrySlots.forEachIndexed { index, slot ->
            owner.slot(slot) {
                this.key = "$key/entry/$index"
                item {
                    val values = entries(this)
                    val pageCount = ((values.size + entrySlots.size - 1) / entrySlots.size).coerceAtLeast(1)
                    val clamped = currentPage.coerceIn(0, pageCount - 1)
                    if (currentPage != clamped) currentPage = clamped
                    values.getOrNull(clamped * entrySlots.size + index)?.let { entry -> renderItem(this, entry) }
                }
                if (click != null) {
                    onClick {
                        val values = entries(GuiRenderContext(session))
                        val entry = values.getOrNull(currentPage * entrySlots.size + index) ?: return@onClick
                        click(this, entry)
                    }
                }
            }
        }

        if (previous != null) {
            owner.slot(previous) {
                this.key = "$key/previous"
                item {
                    if (currentPage > 0) renderPrevious(this) else renderPreviousFallback?.invoke(this)
                }
                onClick {
                    if (currentPage > 0) currentPage -= 1
                }
            }
        }
        if (next != null) {
            owner.slot(next) {
                this.key = "$key/next"
                item {
                    val values = entries(this)
                    if ((currentPage + 1) * entrySlots.size < values.size) {
                        renderNext(this)
                    } else {
                        renderNextFallback?.invoke(this)
                    }
                }
                onClick {
                    val values = entries(GuiRenderContext(session))
                    if ((currentPage + 1) * entrySlots.size < values.size) currentPage += 1
                }
            }
        }
    }
}

public class GuiComponent<C, V : InventoryView> internal constructor(
    internal val install: GuiComponentScope<C, V>.() -> Unit,
)

public fun <V : InventoryView> guiComponent(
    install: GuiComponentScope<Unit, V>.() -> Unit,
): GuiComponent<Unit, V> = GuiComponent(install)

public fun <C, V : InventoryView> guiComponent(
    @Suppress("UNUSED_PARAMETER") context: GuiContextType<C>,
    install: GuiComponentScope<C, V>.() -> Unit,
): GuiComponent<C, V> = GuiComponent(install)

@GuiDslMarker
public class GuiComponentInclusionBuilder<C, V : InventoryView> internal constructor() {
    private val callbacks: MutableMap<String, GuiInteractionContext<C, V>.() -> Unit> = linkedMapOf()

    public fun bind(name: String, callback: GuiInteractionContext<C, V>.() -> Unit) {
        require(name.isNotBlank()) { "Component callback names may not be blank." }
        require(name !in callbacks) { "Component callback '$name' is bound more than once." }
        callbacks[name] = callback
    }

    internal fun bindings(): Map<String, GuiInteractionContext<C, V>.() -> Unit> = callbacks.toMap()
}

@GuiDslMarker
public class GuiComponentScope<C, V : InventoryView> internal constructor(
    private val owner: GuiBuilder<C, V>,
    private val namespace: String,
    private val callbacks: Map<String, GuiInteractionContext<C, V>.() -> Unit>,
) {
    public fun slot(index: Int, configure: GuiSlotBuilder<C, V>.() -> Unit) {
        val componentNamespace = namespace
        owner.slot(index) {
            key = "$componentNamespace/slot/$index"
            configure()
        }
    }

    public fun callback(name: String): GuiInteractionContext<C, V>.() -> Unit =
        callbacks[name] ?: error("Reusable GUI component requires callback binding '$name'.")

    public fun <T> state(initialize: GuiStateInitializationContext<C>.() -> T): GuiStateDelegateProvider<C, T> =
        owner.state(initialize)
}

private var componentInclusionSequence: Long = 0

public fun <C, V : InventoryView> GuiBuilder<C, V>.include(
    component: GuiComponent<C, V>,
    configure: GuiComponentInclusionBuilder<C, V>.() -> Unit = {},
) {
    val inclusion = GuiComponentInclusionBuilder<C, V>().apply(configure)
    val namespace = "component/${componentInclusionSequence++}"
    component.install(GuiComponentScope(this, namespace, inclusion.bindings()))
}
