package xyz.mastriel.cutapi.player

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*
import kotlin.reflect.KClass

private typealias PlayerStateCtor = (UUID) -> PlayerModule

/**
 * PlayerState vs PlayerComponent
 *
 * A PlayerComponent can be read/written to a player, and is managed by the implementor.
 * A PlayerState is managed by the server, and can only be read.
 */
@Deprecated("Bad design")
object PlayerModuleManager : Listener {

    private data class PlayerStateClass(
        val ctor: PlayerStateCtor?,
        val kClass: KClass<*>,
        val preLoginHandler: PreLoginHandler<*>?
    )

    private val stateTypes = mutableMapOf<KClass<*>, PlayerStateClass>()
    private val states = mutableMapOf<UUID, MutableSet<PlayerModule>>()

    /**
     * Adds a PlayerState type to be able to automatically add/remove this state from a player when they join/leave.
     *
     * Either [ctor] or [preLoginHandler] must not be null. (XOR)
     *
     * @param kClass The class of this state.
     * @param ctor A constructor for this state. Created at [PlayerJoinEvent].
     * @param preLoginHandler A handler for creating this state. Invoked at [AsyncPlayerPreLoginEvent]. Used
     * to preform database operations to load this, make IO requests, etc.
     */
    fun <M : PlayerModule> addStateType(
        kClass: KClass<M>,
        ctor: ((UUID) -> M)? = null,
        preLoginHandler: PreLoginHandler<M>? = null
    ) {
        require(!stateTypes.containsKey(kClass)) { "State type ${kClass.qualifiedName} already exists." }
        require((ctor != null) xor (preLoginHandler != null))
            { "State type must have either a ctor arg, or a preLoginHandler arg, not neither nor both." }

        stateTypes[kClass] = PlayerStateClass(ctor, kClass, preLoginHandler)
    }

    private fun addState(playerUuid: UUID, state: PlayerModule) {
        states[playerUuid]?.add(state) ?: error("Player has no state available (offline?).")
    }

    @Suppress("unchecked_cast")
    fun <M : PlayerModule> Player.getModule(state: KClass<M>): M {
        val playerStates = states[this.uniqueId] ?: error("Player has no state available (offline?).")
        return playerStates.find { it::class == state } as? M
            ?: error("State ${state::class.qualifiedName} not found.")
    }

    inline fun <reified M : PlayerModule> Player.getModule(): M {
        return getModule(M::class)
    }

    @EventHandler
    private fun preLogin(e: AsyncPlayerPreLoginEvent) {
        states.putIfAbsent(e.uniqueId, mutableSetOf())
        stateTypes.forEach { (_, stateClass) ->
            val createdState = stateClass.preLoginHandler?.login(e)

            if (createdState != null) {
                addState(e.uniqueId, createdState)
            }
        }
    }

    @EventHandler
    @Suppress("unchecked_cast")
    private fun onJoin(e: PlayerJoinEvent) {
        val uuid = e.player.uniqueId
        stateTypes.forEach { (_, stateClass) ->
            val stateCtor = stateClass.ctor ?: return@forEach
            addState(uuid, stateCtor.invoke(uuid))
        }

        // loop over state types a second time to call the state join handler.
        stateTypes.forEach { (stateType, _) ->
            e.player.getModule(stateType as KClass<PlayerModule>).onJoin(e)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    @Suppress("unchecked_cast")
    private fun onQuit(e: PlayerQuitEvent) {
        val uuid = e.player.uniqueId
        for ((stateType) in stateTypes) {
            e.player.getModule(stateType as KClass<PlayerModule>).onLeave(e)
        }
        states.remove(uuid)
    }
}