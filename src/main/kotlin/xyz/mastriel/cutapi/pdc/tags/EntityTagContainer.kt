package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.entity.Entity
import org.bukkit.entity.Player

class EntityTagContainer(private val entity: Entity) : PDCTagContainer(entity.persistentDataContainer)

val Entity.tags : TagContainer get() = EntityTagContainer(this)
