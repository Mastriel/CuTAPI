package xyz.mastriel.cutapi.block

import org.bukkit.*
import org.bukkit.block.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.block.CustomBlockManager.Companion.tags
import xyz.mastriel.cutapi.block.behaviors.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*


public sealed interface CustomTile<T : CuTPlacedTile> : Identifiable {
    public val descriptor: TileDescriptor
    public val placedBlockTypeClass: KClass<out T>

    public fun setAt(location: Location) {
        val block = location.toBlockLocation().block
        setAt(block)
    }

    public fun setAt(block: Block) {
        CuTAPI.blockManager.placeTile(block.location, this)
        when (val strategy = descriptor.blockStrategy) {
            is BlockStrategy.FakeEntity -> block.type = Material.BARRIER
            is BlockStrategy.Mushroom -> block.type = Material.RED_MUSHROOM
            is BlockStrategy.NoteBlock -> block.type = Material.NOTE_BLOCK
            is BlockStrategy.Vanilla -> block.type = strategy.material
        }
    }

    public companion object : IdentifierRegistry<CustomTile<*>>(id("cutapi:registry/custom_tile")) {

    }
}

public class CustomBlock<T : CuTPlacedBlock> @Deprecated(
    message = "Use customBlock, customBlockFromDescriptor, or typedCustomBlock instead.",
    replaceWith = ReplaceWith("typedCustomBlockFromDescriptor(id, placedBlockTypeClass) { descriptor }"),
    level = DeprecationLevel.WARNING,
) constructor(
    override val id: Identifier,
    override val descriptor: BlockDescriptor,
    override val placedBlockTypeClass: KClass<out T>
) : CustomTile<T>, BehaviorHolder<BlockBehavior> {

    private var definitionPrepared: Boolean = false
    private var preparedItem: PreparedBlockItem? = null

    internal fun prepareDefinition(contributeItem: (CustomItem<*>) -> Unit = ::contributeGeneratedItem) {
        check(!definitionPrepared) { "Custom block $id was prepared more than once." }
        preparedItem = descriptor.itemPolicy.prepare(descriptor, this).also { prepared ->
            if (prepared?.contributeToRegistry == true) contributeItem(prepared.item)
        }
        definitionPrepared = true
        descriptor.onRegister.trigger(this)
    }

    internal fun validatePreparedItem(
        findItem: (Identifier) -> CustomItem<*>? = { CustomItem.getOrNull(it) },
    ) {
        validatePreparedItem(this, definitionPrepared, preparedItem, findItem)
    }

    private val behaviorHolder by lazy { blockBehaviorHolder(this) }
    override fun hasBehavior(behavior: KClass<out BlockBehavior>): Boolean = behaviorHolder.hasBehavior(behavior)
    override fun hasBehavior(behaviorId: Identifier): Boolean = behaviorHolder.hasBehavior(behaviorId)
    override fun getAllBehaviors(): Set<BlockBehavior> = behaviorHolder.getAllBehaviors()
    override fun <T : BlockBehavior> getBehaviorOrNull(behaviorId: Identifier): T? =
        behaviorHolder.getBehaviorOrNull(behaviorId)

    override fun <T : BlockBehavior> getBehaviorOrNull(behavior: KClass<T>): T? =
        behaviorHolder.getBehaviorOrNull(behavior)

    override fun <T : BlockBehavior> getBehavior(behaviorId: Identifier): T = behaviorHolder.getBehavior(behaviorId)
    override fun <T : BlockBehavior> getBehavior(behavior: KClass<T>): T = behaviorHolder.getBehavior(behavior)


    public companion object : IdentifierRegistry<CustomBlock<*>>(id("cutapi:registry/custom_block")) {
        internal val DeferredRegistry: DeferredRegistry<CustomBlock<*>> = defer(RegistryPriority(Int.MAX_VALUE))

        public val Unknown: CustomBlock<CuTPlacedBlock> by DeferredRegistry.registerCustomBlock(
            id("cutapi:unknown_block")
        ) {
            blockStrategy = BlockStrategy.Vanilla(Material.BARRIER)
            itemPolicy = BlockItemPolicy.Generate()
        }

        init {
            addHook(HookPriority.First) {
                CustomTile.register(item)
                item.prepareDefinition()
            }
        }
    }
}

public class CustomTileEntity<T : CuTPlacedTileEntity> @Deprecated(
    message = "Use customTileEntity, customTileEntityFromDescriptor, or typedCustomTileEntity instead.",
    replaceWith = ReplaceWith("typedCustomTileEntityFromDescriptor(id, placedBlockTypeClass) { descriptor }"),
    level = DeprecationLevel.WARNING,
) constructor(
    override val id: Identifier,
    override val descriptor: TileEntityDescriptor,
    override val placedBlockTypeClass: KClass<out T>
) : CustomTile<T>, BehaviorHolder<TileEntityBehavior> {

    private var definitionPrepared: Boolean = false
    private var preparedItem: PreparedBlockItem? = null

    internal fun prepareDefinition(contributeItem: (CustomItem<*>) -> Unit = ::contributeGeneratedItem) {
        check(!definitionPrepared) { "Custom tile entity $id was prepared more than once." }
        preparedItem = descriptor.itemPolicy.prepare(descriptor, this).also { prepared ->
            if (prepared?.contributeToRegistry == true) contributeItem(prepared.item)
        }
        definitionPrepared = true
        descriptor.onRegister.trigger(this)
    }

    internal fun validatePreparedItem(
        findItem: (Identifier) -> CustomItem<*>? = { CustomItem.getOrNull(it) },
    ) {
        validatePreparedItem(this, definitionPrepared, preparedItem, findItem)
    }

    private val behaviorHolder by lazy { tileEntityBehaviorHolder(this) }
    override fun hasBehavior(behavior: KClass<out TileEntityBehavior>): Boolean = behaviorHolder.hasBehavior(behavior)
    override fun hasBehavior(behaviorId: Identifier): Boolean = behaviorHolder.hasBehavior(behaviorId)
    override fun getAllBehaviors(): Set<TileEntityBehavior> = behaviorHolder.getAllBehaviors()
    override fun <T : TileEntityBehavior> getBehaviorOrNull(behaviorId: Identifier): T? =
        behaviorHolder.getBehaviorOrNull(behaviorId)

    override fun <T : TileEntityBehavior> getBehaviorOrNull(behavior: KClass<T>): T? =
        behaviorHolder.getBehaviorOrNull(behavior)

    override fun <T : TileEntityBehavior> getBehavior(behaviorId: Identifier): T =
        behaviorHolder.getBehavior(behaviorId)

    override fun <T : TileEntityBehavior> getBehavior(behavior: KClass<T>): T = behaviorHolder.getBehavior(behavior)

    public companion object : IdentifierRegistry<CustomTileEntity<*>>(id("cutapi:registry/custom_tile_entity")) {
        internal val DeferredRegistry: DeferredRegistry<CustomTileEntity<*>> = defer(RegistryPriority(Int.MAX_VALUE))

        public val Unknown: CustomTileEntity<CuTPlacedTileEntity> by DeferredRegistry.registerCustomTileEntity(
            id("cutapi:unknown_tile_entity")
        ) {
            blockStrategy = BlockStrategy.Vanilla(Material.BARRIER)
            itemPolicy = BlockItemPolicy.Generate()
        }

        init {
            addHook(HookPriority.First) {
                CustomTile.register(item)
                item.prepareDefinition()
            }
        }
    }
}

private fun contributeGeneratedItem(item: CustomItem<*>) {
    check(CustomItem.isOpen) {
        "Generated custom item ${item.id} cannot be contributed after the item registry closes."
    }
    CustomItem.modifyRegistry {
        register(item)
    }
}

private fun validatePreparedItem(
    tile: CustomTile<*>,
    definitionPrepared: Boolean,
    prepared: PreparedBlockItem?,
    findItem: (Identifier) -> CustomItem<*>?,
) {
    check(definitionPrepared) { "Custom tile ${tile.id} was registered without preparing its item policy." }
    val item = prepared?.item ?: return
    check(findItem(item.id) === item) {
        "Custom tile ${tile.id} resolved item ${item.id}, but that exact item was not registered."
    }
}

internal fun validatePreparedTileItems() {
    CustomBlock.getAllValues().forEach(CustomBlock<*>::validatePreparedItem)
    CustomTileEntity.getAllValues().forEach(CustomTileEntity<*>::validatePreparedItem)
}
