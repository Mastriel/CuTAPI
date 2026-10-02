package xyz.mastriel.cutapi.block

import org.bukkit.*
import org.bukkit.block.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.block.nativeblock.*
import org.bukkit.craftbukkit.*
import net.minecraft.core.*
import org.bukkit.persistence.PersistentDataContainer
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.system.*

internal data class PlacedTileConstructionSnapshot(
    val identity: BlockIdentity.Custom,
    val state: CustomBlockState,
)

internal object PlacedTileConstructionContext {
    private val current: ThreadLocal<PlacedTileConstructionSnapshot?> = ThreadLocal()

    fun snapshot(): PlacedTileConstructionSnapshot? = current.get()

    fun <T> withSnapshot(snapshot: PlacedTileConstructionSnapshot, create: () -> T): T {
        val previous = current.get()
        current.set(snapshot)
        return try {
            create()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }
}

public sealed class CuTPlacedTile(
    public val handle: Block,
) : TagContainer by BlockDataTagContainer(handle), AttachmentHolder<BlockAttachment> {

    private val constructionSnapshot: PlacedTileConstructionSnapshot? = PlacedTileConstructionContext.snapshot()
    private var placedIdentitySnapshot: BlockIdentity = constructionSnapshot?.identity ?: handle.blockIdentity
    private var placedCustomStateSnapshot: CustomBlockState? =
        constructionSnapshot?.state ?: NativeBlockTypes.customState(handle)

    public val location: Location by handle::location
    public val chunk: Chunk by handle::chunk

    public fun vanilla(): Block = handle

    public val identity: BlockIdentity
        get() = placedIdentitySnapshot.takeIf(BlockIdentity::isCustom)
            ?: NativeBlockTypes.definition(handle)?.asIdentity()
            ?: handle.type.asBlockIdentity()

    public val customTile: CustomTile<*>? get() = identity.customTile

    public val isCustom: Boolean get() = identity.isCustom

    public val material: Material get() = handle.type

    public val customState: CustomBlockState?
        get() = placedCustomStateSnapshot.takeIf { placedIdentitySnapshot.isCustom }
            ?: NativeBlockTypes.customState(handle)

    public var state: CustomBlockState
        get() = customState ?: error("Block at $location has no custom native state.")
        set(value) {
            val previous = state
            val definition = identity.customTile
                ?: error("Block at $location is not a native custom block.")
            NativeBlockTypes.setAt(handle, definition, value)
            placedIdentitySnapshot = definition.asIdentity()
            placedCustomStateSnapshot = value
            BlockSystem.dispatch(this) { it.onStateChanged(BlockStateChangeContext(this, previous, value)) }
        }

    public operator fun <T : Any> get(type: BlockStateType<T>): T = state[type]

    public fun <T : Any> setState(type: BlockStateType<T>, value: T) {
        val definition = identity.customTile
            ?: error("Block at $location is not a native custom block.")
        state = definition.descriptor.states.state(state.asMap() + (type to value))
    }

    /** Captures placed-only data needed by break and removal contexts after the native block is gone. */
    internal open fun snapshotForRemoval() {
        NativeBlockTypes.definition(handle)?.asIdentity()?.let { placedIdentitySnapshot = it }
        NativeBlockTypes.customState(handle)?.let { placedCustomStateSnapshot = it }
    }

    public open override fun hasAttachment(schema: Schema<out BlockAttachment>): Boolean = identity.hasAttachment(schema)

    public open override fun <T : BlockAttachment> getAttachment(schema: Schema<T>): T = identity.getAttachment(schema)

    public open override fun <T : BlockAttachment> getAttachmentOrNull(schema: Schema<T>): T? =
        identity.getAttachmentOrNull(schema)

    public open override fun <T : BlockAttachment> getAttachments(schema: Schema<T>): List<T> =
        identity.getAttachments(schema)

    public open override fun getAllAttachments(): List<BlockAttachment> = identity.getAllAttachments()
}


public open class CuTPlacedTileEntity(
    handle: Block
) : CuTPlacedTile(handle) {

    private var lastResolvedAttachments: List<BlockAttachment>? = null
    private var lastPersistentDataContainer: PersistentDataContainer? = null
    private var lastNativeEntity: NativeCustomBlockEntity? = null

    override fun hasAttachment(schema: Schema<out BlockAttachment>): Boolean =
        getAllAttachments().any { it.schema().id == schema.id }

    override fun <T : BlockAttachment> getAttachment(schema: Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on tile at $location.")

    override fun <T : BlockAttachment> getAttachmentOrNull(schema: Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : BlockAttachment> getAttachments(schema: Schema<T>): List<T> =
        getAllAttachments().matching(schema)

    override fun getAllAttachments(): List<BlockAttachment> {
        val overlay = try {
            readBlockAttachmentOverlay()
        } catch (_: IllegalStateException) {
            return lastResolvedAttachments ?: identity.getAllAttachments()
        }
        val overlayIds = overlay.attachments.map { it.schema().id }.toSet()
        val intrinsic = identity.getAllAttachments().filter { attachment ->
            val schema = attachment.schema()
            schema.id !in overlay.suppressed &&
                (attachment.isRepeatableAttachment() || schema.id !in overlayIds)
        }
        return (intrinsic + overlay.attachments).also { lastResolvedAttachments = it }
    }

    override fun <P : Any, C : Any> set(
        id: Identifier,
        complexValue: C?,
        converter: xyz.mastriel.cutapi.pdc.tags.converters.TagConverter<P, C>,
    ) {
        mutatePersistentData { container ->
            PDCTagContainer(container).set(id, complexValue, converter)
        }
    }

    override fun <P : Any, C : Any> get(
        id: Identifier,
        converter: xyz.mastriel.cutapi.pdc.tags.converters.TagConverter<P, C>,
    ): C? = PDCTagContainer(persistentDataContainer()).get(id, converter)

    override fun has(id: Identifier): Boolean = PDCTagContainer(persistentDataContainer()).has(id)

    override fun isNull(id: Identifier): Boolean = PDCTagContainer(persistentDataContainer()).isNull(id)

    internal override fun snapshotForRemoval() {
        super.snapshotForRemoval()
        getAllAttachments()
        persistentDataContainer()
    }

    internal fun captureNativeEntity(entity: NativeCustomBlockEntity) {
        lastNativeEntity = entity
        lastPersistentDataContainer = entity.persistentDataContainer
    }

    internal fun persistentDataContainer(): PersistentDataContainer {
        lastPersistentDataContainer?.let { return it }
        nativeEntityOrNull()?.persistentDataContainer?.let { container ->
            lastPersistentDataContainer = container
            return container
        }
        if (NativeBlockTypes.definition(handle) == null) {
            (handle.state as? TileState)?.persistentDataContainer?.let { container ->
                lastPersistentDataContainer = container
                return container
            }
        }
        return checkNotNull(lastPersistentDataContainer) {
            "Tile data at $location was not captured before its block entity was removed."
        }
    }

    internal fun mutatePersistentData(mutation: (PersistentDataContainer) -> Unit) {
        val native = nativeEntityOrNull()
        if (native != null) {
            mutation(native.persistentDataContainer)
            lastPersistentDataContainer = native.persistentDataContainer
            native.setChanged()
            return
        }

        if (NativeBlockTypes.definition(handle) == null) {
            val vanilla = handle.state as? TileState
            if (vanilla != null) {
                mutation(vanilla.persistentDataContainer)
                check(vanilla.update(true, false)) {
                    "Minecraft rejected persistent tile data at $location."
                }
                lastPersistentDataContainer = vanilla.persistentDataContainer
                return
            }
        }

        mutation(
            checkNotNull(lastPersistentDataContainer) {
                "Tile data at $location was not captured before its block entity was removed."
            },
        )
    }

    internal fun nativeEntity(): NativeCustomBlockEntity {
        return nativeEntityOrNull()
            ?: error("Native custom tile entity at $location is missing or has the wrong type.")
    }

    private fun nativeEntityOrNull(): NativeCustomBlockEntity? {
        lastNativeEntity?.let { return it }
        if (!isCustom) return null
        val level = (handle.world as CraftWorld).handle
        return (level.getBlockEntity(BlockPos(handle.x, handle.y, handle.z)) as? NativeCustomBlockEntity)
            ?.also(::captureNativeEntity)
    }


}

public open class CuTPlacedBlock(handle: Block) : CuTPlacedTile(handle)

private data class BlockAttachmentOverlay(
    val attachments: List<BlockAttachment>,
    val suppressed: Set<Identifier>,
)

private fun CuTPlacedTileEntity.readBlockAttachmentOverlay(): BlockAttachmentOverlay {
    val state = AttachmentPdcStorage.read(persistentDataContainer())
    return BlockAttachmentOverlay(state.attachments.filterIsInstance<BlockAttachment>(), state.suppressed)
}

private fun CuTPlacedTileEntity.writeBlockAttachmentOverlay(overlay: BlockAttachmentOverlay) {
    mutatePersistentData { container ->
        AttachmentPdcStorage.write(container, overlay.attachments, overlay.suppressed)
    }
}

internal fun <T : BlockAttachment> CuTPlacedTileEntity.readDynamicAttachment(schema: Schema<T>): Result<T?> =
    runCatching { persistentDataContainer() }
        .mapCatching { container -> AttachmentPdcStorage.readOne(container, schema).getOrThrow() }

public fun CuTPlacedTileEntity.setAttachment(attachment: BlockAttachment) {
    @Suppress("UNCHECKED_CAST")
    val schema = attachment.schema().requireRegistered() as Schema<BlockAttachment>
    val overlay = readBlockAttachmentOverlay()
    val next = overlay.attachments.filterNot { it.schema().id == schema.id } + attachment
    val suppressIntrinsic = attachment.isRepeatableAttachment() && identity.hasAttachment(schema)
    writeBlockAttachmentOverlay(
        BlockAttachmentOverlay(next, if (suppressIntrinsic) overlay.suppressed + schema.id else overlay.suppressed - schema.id),
    )
    dispatchAttachmentChanged(schema)
}

public fun CuTPlacedTileEntity.addAttachment(attachment: BlockAttachment) {
    if (!attachment.isRepeatableAttachment()) return setAttachment(attachment)
    @Suppress("UNCHECKED_CAST")
    val schema = attachment.schema().requireRegistered() as Schema<BlockAttachment>
    val overlay = readBlockAttachmentOverlay()
    writeBlockAttachmentOverlay(BlockAttachmentOverlay(overlay.attachments + attachment, overlay.suppressed - schema.id))
    dispatchAttachmentChanged(schema)
}

public fun CuTPlacedTileEntity.removeAttachment(schema: Schema<out BlockAttachment>) {
    schema.requireRegistered()
    val overlay = readBlockAttachmentOverlay()
    val suppressed = if (identity.hasAttachment(schema)) overlay.suppressed + schema.id else overlay.suppressed - schema.id
    writeBlockAttachmentOverlay(
        BlockAttachmentOverlay(overlay.attachments.filterNot { it.schema().id == schema.id }, suppressed),
    )
    dispatchAttachmentChanged(schema)
}

private fun CuTPlacedTileEntity.dispatchAttachmentChanged(schema: Schema<out BlockAttachment>) {
    BlockSystem.systemsFor(this)
        .filterIsInstance<TileSystem>()
        .forEach { it.onAttachmentChanged(TileAttachmentChangeContext(this, schema)) }
}
