package xyz.mastriel.cutapi.world

import org.bukkit.*
import org.bukkit.event.*
import org.bukkit.event.world.*

public class CuTWorldManager : Listener {

    private val worlds = mutableMapOf<World, CuTWorld>()

    @EventHandler
    internal fun onWorldLoad(event: WorldLoadEvent) {
        initWorld(event.world)
    }

    @EventHandler
    internal fun onWorldUnload(event: WorldUnloadEvent) {
        uninitWorld(event.world)
    }

    private fun initWorld(world: World) {
        if (worlds.containsKey(world)) {
            throw IllegalArgumentException("World ${world.name} is already initialized in CuTWorldManager!")
        }
        CuTWorld(world).also {
            worlds[world] = it
            it.initialize()
        }
    }

    private fun uninitWorld(world: World) {
        worlds[world]?.cleanup();
        worlds.remove(world);
    }

    public fun getCuTWorld(world: World): CuTWorld {
        return worlds.getOrElse(world) {
            throw IllegalArgumentException("World ${world.name} is not initialized in CuTWorldManager!")
        }
    }

    public fun getWorlds(): Collection<CuTWorld> {
        return worlds.values
    }

    public fun getWorldByType(type: World.Environment): CuTWorld? {
        return worlds.values.firstOrNull { it.handle.environment == type }
    }
}