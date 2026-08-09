package xyz.mastriel.cutapi.item

import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.item.events.*
import xyz.mastriel.cutapi.item.systems.*
import xyz.mastriel.cutapi.pdc.tags.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.system.*

public open class ItemSystemContext(public val item: CuTItemStack) {
    public fun <T : ItemAttachment> attachment(schema: Schema<T>): T =
        item.getAttachment(schema)

    public fun data(schema: Schema<out ItemAttachment>): TagContainer =
        ItemAttachmentTagContainer(item.handle, schema.id)
}

public class ItemRenderContext(
    item: CuTItemStack,
    /**
     * A copy of the stack before it's rendered. Changes made to this stack will not be reflected in the original stack.
     */
    public val prerenderStack: CuTItemStack,
    public val viewer: Player?
) : ItemSystemContext(item)

public class ItemObtainContext(item: CuTItemStack, public val player: Player, public val event: CustomItemObtainEvent) :
    ItemSystemContext(item)

public class ItemInteractContext(item: CuTItemStack, public val player: Player, public val event: PlayerInteractEvent) :
    ItemSystemContext(item)

public class ItemRightClickEntityContext(
    item: CuTItemStack,
    public val player: Player,
    public val entity: Entity,
    public val event: PlayerInteractEntityEvent
) : ItemSystemContext(item)

public class ItemDropContext(item: CuTItemStack, public val player: Player, public val event: PlayerDropItemEvent) :
    ItemSystemContext(item)

public class ItemOffhandEquipContext(item: CuTItemStack, public val player: Player, public val event: Cancellable) :
    ItemSystemContext(item)

public class ItemDamageEntityContext(
    item: CuTItemStack,
    public val attacker: LivingEntity,
    public val victim: LivingEntity,
    public val event: EntityDamageByEntityEvent
) : ItemSystemContext(item)

public class ItemBlockBreakContext(item: CuTItemStack, public val player: Player, public val event: BlockBreakEvent) :
    ItemSystemContext(item)

public class ItemBlockPlaceContext(
    item: CuTItemStack,
    public val player: Player,
    public val location: Location,
    public val event: BlockPlaceEvent
) : ItemSystemContext(item)

public class ItemInventoryTickContext(item: CuTItemStack, public val player: Player, public val slot: Int) :
    ItemSystemContext(item)

public class ItemHandTickContext(item: CuTItemStack, public val player: Player, public val slot: HandSlot) :
    ItemSystemContext(item)

public class ItemEquippedTickContext(item: CuTItemStack, public val player: Player, public val slot: ArmorSlot) :
    ItemSystemContext(item)

public enum class HandSlot { MainHand, OffHand }
public enum class ArmorSlot { Helmet, Chestplate, Leggings, Boots }

public interface ItemSystem : CuTSystem<CuTItemStack> {
    public fun onRender(context: ItemRenderContext) {}
    public fun onObtain(context: ItemObtainContext) {}
    public fun onLeftClick(context: ItemInteractContext) {}
    public fun onRightClick(context: ItemInteractContext) {}
    public fun onRightClickEntity(context: ItemRightClickEntityContext) {}
    public fun onDrop(context: ItemDropContext) {}
    public fun onOffhandEquip(context: ItemOffhandEquipContext) {}
    public fun onDamageEntity(context: ItemDamageEntityContext) {}
    public fun onBreak(context: ItemBlockBreakContext) {}
    public fun onPlace(context: ItemBlockPlaceContext) {}
    public fun onTickInInventory(context: ItemInventoryTickContext) {}
    public fun onTickInEitherHand(context: ItemHandTickContext) {}
    public fun onTickEquipped(context: ItemEquippedTickContext) {}

    public companion object : IdentifierRegistry<ItemSystem>(id("cutapi:registry/item_system")) {
        internal val DeferredRegistry: DeferredRegistry<ItemSystem> = defer()

        public fun registerSystem(system: ItemSystem): ItemSystem {
            if (has(system.id)) return get(system.id)
            return register(system)
        }

        public fun registerBuiltins() {
            registerSystem(DisplayAsSystem)
            registerSystem(HideAttributesSystem)
            registerSystem(HideTooltipSystem)
            registerSystem(ShinySystem)
            registerSystem(BlockPlaceSystem)
            registerSystem(ItemOriginSystem)
        }

        public fun dispatchRender(context: ItemRenderContext) {
            applicableTo(context.item).forEach { it.onRender(context) }
        }
    }
}

public fun generalItemSystem(
    id: Identifier,
    priority: RegistryPriority = RegistryPriority.Medium,
    block: ItemSystem.() -> Unit = {}
): ItemSystem = object : ItemSystem {
    override val id: Identifier = id
    override val priority: RegistryPriority = priority

    override fun prerequisite(target: CuTItemStack): Boolean {
        return true
    }

    init {
        block()
    }
}

public fun attachmentItemSystem(
    attachment: Schema<out ItemAttachment>,
    id: Identifier = attachment.id / "system",
    priority: RegistryPriority = RegistryPriority.Medium,
    block: ItemSystem.() -> Unit = {}
): ItemSystem = object : ItemSystem {
    override val id: Identifier = id
    override val priority: RegistryPriority = priority

    override fun prerequisite(target: CuTItemStack): Boolean {
        return target.hasAttachment(attachment)
    }

    init {
        block()
    }
}
