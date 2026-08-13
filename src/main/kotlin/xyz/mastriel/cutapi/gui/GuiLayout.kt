package xyz.mastriel.cutapi.gui

public data class GuiSlotPosition(public val x: Int, public val y: Int)

public class GuiLayout(
    public val topSize: Int,
    public val columns: Int,
    public val rows: Int,
    roles: Map<GuiSlotRole, Collection<Int>> = emptyMap(),
    positions: Map<Int, GuiSlotPosition> = emptyMap(),
) {
    private val roleSlots: Map<GuiSlotRole, List<Int>> = roles.mapValues { (_, value) -> value.distinct() }
    private val slotPositions: Map<Int, GuiSlotPosition> = if (positions.isEmpty()) {
        (0 until topSize).associateWith { index ->
            GuiSlotPosition(index % columns, index / columns)
        }
    } else {
        positions.toMap()
    }

    init {
        require(topSize > 0) { "A GUI layout must contain at least one top slot." }
        require(columns > 0 && rows > 0) { "GUI layout dimensions must be positive." }
        require(roleSlots.values.flatten().all { it in 0 until topSize }) {
            "A GUI slot role references a slot outside 0 until $topSize."
        }
        require(slotPositions.keys.all { it in 0 until topSize }) {
            "A GUI slot position references a slot outside 0 until $topSize."
        }
    }

    public fun isValid(slot: GuiSlot): Boolean = when (slot) {
        is GuiSlot.Top -> slot.index in 0 until topSize
        is GuiSlot.Player -> slot.index in 0 until 36
        is GuiSlot.Raw -> slot.index in 0 until topSize + 36
        GuiSlot.Outside -> true
    }

    public fun fromRaw(rawSlot: Int): GuiSlot = when {
        rawSlot == org.bukkit.inventory.InventoryView.OUTSIDE -> GuiSlot.Outside
        rawSlot in 0 until topSize -> GuiSlot.Top(rawSlot)
        rawSlot in topSize until topSize + 36 -> GuiSlot.Player(rawSlot - topSize)
        else -> GuiSlot.Raw(rawSlot)
    }

    public fun toRaw(slot: GuiSlot): Int = when (slot) {
        is GuiSlot.Top -> slot.index
        is GuiSlot.Player -> topSize + slot.index
        is GuiSlot.Raw -> slot.index
        GuiSlot.Outside -> org.bukkit.inventory.InventoryView.OUTSIDE
    }

    public fun slots(role: GuiSlotRole): GuiSlotGroup =
        GuiSlotGroup(roleSlots[role].orEmpty().map(GuiSlot::Top))

    public fun position(slot: Int): GuiSlotPosition? = slotPositions[slot]

    public companion object {
        public fun grid(rows: Int, columns: Int = 9): GuiLayout {
            require(rows > 0) { "Grid rows must be positive." }
            require(columns > 0) { "Grid columns must be positive." }
            return GuiLayout(rows * columns, columns, rows)
        }
    }
}
