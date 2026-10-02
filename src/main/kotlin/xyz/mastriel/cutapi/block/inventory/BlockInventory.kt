package xyz.mastriel.cutapi.block.inventory

import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.block.CuTPlacedTileEntity
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.block.removeAttachment
import xyz.mastriel.cutapi.block.readDynamicAttachment
import xyz.mastriel.cutapi.block.setAttachment
import xyz.mastriel.cutapi.gui.GuiSlotBacking
import xyz.mastriel.cutapi.gui.GuiSlotBackingSubscription
import xyz.mastriel.cutapi.gui.GuiSlotBackingUpdate
import xyz.mastriel.cutapi.gui.GuiSlotUpdateSource
import java.util.UUID

public class BlockInventory internal constructor(
    public val tile: CuTPlacedTileEntity,
    public val definition: BlockInventoryDefinition,
) {
    private val items: Array<ItemStack?> = arrayOfNulls(definition.size)
    private val slotListeners: Array<MutableSet<(GuiSlotBackingUpdate) -> Unit>> =
        Array(definition.size) { linkedSetOf() }
    private var loadFailure: Throwable? = null

    public val size: Int get() = definition.size
    public val isAvailable: Boolean get() = loadFailure == null
    public val failureOrNull: Throwable? get() = loadFailure

    init {
        load()
    }

    public fun getItem(slot: Int): ItemStack? {
        requireUsableSlot(slot)
        return items[slot]?.clone()
    }

    public fun setItem(slot: Int, item: ItemStack?) {
        requireUnlocked()
        requireUsableSlot(slot)
        val normalized = item?.takeUnless { it.type.isAir || it.amount <= 0 }?.clone()
        if (normalized != null) validateCandidate(slot, normalized, BlockInventorySourceKind.Programmatic, null)
        commit(slot, normalized, BlockInventorySourceKind.Programmatic)
    }

    public fun clear(slot: Int) {
        setItem(slot, null)
    }

    public fun clear() {
        requireUnlocked()
        requireAvailable()
        val changes = items.indices.filter { items[it] != null }
        changes.forEach { slot -> commit(slot, null, BlockInventorySourceKind.Programmatic, persistNow = false) }
        if (changes.isNotEmpty()) persistAndRefresh()
    }

    public fun contents(): List<ItemStack?> {
        requireAvailable()
        return items.map { it?.clone() }
    }

    public fun tryInsert(slot: Int, item: ItemStack, face: BlockFace? = null): ItemStack {
        val source = if (face == null) BlockInventorySourceKind.Programmatic else BlockInventorySourceKind.Automation
        return insert(slot, item, face, source)
    }

    private fun insert(
        slot: Int,
        item: ItemStack,
        face: BlockFace?,
        source: BlockInventorySourceKind,
        playerInsertionAllowed: Boolean? = null,
    ): ItemStack {
        requireUnlocked()
        requireUsableSlot(slot)
        val input = item.clone()
        if (input.type.isAir || input.amount <= 0) return input
        val slotDefinition = definition.slot(slot)
        if (!(playerInsertionAllowed ?: slotDefinition.permitsInsertion(face))) return input
        validateCandidate(slot, input, source, face)
        val current = items[slot]
        if (current != null && !current.isSimilar(input)) return input
        val max = minOf(input.maxStackSize, current?.maxStackSize ?: input.maxStackSize)
        val room = max - (current?.amount ?: 0)
        if (room <= 0) return input
        val moved = minOf(room, input.amount)
        val resulting = (current?.clone() ?: input.clone().also { it.amount = 0 }).also { it.amount += moved }
        commit(slot, resulting, source)
        return input.also { it.amount -= moved }
    }

    public fun tryExtract(slot: Int, amount: Int, face: BlockFace? = null): ItemStack? {
        val source = if (face == null) BlockInventorySourceKind.Programmatic else BlockInventorySourceKind.Automation
        return extract(slot, amount, face, source)
    }

    internal fun extractFromAutomation(slot: Int, amount: Int): ItemStack? =
        extract(slot, amount, null, BlockInventorySourceKind.Automation)

    private fun extract(
        slot: Int,
        amount: Int,
        face: BlockFace?,
        source: BlockInventorySourceKind,
        playerExtractionAllowed: Boolean? = null,
    ): ItemStack? {
        require(amount > 0) { "Extraction amount must be positive." }
        requireUnlocked()
        requireUsableSlot(slot)
        val slotDefinition = definition.slot(slot)
        if (!(playerExtractionAllowed ?: slotDefinition.permitsExtraction(face))) return null
        val current = items[slot] ?: return null
        val moved = minOf(amount, current.amount)
        val extracted = current.clone().also { it.amount = moved }
        val remaining = current.amount - moved
        commit(slot, current.clone().takeIf { remaining > 0 }?.also { it.amount = remaining }, source)
        return extracted
    }

    internal fun guiSlotBacking(slot: Int): GuiSlotBacking {
        requireUsableSlot(slot)
        return BlockInventoryGuiSlotBacking(this, slot)
    }

    private fun setFromGui(slot: Int, item: ItemStack?, source: GuiSlotUpdateSource) {
        requireUnlocked()
        requireUsableSlot(slot)
        val normalized = item?.takeUnless { it.type.isAir || it.amount <= 0 }?.clone()
        if (normalized != null) validateCandidate(slot, normalized, source.toBlockSourceKind(), null)
        commit(slot, normalized, source.toBlockSourceKind())
    }

    private fun insertFromGui(slot: Int, item: ItemStack, source: GuiSlotUpdateSource): ItemStack =
        try {
            insert(
                slot = slot,
                item = item,
                face = null,
                source = source.toBlockSourceKind(),
                playerInsertionAllowed = true,
            )
        } catch (_: IllegalArgumentException) {
            item.clone()
        }

    private fun extractFromGui(slot: Int, amount: Int, source: GuiSlotUpdateSource): ItemStack? =
        extract(
            slot = slot,
            amount = amount,
            face = null,
            source = source.toBlockSourceKind(),
            playerExtractionAllowed = true,
        )

    private fun subscribeToSlot(
        slot: Int,
        listener: (GuiSlotBackingUpdate) -> Unit,
    ): GuiSlotBackingSubscription {
        requireUsableSlot(slot)
        slotListeners[slot] += listener
        return GuiSlotBackingSubscription { slotListeners[slot] -= listener }
    }

    internal fun canInsert(slot: Int, item: ItemStack, face: BlockFace?): Boolean {
        if (!isAvailable || slot !in 0 until size || item.type.isAir) return false
        val slotDefinition = definition.slot(slot)
        if (!slotDefinition.permitsInsertion(face)) return false
        return runCatching {
            validateCandidate(slot, item, BlockInventorySourceKind.Automation, face)
            val current = items[slot]
            (current == null || current.isSimilar(item)) && (current?.amount ?: 0) < item.maxStackSize
        }.getOrDefault(false)
    }

    internal fun canExtract(slot: Int, face: BlockFace?): Boolean {
        if (!isAvailable || slot !in 0 until size || items[slot] == null) return false
        return definition.slot(slot).permitsExtraction(face)
    }

    internal fun setFromAutomation(slot: Int, item: ItemStack?) {
        requireUnlocked()
        requireUsableSlot(slot)
        val previous = items[slot]
        val next = item?.takeUnless { it.type.isAir || it.amount <= 0 }?.clone()
        if (sameContents(previous, next)) return
        val slotDefinition = definition.slot(slot)
        val inserts = when {
            next == null -> false
            previous == null -> true
            !previous.isSimilar(next) -> true
            else -> next.amount > previous.amount
        }
        val extracts = when {
            previous == null -> false
            next == null -> true
            !previous.isSimilar(next) -> true
            else -> next.amount < previous.amount
        }
        require(!inserts || slotDefinition.permitsInsertion(null)) { "Automation cannot insert into slot $slot." }
        require(!extracts || slotDefinition.permitsExtraction(null)) { "Automation cannot extract from slot $slot." }
        if (next != null) validateCandidate(slot, next, BlockInventorySourceKind.Automation, null)
        commit(slot, next, BlockInventorySourceKind.Automation)
    }

    internal fun flushPending() {
        val entity = runCatching { tile.nativeEntity() }.getOrNull() ?: return
        entity.flushInventoryChanges()
    }

    internal fun snapshot(): BlockContents? {
        requireAvailable()
        val nonempty = items.mapIndexedNotNull { slot, stack -> stack?.let { slot to it.clone() } }.toMap()
        return nonempty.takeIf(Map<Int, ItemStack>::isNotEmpty)?.let {
            BlockContents.of(definition.id, size, it)
        }
    }

    private fun validateCandidate(
        slot: Int,
        item: ItemStack,
        source: BlockInventorySourceKind,
        face: BlockFace?,
    ) {
        require(item.amount in 1..item.maxStackSize) {
            "Block inventory ${definition.id} slot $slot received ${item.amount}, above its stack limit ${item.maxStackSize}."
        }
        val context = BlockInventoryAcceptanceContext(tile, slot, item.clone(), source, face)
        require(definition.slot(slot).accepts(context)) {
            "Block inventory ${definition.id} slot $slot rejected ${item.type}."
        }
    }

    private fun commit(
        slot: Int,
        item: ItemStack?,
        source: BlockInventorySourceKind,
        persistNow: Boolean = true,
    ) {
        val previous = items[slot]?.clone()
        items[slot] = item?.clone()
        try {
            val context = BlockInventoryChangeContext(tile, slot, previous, item?.clone(), source)
            definition.changeHandlers.forEach { handler -> handler(context) }
        } catch (failure: Throwable) {
            items[slot] = previous?.clone()
            invalidateNativeInventorySlot(slot)
            throw failure
        }
        invalidateNativeInventorySlot(slot)
        if (!sameContents(previous, item)) {
            val update = GuiSlotBackingUpdate(previous, item, source.toGuiSlotUpdateSource())
            slotListeners[slot].toList().forEach { listener -> listener(update) }
        }
        if (persistNow) persistAndRefresh()
    }

    private fun sameContents(first: ItemStack?, second: ItemStack?): Boolean {
        if (first == null || first.type.isAir) return second == null || second.type.isAir
        if (second == null || second.type.isAir) return false
        return first.amount == second.amount && first.isSimilar(second)
    }

    private fun persistAndRefresh() {
        val nonempty = items.mapIndexedNotNull { slot, stack -> stack?.let { slot to it.clone() } }.toMap()
        if (nonempty.isEmpty()) {
            tile.removeAttachment(BlockContents)
        } else {
            tile.setAttachment(BlockContents.of(definition.id, size, nonempty))
        }
        runCatching { tile.nativeEntity().setChanged() }
        CuTAPI.guiManager.refreshBlock(tile)
    }

    private fun invalidateNativeInventorySlot(slot: Int) {
        runCatching { tile.nativeEntity().invalidateInventorySlot(slot) }
    }

    private fun load() {
        try {
            val stored = tile.readDynamicAttachment(BlockContents).getOrThrow() ?: return
            require(stored.inventoryId == definition.id) {
                "Stored inventory id ${stored.inventoryId} does not match ${definition.id}."
            }
            require(stored.size == size) { "Stored inventory size ${stored.size} does not match $size." }
            for ((slot, stack) in stored.entries) {
                validateCandidate(slot, stack, BlockInventorySourceKind.Programmatic, null)
                items[slot] = stack.clone()
            }
        } catch (failure: Throwable) {
            loadFailure = failure
        }
    }

    private fun requireUnlocked() {
        requireAvailable()
    }

    private fun requireUsableSlot(slot: Int) {
        requireAvailable()
        if (slot !in 0 until size) throw IndexOutOfBoundsException("Slot $slot is outside 0 until $size.")
    }

    private fun requireAvailable() {
        val failure = loadFailure
        check(failure == null) { "Block inventory ${definition.id} is unavailable: ${failure?.message}" }
    }

    private class BlockInventoryGuiSlotBacking(
        private val inventory: BlockInventory,
        private val slot: Int,
    ) : GuiSlotBacking {
        override val item: ItemStack?
            get() = inventory.getItem(slot)

        override fun setItem(item: ItemStack?, source: GuiSlotUpdateSource) {
            inventory.setFromGui(slot, item, source)
        }

        override fun tryInsert(item: ItemStack, source: GuiSlotUpdateSource): ItemStack =
            inventory.insertFromGui(slot, item, source)

        override fun tryExtract(amount: Int, source: GuiSlotUpdateSource): ItemStack? =
            inventory.extractFromGui(slot, amount, source)

        override fun subscribe(listener: (GuiSlotBackingUpdate) -> Unit): GuiSlotBackingSubscription =
            inventory.subscribeToSlot(slot, listener)
    }
}

private fun GuiSlotUpdateSource.toBlockSourceKind(): BlockInventorySourceKind = when (this) {
    GuiSlotUpdateSource.Player -> BlockInventorySourceKind.Player
    GuiSlotUpdateSource.Programmatic -> BlockInventorySourceKind.Programmatic
    GuiSlotUpdateSource.Automation -> BlockInventorySourceKind.Automation
}

private fun BlockInventorySourceKind.toGuiSlotUpdateSource(): GuiSlotUpdateSource = when (this) {
    BlockInventorySourceKind.Player -> GuiSlotUpdateSource.Player
    BlockInventorySourceKind.Programmatic -> GuiSlotUpdateSource.Programmatic
    BlockInventorySourceKind.Automation -> GuiSlotUpdateSource.Automation
}

private data class BlockInventoryKey(
    val worldId: UUID,
    val x: Int,
    val y: Int,
    val z: Int,
)

internal object BlockInventoryStore {
    private val inventories: MutableMap<BlockInventoryKey, BlockInventory> = mutableMapOf()

    fun get(tile: CuTPlacedTileEntity): BlockInventory? {
        val definition = (tile.identity.customTile as? CustomTileEntity<*>)?.descriptor?.inventory ?: return null
        val key = BlockInventoryKey(tile.handle.world.uid, tile.handle.x, tile.handle.y, tile.handle.z)
        return inventories.getOrPut(key) { BlockInventory(tile, definition) }
    }

    fun remove(tile: CuTPlacedTileEntity): BlockInventory? {
        val key = BlockInventoryKey(tile.handle.world.uid, tile.handle.x, tile.handle.y, tile.handle.z)
        return inventories.remove(key)
    }

    fun all(): List<BlockInventory> = inventories.values.toList()

    fun install(tile: CuTPlacedTileEntity, contents: BlockContents) {
        val tileDefinition = tile.identity.customTile as? CustomTileEntity<*>
            ?: error("Block at ${tile.location} is not a native custom tile entity.")
        val definition = tileDefinition.descriptor.inventory
            ?: error("Custom tile ${tileDefinition.id} cannot receive block contents because it has no inventory.")
        require(contents.inventoryId == definition.id) {
            "Content inventory id ${contents.inventoryId} does not match ${definition.id}."
        }
        require(contents.size == definition.size) {
            "Content inventory size ${contents.size} does not match ${definition.size}."
        }
        for ((slot, stack) in contents.entries) {
            require(slot in 0 until definition.size) { "Content slot $slot is outside the destination inventory." }
            require(stack.amount in 1..stack.maxStackSize) { "Content slot $slot exceeds its stack limit." }
            val accepted = definition.slot(slot).accepts(
                BlockInventoryAcceptanceContext(
                    tile = tile,
                    slot = slot,
                    candidate = stack.clone(),
                    source = BlockInventorySourceKind.Programmatic,
                    face = null,
                ),
            )
            require(accepted) { "Destination slot $slot rejects ${stack.type}." }
        }
        tile.setAttachment(contents)
        remove(tile)
        val installed = get(tile) ?: error("Installed block inventory could not be created.")
        check(installed.isAvailable) { "Installed block contents failed to load: ${installed.failureOrNull?.message}" }
    }

    fun removeChunk(worldId: UUID, chunkX: Int, chunkZ: Int): List<BlockInventory> {
        val matches = inventories.filterKeys { key ->
            key.worldId == worldId && (key.x shr 4) == chunkX && (key.z shr 4) == chunkZ
        }
        matches.keys.forEach(inventories::remove)
        return matches.values.toList()
    }

    fun clear() {
        inventories.clear()
    }
}

public val CuTPlacedTileEntity.inventoryOrNull: BlockInventory?
    get() = BlockInventoryStore.get(this)

public fun CuTPlacedTileEntity.requireInventory(): BlockInventory =
    inventoryOrNull ?: error("Custom tile ${identity.customTile?.id ?: identity} does not define an inventory.")
