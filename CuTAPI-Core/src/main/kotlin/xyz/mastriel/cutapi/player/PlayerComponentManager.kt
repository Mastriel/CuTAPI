package xyz.mastriel.cutapi.player

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import kotlin.reflect.KClass

@Deprecated("Bad design")
object PlayerComponentManager : Listener {

    private val components = mutableMapOf<Player, MutableSet<PlayerComponent>>()

    fun Player.getComponents() : MutableSet<PlayerComponent> {
        components.putIfAbsent(this, mutableSetOf())
        return components[player]!!
    }

    /**
     * @return false if the player already has component with this class, true otherwise.
     */
    fun Player.addComponent(component: PlayerComponent) : Boolean {
        val components = getComponents()
        if (hasComponent(component::class)) return false
        components.add(component)
        component.onAdd(this)
        return true
    }


    fun <C : PlayerComponent> Player.removeComponent(componentClass: KClass<C>): Boolean {
        return getComponents().removeIf { it::class == componentClass }
    }

    inline fun <reified C : PlayerComponent> Player.removeComponent() : Boolean {
        return removeComponent(C::class)
    }

    fun <C : PlayerComponent> Player.hasComponent(componentClass: KClass<C>): Boolean {
        return getComponents().any { it::class == componentClass }
    }

    inline fun <reified C : PlayerComponent> Player.hasComponent() : Boolean {
        return hasComponent(C::class)
    }

    @Suppress("unchecked_cast")
    fun <C : PlayerComponent> Player.getComponent(componentClass: KClass<C>): C? {
        val components = getComponents()
        return components.find { it::class == componentClass } as? C
    }

    inline fun <reified C : PlayerComponent> Player.getComponent() : C? {
        return getComponent(C::class)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        val player = e.player

        components[player]?.forEach {
            it.onRemove(player)
        }
        components.remove(player)
    }
}