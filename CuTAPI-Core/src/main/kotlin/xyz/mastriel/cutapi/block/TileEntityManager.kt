package xyz.mastriel.cutapi.block

import org.bukkit.Location
import org.bukkit.World

class TileEntityManager(val world: World) {
    private val _tileEntities = mutableSetOf<TileEntity>()
    val tileEntities = _tileEntities as Set<TileEntity>

    fun add(tileEntity: TileEntity) {
        _tileEntities += tileEntity
    }

    fun remove(tileEntity: TileEntity) {
        _tileEntities -= tileEntity
    }

    fun get(location: Location) {
        tileEntities.find { it.location.toBlockLocation() == location.toBlockLocation() }
    }
}