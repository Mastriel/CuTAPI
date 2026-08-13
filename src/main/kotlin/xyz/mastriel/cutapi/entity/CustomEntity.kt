package xyz.mastriel.cutapi.entity

import org.bukkit.entity.*
import org.bukkit.event.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

public class CustomEntity<T : Entity>(
    override val id: Identifier,
    public val type: KClass<T>,
    public val descriptor: EntityDescriptor
) : Identifiable, Listener
