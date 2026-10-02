package xyz.mastriel.cutapi.block.inventory

import org.bukkit.block.*
import org.bukkit.entity.*
import org.bukkit.inventory.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.gui.*
import xyz.mastriel.cutapi.registry.*

public enum class BlockInventorySlotAccess {
    Input,
    Output,
    Storage,
}

public enum class BlockInventoryBreakPolicy {
    DropContents,
    KeepContents,
    DeleteContents,
}

public enum class BlockInventorySourceKind {
    Player,
    Automation,
    Programmatic,
}

internal fun GuiBoundSlotAccess.permitsPlayerInsertion(role: BlockInventorySlotAccess): Boolean =
    permitsInsertionOverride ?: (role != BlockInventorySlotAccess.Output)

internal fun GuiBoundSlotAccess.permitsPlayerExtraction(role: BlockInventorySlotAccess): Boolean =
    permitsExtractionOverride ?: true

public data class BlockInventoryAcceptanceContext(
    public val tile: CuTPlacedTileEntity,
    public val slot: Int,
    public val candidate: ItemStack,
    public val source: BlockInventorySourceKind,
    public val face: BlockFace?,
)

public data class BlockInventoryChangeContext(
    public val tile: CuTPlacedTileEntity,
    public val slot: Int,
    public val previous: ItemStack?,
    public val item: ItemStack?,
    public val source: BlockInventorySourceKind,
)

public class BlockInventorySlotDefinition internal constructor(
    public val index: Int,
    public val access: BlockInventorySlotAccess,
    public val insertFaces: Set<BlockFace>?,
    public val extractFaces: Set<BlockFace>?,
    internal val acceptance: (BlockInventoryAcceptanceContext.(ItemStack) -> Boolean)?,
) {
    public fun accepts(context: BlockInventoryAcceptanceContext): Boolean =
        acceptance?.invoke(context, context.candidate.clone()) ?: true

    public fun permitsInsertion(face: BlockFace?): Boolean =
        access != BlockInventorySlotAccess.Output && (face == null || insertFaces == null || face in insertFaces)

    public fun permitsExtraction(face: BlockFace?): Boolean =
        access != BlockInventorySlotAccess.Input && (face == null || extractFaces == null || face in extractFaces)
}

/** Values available while producing a contextual block presentation's per-viewer GUI context. */
public class BlockGuiOpenContext internal constructor(
    public val player: Player,
    public val tile: CuTPlacedTileEntity,
    public val inventory: BlockInventory,
)

public class BlockInventoryPresentation<C, V : InventoryView> internal constructor(
    guiDefinition: GuiDefinition<C, V>,
    bindings: Map<Int, GuiSlotPort>,
    internal val contextProducer: BlockGuiOpenContext.() -> C,
) {
    private val guiDefinition: GuiDefinition<C, V> = guiDefinition
    public val gui: GuiDefinition<C, V> get() = guiDefinition
    public val bindings: Map<Int, GuiSlotPort> = bindings.toMap()
}

public class BlockInventoryDefinition internal constructor(
    public val id: Identifier,
    public val size: Int,
    slots: List<BlockInventorySlotDefinition>,
    public val breakPolicy: BlockInventoryBreakPolicy,
    public val requireCorrectToolForContents: Boolean,
    public val openOnRightClick: Boolean,
    public val presentation: BlockInventoryPresentation<*, out InventoryView>?,
    internal val changeHandlers: List<BlockInventoryChangeContext.() -> Unit>,
) {
    private val slotsByIndex: Map<Int, BlockInventorySlotDefinition> = slots.associateBy { it.index }

    public fun slot(index: Int): BlockInventorySlotDefinition =
        slotsByIndex[index] ?: throw IndexOutOfBoundsException("Block inventory $id has no slot $index.")

    public fun slots(): List<BlockInventorySlotDefinition> = slotsByIndex.values.sortedBy { it.index }

    internal fun validateItemPolicy(policy: BlockItemPolicy) {
        if (breakPolicy != BlockInventoryBreakPolicy.KeepContents) return
        require(policy !is BlockItemPolicy.None) {
            "Block inventory $id uses KeepContents but its tile has no placement item."
        }
        require(policy !is BlockItemPolicy.Item || policy.consumesItem) {
            "Block inventory $id uses KeepContents but its placement item is not consumed outside creative mode."
        }
    }
}

@xyz.mastriel.cutapi.gui.GuiDslMarker
public class BlockInventorySlotBuilder internal constructor(
    private val index: Int,
    private val access: BlockInventorySlotAccess,
) {
    private var acceptance: (BlockInventoryAcceptanceContext.(ItemStack) -> Boolean)? = null
    private var insertionFaces: Set<BlockFace>? = null
    private var extractionFaces: Set<BlockFace>? = null

    public fun accepts(predicate: BlockInventoryAcceptanceContext.(ItemStack) -> Boolean) {
        require(access != BlockInventorySlotAccess.Output) {
            "Output slot $index cannot declare an insertion predicate."
        }
        check(acceptance == null) { "Slot $index declares more than one acceptance predicate." }
        acceptance = predicate
    }

    public fun insertFaces(vararg faces: BlockFace) {
        require(access != BlockInventorySlotAccess.Output) { "Output slot $index cannot declare insertion faces." }
        require(faces.isNotEmpty()) { "Insertion faces may not be empty; omit insertFaces to allow every face." }
        insertionFaces = faces.toSet()
    }

    public fun extractFaces(vararg faces: BlockFace) {
        require(access != BlockInventorySlotAccess.Input) { "Input slot $index cannot declare extraction faces." }
        require(faces.isNotEmpty()) { "Extraction faces may not be empty; omit extractFaces to allow every face." }
        extractionFaces = faces.toSet()
    }

    internal fun build(): BlockInventorySlotDefinition = BlockInventorySlotDefinition(
        index = index,
        access = access,
        insertFaces = insertionFaces,
        extractFaces = extractionFaces,
        acceptance = acceptance,
    )
}

@xyz.mastriel.cutapi.gui.GuiDslMarker
public class BlockInventoryPresentationBuilder<C, V : InventoryView> internal constructor(
    private val storageSize: Int,
    private val gui: () -> GuiDefinition<C, V>,
    private val contextProducer: BlockGuiOpenContext.() -> C,
) {
    private val bindings: MutableMap<Int, GuiSlotPort> = linkedMapOf()

    /** Connects canonical block storage to a semantic port declared by the presentation GUI. */
    public fun bind(storageSlot: Int, to: GuiSlotPort) {
        require(storageSlot in 0 until storageSize) { "Storage slot $storageSlot is outside 0 until $storageSize." }
        require(storageSlot !in bindings) { "Storage slot $storageSlot is bound more than once." }
        require(to !in bindings.values) { "GUI slot port '${to.name}' is bound more than once." }
        bindings[storageSlot] = to
    }

    internal fun build(): BlockInventoryPresentation<C, V> {
        val definition = gui()
        for (port in bindings.values) {
            require(port in definition.slotPorts) {
                "Block inventory presentation for ${definition.id} binds undeclared slot port '${port.name}'."
            }
        }
        val missingRequired = definition.requiredSlotPorts - bindings.values.toSet()
        require(missingRequired.isEmpty()) {
            "Block inventory presentation for ${definition.id} does not bind required slot ports: " +
                missingRequired.joinToString { "'${it.name}'" }
        }
        return BlockInventoryPresentation(definition, bindings, contextProducer)
    }
}

@xyz.mastriel.cutapi.gui.GuiDslMarker
public class BlockInventoryDefinitionBuilder internal constructor(
    private val id: Identifier,
    private val size: Int,
) {
    public var breakPolicy: BlockInventoryBreakPolicy = BlockInventoryBreakPolicy.DropContents
    public var requireCorrectToolForContents: Boolean = false
    public var openOnRightClick: Boolean = true

    private val slots: MutableMap<Int, BlockInventorySlotDefinition> = linkedMapOf()
    private var presentation: BlockInventoryPresentation<*, out InventoryView>? = null
    private val changeHandlers: MutableList<BlockInventoryChangeContext.() -> Unit> = mutableListOf()

    init {
        require(size in 1..54) { "Block inventory $id size must be between 1 and 54, found $size." }
    }

    public fun slot(
        index: Int,
        access: BlockInventorySlotAccess,
        configure: BlockInventorySlotBuilder.() -> Unit = {},
    ) {
        require(index in 0 until size) { "Block inventory $id slot $index is outside 0 until $size." }
        require(index !in slots) { "Block inventory $id declares slot $index more than once." }
        slots[index] = BlockInventorySlotBuilder(index, access).apply(configure).build()
    }

    /** Declares a presentation for a context-free GUI definition. */
    public fun <V : InventoryView> presentation(
        gui: () -> GuiDefinition<Unit, V>,
        configure: BlockInventoryPresentationBuilder<Unit, V>.() -> Unit,
    ) {
        check(presentation == null) { "Block inventory $id declares more than one presentation." }
        presentation = BlockInventoryPresentationBuilder(size, gui) { Unit }.apply(configure).build()
    }

    /** Declares a contextual presentation; [context] is required and runs independently for every open. */
    public fun <C, V : InventoryView> presentation(
        gui: () -> GuiDefinition<C, V>,
        context: BlockGuiOpenContext.() -> C,
        configure: BlockInventoryPresentationBuilder<C, V>.() -> Unit,
    ) {
        check(presentation == null) { "Block inventory $id declares more than one presentation." }
        presentation = BlockInventoryPresentationBuilder(size, gui, context).apply(configure).build()
    }

    public fun onChange(handler: BlockInventoryChangeContext.() -> Unit) {
        changeHandlers += handler
    }

    internal fun build(): BlockInventoryDefinition {
        val missing = (0 until size).filterNot(slots::containsKey)
        require(missing.isEmpty()) { "Block inventory $id is missing slot declarations: $missing." }
        return BlockInventoryDefinition(
            id = id,
            size = size,
            slots = slots.values.toList(),
            breakPolicy = breakPolicy,
            requireCorrectToolForContents = requireCorrectToolForContents,
            openOnRightClick = openOnRightClick,
            presentation = presentation,
            changeHandlers = changeHandlers.toList(),
        )
    }
}
