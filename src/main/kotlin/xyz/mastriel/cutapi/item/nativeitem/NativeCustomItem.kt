package xyz.mastriel.cutapi.item.nativeitem

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Position
import net.minecraft.core.component.TypedDataComponent
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.SlotAccess
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.inventory.ClickAction
import net.minecraft.world.inventory.Slot
import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.item.ProjectileItem
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import java.util.Optional
import java.util.function.Consumer

/** Common identity contract for native custom items projected as their vanilla backing item. */
internal interface NativeBackedItem {
    val backing: Item
}

/** A native registry item whose observable gameplay behavior is supplied by [backing]. */
internal open class NativeCustomItem(
    backing: Item,
    key: ResourceKey<Item>,
) : Item(nativeItemProperties(backing, key)), NativeBackedItem {
    private val behavior: NativeItemBehaviorAdapter = NativeItemBehaviorAdapter.create(backing)
    override val backing: Item get() = behavior.backing

    override fun onUseTick(level: Level, entity: LivingEntity, stack: ItemStack, remainingUseDuration: Int) =
        backing.onUseTick(level, entity, stack, remainingUseDuration)

    override fun onDestroyed(itemEntity: ItemEntity) = backing.onDestroyed(itemEntity)

    override fun canDestroyBlock(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, entity: LivingEntity): Boolean =
        backing.canDestroyBlock(stack, state, level, pos, entity)

    override fun useOn(context: UseOnContext): InteractionResult = behavior.useOn(context)

    override fun getDestroySpeed(stack: ItemStack, state: BlockState): Float = backing.getDestroySpeed(stack, state)

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult = backing.use(level, player, hand)

    override fun finishUsingItem(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack =
        backing.finishUsingItem(stack, level, entity)

    override fun isBarVisible(stack: ItemStack): Boolean = backing.isBarVisible(stack)

    override fun getBarWidth(stack: ItemStack): Int = backing.getBarWidth(stack)

    override fun getBarColor(stack: ItemStack): Int = backing.getBarColor(stack)

    override fun overrideStackedOnOther(stack: ItemStack, slot: Slot, action: ClickAction, player: Player): Boolean =
        backing.overrideStackedOnOther(stack, slot, action, player)

    override fun overrideOtherStackedOnMe(
        stack: ItemStack,
        other: ItemStack,
        slot: Slot,
        action: ClickAction,
        player: Player,
        access: SlotAccess,
    ): Boolean = backing.overrideOtherStackedOnMe(stack, other, slot, action, player, access)

    override fun getAttackDamageBonus(target: Entity, damageAmount: Float, damageSource: DamageSource): Float =
        backing.getAttackDamageBonus(target, damageAmount, damageSource)

    @Suppress("DEPRECATION")
    override fun getItemDamageSource(entity: LivingEntity): DamageSource? = backing.getItemDamageSource(entity)

    override fun hurtEnemy(stack: ItemStack, target: LivingEntity, attacker: LivingEntity) =
        backing.hurtEnemy(stack, target, attacker)

    override fun postHurtEnemy(stack: ItemStack, target: LivingEntity, attacker: LivingEntity) =
        backing.postHurtEnemy(stack, target, attacker)

    override fun mineBlock(stack: ItemStack, level: Level, state: BlockState, pos: BlockPos, entity: LivingEntity): Boolean =
        backing.mineBlock(stack, level, state, pos, entity)

    override fun isCorrectToolForDrops(stack: ItemStack, state: BlockState): Boolean =
        backing.isCorrectToolForDrops(stack, state)

    override fun interactLivingEntity(
        stack: ItemStack,
        player: Player,
        interactionTarget: LivingEntity,
        usedHand: InteractionHand,
    ): InteractionResult = backing.interactLivingEntity(stack, player, interactionTarget, usedHand)

    override fun inventoryTick(stack: ItemStack, level: ServerLevel, entity: Entity, slot: EquipmentSlot?) =
        backing.inventoryTick(stack, level, entity, slot)

    override fun onCraftedBy(stack: ItemStack, player: Player) = backing.onCraftedBy(stack, player)

    override fun onCraftedPostProcess(stack: ItemStack, level: Level) = backing.onCraftedPostProcess(stack, level)

    override fun getUseAnimation(stack: ItemStack): ItemUseAnimation = backing.getUseAnimation(stack)

    override fun getUseDuration(stack: ItemStack, entity: LivingEntity): Int = backing.getUseDuration(stack, entity)

    override fun releaseUsing(stack: ItemStack, level: Level, entity: LivingEntity, timeLeft: Int): Boolean =
        backing.releaseUsing(stack, level, entity, timeLeft)

    @Suppress("DEPRECATION")
    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipDisplay: TooltipDisplay,
        tooltipAdder: Consumer<net.minecraft.network.chat.Component>,
        flag: TooltipFlag,
    ) = backing.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag)

    override fun getTooltipImage(stack: ItemStack): Optional<TooltipComponent> = backing.getTooltipImage(stack)

    override fun getName(stack: ItemStack): net.minecraft.network.chat.Component = backing.getName(stack)

    override fun isFoil(stack: ItemStack): Boolean = backing.isFoil(stack)

    override fun useOnRelease(stack: ItemStack): Boolean = backing.useOnRelease(stack)

    override fun canFitInsideContainerItems(): Boolean = backing.canFitInsideContainerItems()

    override fun shouldPrintOpWarning(stack: ItemStack, player: Player?): Boolean = backing.shouldPrintOpWarning(stack, player)
}

/**
 * Bow behavior contains concrete [net.minecraft.world.item.ProjectileWeaponItem] checks in vanilla
 * code, so forwarding [Item] methods cannot preserve it. Keeping the native item a real [BowItem]
 * lets vanilla ammo selection, drawing, and firing operate on the authoritative custom stack.
 */
internal class NativeBowCustomItem(
    override val backing: BowItem,
    key: ResourceKey<Item>,
) : BowItem(nativeItemProperties(backing, key)), NativeBackedItem

/** Crossbow counterpart to [NativeBowCustomItem], including charging and loaded-projectile state. */
internal class NativeCrossbowCustomItem(
    override val backing: CrossbowItem,
    key: ResourceKey<Item>,
) : CrossbowItem(nativeItemProperties(backing, key)), NativeBackedItem

/** Preserves vanilla cast, retrieve, durability, event, and item-use-stat behavior. */
internal class NativeFishingRodCustomItem(
    override val backing: FishingRodItem,
    key: ResourceKey<Item>,
) : FishingRodItem(nativeItemProperties(backing, key)), NativeBackedItem

/** Preserves the backing item's player-use and dispenser projectile contract. */
internal class NativeProjectileCustomItem(
    backing: Item,
    key: ResourceKey<Item>,
) : NativeCustomItem(backing, key), ProjectileItem {
    private val projectileBacking: ProjectileItem = backing as ProjectileItem

    override fun asProjectile(
        level: Level,
        position: Position,
        stack: ItemStack,
        direction: Direction,
    ): Projectile = projectileBacking.asProjectile(level, position, stack, direction)

    override fun createDispenseConfig(): ProjectileItem.DispenseConfig =
        projectileBacking.createDispenseConfig()

    override fun shoot(
        projectile: Projectile,
        x: Double,
        y: Double,
        z: Double,
        power: Float,
        uncertainty: Float,
    ) = projectileBacking.shoot(projectile, x, y, z, power, uncertainty)
}

private fun nativeItemProperties(backing: Item, key: ResourceKey<Item>): Item.Properties {
    val properties = Item.Properties().setId(key)
    backing.components().forEach { copyComponent(properties, it) }

    val remainder = backing.craftingRemainder
    if (!remainder.isEmpty) properties.craftRemainder(remainder.item)

    val requiredFeatures = Item.Properties::class.java.getDeclaredField("requiredFeatures")
    requiredFeatures.isAccessible = true
    requiredFeatures.set(properties, backing.requiredFeatures())
    return properties
}

private fun <T : Any> copyComponent(
    properties: Item.Properties,
    component: TypedDataComponent<T>,
) {
    properties.component(component.type(), component.value())
}
