package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.entity.*

public class EntityTagContainer(private val entity: Entity) : PDCTagContainer(entity.persistentDataContainer)

public val Entity.tags : TagContainer get() = EntityTagContainer(this)
