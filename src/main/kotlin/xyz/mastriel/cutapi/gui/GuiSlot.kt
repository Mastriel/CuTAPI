package xyz.mastriel.cutapi.gui

public sealed interface GuiSlot {
    public data class Top(public val index: Int) : GuiSlot
    public data class Player(public val index: Int) : GuiSlot
    public data class Raw(public val index: Int) : GuiSlot
    public data object Outside : GuiSlot
}

/**
 * An opaque semantic storage port whose visual position is owned by a GUI definition.
 */
public data class GuiSlotPort(public val name: String) {
    init {
        require(name.isNotBlank()) { "A GUI slot port name may not be blank." }
    }

    override fun toString(): String = "GuiSlotPort($name)"
}

public class GuiSlotGroup internal constructor(slots: Collection<GuiSlot>) : Iterable<GuiSlot> {
    private val orderedSlots: List<GuiSlot> = slots.distinct()

    public val size: Int get() = orderedSlots.size

    public operator fun contains(slot: GuiSlot): Boolean = slot in orderedSlots

    public fun toList(): List<GuiSlot> = orderedSlots.toList()

    override fun iterator(): Iterator<GuiSlot> = orderedSlots.iterator()
}

public enum class GuiSlotRole {
    Input,
    Fuel,
    Ingredient,
    Result,
    Payment,
    Book,
    Bottle,
    Player,
}
