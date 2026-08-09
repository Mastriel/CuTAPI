package xyz.mastriel.cutapi.block

import net.kyori.adventure.text.*
import org.bukkit.Material
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.block.behaviors.*
import xyz.mastriel.cutapi.data.Schema
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*
import xyz.mastriel.cutapi.utils.personalized.*

public fun interface BlockVisualResolver {
    public fun resolve(state: CustomBlockState): BlockVisualMethod
}

public fun interface BlockModelResolver {
    public fun resolve(state: CustomBlockState): BlockModel?
}

public sealed interface TileDescriptor : AttachmentHolder<BlockAttachment> {
    @Deprecated("Use attachments and BlockSystem.")
    public val behaviors: List<TileBehavior>
    @Deprecated("Use visualMethod(state).")
    public val blockStrategy: BlockStrategy
    public val name: Personalized<Component>?
    public val itemPolicy: BlockItemPolicy
    public val states: BlockStateDefinition
    public val settings: BlockSettings
    public val attachments: List<BlockAttachment>
    public val dropProducer: (BlockDropContext) -> List<org.bukkit.inventory.ItemStack>
    public val experienceProducer: (BlockDropContext) -> Int

    public fun visualMethod(state: CustomBlockState): BlockVisualMethod
    public fun model(state: CustomBlockState): BlockModel?
    public fun visualIdentity(state: CustomBlockState): String?

    override fun hasAttachment(schema: Schema<out BlockAttachment>): Boolean =
        attachments.any { it.schema().id == schema.id }

    override fun <T : BlockAttachment> getAttachment(schema: Schema<T>): T =
        getAttachmentOrNull(schema) ?: error("Attachment ${schema.id} does not exist on this block descriptor.")

    override fun <T : BlockAttachment> getAttachmentOrNull(schema: Schema<T>): T? =
        getAttachments(schema).firstOrNull()

    override fun <T : BlockAttachment> getAttachments(schema: Schema<T>): List<T> =
        attachments.filter { it.schema().id == schema.id }.map {
            @Suppress("UNCHECKED_CAST")
            it as T
        }

    override fun getAllAttachments(): List<BlockAttachment> = attachments.toList()
}

public class BlockDescriptor(
    @Deprecated("Use attachments and BlockSystem.")
    override val behaviors: List<BlockBehavior> = emptyList(),
    @Deprecated("Use visualMethod(state).")
    override val blockStrategy: BlockStrategy,
    override val name: Personalized<Component>?,
    override val itemPolicy: BlockItemPolicy,
    override val states: BlockStateDefinition = BlockStateDefinition.Empty,
    override val settings: BlockSettings = BlockSettingsBuilder().build(),
    override val attachments: List<BlockAttachment> = emptyList(),
    private val visualResolver: BlockVisualResolver = BlockVisualResolver { blockStrategy.toVisualMethod() },
    private val modelResolver: BlockModelResolver = BlockModelResolver { null },
    private val visualIdentityResolver: (CustomBlockState) -> String? = { null },
    override val dropProducer: (BlockDropContext) -> List<org.bukkit.inventory.ItemStack> = ::defaultBlockDrops,
    override val experienceProducer: (BlockDropContext) -> Int = { 0 },
    public val onRegister: EventHandlerList<CustomBlock<*>> = EventHandlerList(),
) : TileDescriptor {
    override fun visualMethod(state: CustomBlockState): BlockVisualMethod = visualResolver.resolve(state)
    override fun model(state: CustomBlockState): BlockModel? = modelResolver.resolve(state)
    override fun visualIdentity(state: CustomBlockState): String? = visualIdentityResolver(state)
}

public class TileEntityDescriptor(
    @Deprecated("Use attachments and BlockSystem.")
    override val behaviors: List<TileEntityBehavior> = emptyList(),
    @Deprecated("Use visualMethod(state).")
    override val blockStrategy: BlockStrategy,
    override val name: Personalized<Component>?,
    override val itemPolicy: BlockItemPolicy,
    override val states: BlockStateDefinition = BlockStateDefinition.Empty,
    override val settings: BlockSettings = BlockSettingsBuilder().build(),
    override val attachments: List<BlockAttachment> = emptyList(),
    private val visualResolver: BlockVisualResolver = BlockVisualResolver { blockStrategy.toVisualMethod() },
    private val modelResolver: BlockModelResolver = BlockModelResolver { null },
    private val visualIdentityResolver: (CustomBlockState) -> String? = { null },
    override val dropProducer: (BlockDropContext) -> List<org.bukkit.inventory.ItemStack> = ::defaultBlockDrops,
    override val experienceProducer: (BlockDropContext) -> Int = { 0 },
    public val onRegister: EventHandlerList<CustomTileEntity<*>> = EventHandlerList(),
) : TileDescriptor {
    override fun visualMethod(state: CustomBlockState): BlockVisualMethod = visualResolver.resolve(state)
    override fun model(state: CustomBlockState): BlockModel? = modelResolver.resolve(state)
    override fun visualIdentity(state: CustomBlockState): String? = visualIdentityResolver(state)
}

public abstract class TileDescriptorBuilder<B : TileBehavior, T : TileDescriptor, C : CustomTile<*>> {

    @Deprecated("Use attach and BlockSystem.")
    public open val behaviors: ListBehaviorHolder<B> = ListBehaviorHolder<B>()

    public open val onRegister: EventHandlerList<C> = EventHandlerList<C>()

    @Deprecated("Use visual(method) or visuals(resolver).")
    public open var blockStrategy: BlockStrategy = BlockStrategy.Mushroom
    public open var itemPolicy: BlockItemPolicy = BlockItemPolicy.Generate()

    public open var name: Personalized<Component>? = null

    private val attachmentValues: MutableList<BlockAttachment> = mutableListOf()
    public val attachments: List<BlockAttachment> get() = attachmentValues.toList()

    private var statesDefinition: BlockStateDefinition = BlockStateDefinition.Empty
    private var settingsDefinition: BlockSettings = BlockSettingsBuilder().build()
    private var visualResolver: BlockVisualResolver? = null
    private var modelResolver: BlockModelResolver = BlockModelResolver { null }
    private var visualIdentityResolver: (CustomBlockState) -> String? = { null }
    private var dropsProducer: (BlockDropContext) -> List<org.bukkit.inventory.ItemStack> = ::defaultBlockDrops
    private var xpProducer: (BlockDropContext) -> Int = { 0 }

    public fun states(configure: BlockStates.() -> Unit) {
        statesDefinition = blockStates(configure)
    }

    public fun settings(configure: BlockSettingsBuilder.() -> Unit) {
        settingsDefinition = BlockSettingsBuilder().apply(configure).build()
    }

    public fun visual(method: BlockVisualMethod) {
        visualResolver = BlockVisualResolver { method }
    }

    /** Defers carrier BlockData creation until Minecraft's vanilla block registry is available. */
    public fun visual(method: () -> BlockVisualMethod) {
        visualResolver = BlockVisualResolver { method() }
    }

    public fun visuals(resolve: (CustomBlockState) -> BlockVisualMethod) {
        visualResolver = BlockVisualResolver(resolve)
    }

    public fun model(model: BlockModel) {
        modelResolver = BlockModelResolver { model }
    }

    public fun models(resolve: (CustomBlockState) -> BlockModel?) {
        modelResolver = BlockModelResolver(resolve)
    }

    public fun visualIdentity(resolve: (CustomBlockState) -> String?) {
        visualIdentityResolver = resolve
    }

    public fun drops(produce: (BlockDropContext) -> List<org.bukkit.inventory.ItemStack>) {
        dropsProducer = produce
    }

    public fun experience(produce: (BlockDropContext) -> Int) {
        xpProducer = produce
    }

    public fun attach(vararg attachments: BlockAttachment) {
        for (attachment in attachments) {
            val schema = attachment.schema()
            if (attachmentValues.any { it.schema().id == schema.id } && !attachment.isRepeatableAttachment()) {
                error("${schema.id} lacks a RepeatableAttachment annotation to be repeatable.")
            }
            attachmentValues += attachment
        }
    }

    public fun attach(attachments: Collection<BlockAttachment>) {
        attach(*attachments.toTypedArray())
    }

    protected fun descriptorParts(): DescriptorParts = DescriptorParts(
        states = statesDefinition,
        settings = settingsDefinition,
        attachments = attachmentValues.toList(),
        visualResolver = visualResolver ?: BlockVisualResolver { blockStrategy.toVisualMethod() },
        modelResolver = modelResolver,
        visualIdentityResolver = visualIdentityResolver,
        dropsProducer = dropsProducer,
        experienceProducer = xpProducer,
    )

    public open fun behavior(vararg behaviors: B) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)
            this.behaviors += behavior
        }
    }

    public open fun behavior(behaviors: Collection<B>) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)
            this.behaviors += behavior
        }
    }


    public abstract fun build(): T

}


public class BlockDescriptorBuilder : TileDescriptorBuilder<BlockBehavior, BlockDescriptor, CustomBlock<*>>() {
    override fun build(): BlockDescriptor {
        val parts = descriptorParts()
        return BlockDescriptor(
            behaviors.toList(), blockStrategy, name, itemPolicy,
            parts.states, parts.settings, parts.attachments, parts.visualResolver,
            parts.modelResolver, parts.visualIdentityResolver, parts.dropsProducer,
            parts.experienceProducer, onRegister,
        )
    }
}

private fun BlockBehavior.tileEntity(): TileEntityBehavior {
    return object : TileEntityBehavior(this.id), TileBehavior by this {
        override val id: Identifier
            get() = super.id

    }
}

public class TileEntityDescriptorBuilder :
    TileDescriptorBuilder<TileEntityBehavior, TileEntityDescriptor, CustomTileEntity<*>>() {

    @JvmName("tileBehavior")
    public fun behavior(vararg behaviors: TileBehavior) {
        for (behavior in behaviors) {
            this.behaviors.requireRepeatableIfExists(behavior)

            when (behavior) {
                is TileEntityBehavior -> {
                    this.behaviors += behavior
                }

                is BlockBehavior -> {
                    this.behaviors += behavior.tileEntity()
                }
            }

        }
    }

    @JvmName("blockBehavior")
    public fun behavior(behaviors: Collection<TileBehavior>) {
        behavior(*behaviors.toTypedArray())
    }

    override fun build(): TileEntityDescriptor {
        val parts = descriptorParts()
        return TileEntityDescriptor(
            behaviors.toList(), blockStrategy, name, itemPolicy,
            parts.states, parts.settings, parts.attachments, parts.visualResolver,
            parts.modelResolver, parts.visualIdentityResolver, parts.dropsProducer,
            parts.experienceProducer, onRegister,
        )
    }
}

public data class DescriptorParts(
    val states: BlockStateDefinition,
    val settings: BlockSettings,
    val attachments: List<BlockAttachment>,
    val visualResolver: BlockVisualResolver,
    val modelResolver: BlockModelResolver,
    val visualIdentityResolver: (CustomBlockState) -> String?,
    val dropsProducer: (BlockDropContext) -> List<org.bukkit.inventory.ItemStack>,
    val experienceProducer: (BlockDropContext) -> Int,
)

public fun blockDescriptor(configure: BlockDescriptorBuilder.() -> Unit = {}): BlockDescriptor =
    BlockDescriptorBuilder().apply(configure).build()

public fun defaultBlockDescriptor(): BlockDescriptor = BlockDescriptorBuilder().build()

public fun tileEntityDescriptor(configure: TileEntityDescriptorBuilder.() -> Unit = {}): TileEntityDescriptor =
    TileEntityDescriptorBuilder().apply(configure).build()

public fun defaultTileEntityDescriptor(): TileEntityDescriptor = TileEntityDescriptorBuilder().build()
