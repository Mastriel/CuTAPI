package xyz.mastriel.cutapi.items.components

import net.kyori.adventure.text.Component
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import xyz.mastriel.cutapi.items.CuTItemStack
import xyz.mastriel.cutapi.items.events.CustomItemObtainEvent
import xyz.mastriel.cutapi.nbt.tags.TagContainer
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.utils.nbt

/**
 * A Material Component is essentially a small bit of data that can be attached to any [CustomMaterial] to change
 * their behaviors. Components can have data that is saved to a [CuTItemStack]
 */
abstract class MaterialComponent(
    override val id: Identifier
) : Identifiable {

    /**
     * The lore which this component shows when applied to a [CuTItemStack].
     */
    open fun getLore(item: CuTItemStack, viewer: Player) : Component? = null

    protected fun getData(item: CuTItemStack) : TagContainer {
        val compound = item.handle.nbt
            .getOrCreateCompound("CuTAPIComponents")
            .getOrCreateCompound(id.toString())
        return TagContainer(compound)
    }

    open fun onLeftClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}
    open fun onMiddleClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}
    open fun onRightClick(player: Player, item: CuTItemStack, event: PlayerInteractEvent) {}

    open fun onDrop(player: Player, item: CuTItemStack, event: PlayerDropItemEvent) {}
    open fun onObtain(player: Player, item: CuTItemStack, event: CustomItemObtainEvent) {}

    open fun onTickInEitherHand(player: Player, item: CuTItemStack) {}
    open fun onTickInInventory(player: Player, item: CuTItemStack) {}
    open fun onTickEquipped(player: Player, item: CuTItemStack) {}

    open fun onOffhandEquip(player: Player, item: CuTItemStack, event: Cancellable) {}
    open fun onDamageEntity(attacker: LivingEntity, victim: LivingEntity, mainHandItem: CuTItemStack, event: EntityDamageByEntityEvent) {}


}