package xyz.mastriel.cutapi.entity

import org.bukkit.entity.*
import org.bukkit.event.*
import xyz.mastriel.cutapi.behavior.*
import xyz.mastriel.cutapi.entity.behaviors.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

public class CustomEntity<T : Entity>(
    override val id: Identifier,
    public val type: KClass<T>,
    public val descriptor: EntityDescriptor
) : Identifiable, Listener, BehaviorHolder<EntityBehavior> {


    override fun hasBehavior(behavior: KClass<out EntityBehavior>): Boolean {
        TODO("Not yet implemented")
    }

    override fun <T : EntityBehavior> getBehavior(behavior: KClass<T>): T {
        TODO("Not yet implemented")
    }

    override fun <T : EntityBehavior> getBehaviorOrNull(behavior: KClass<T>): T? {
        TODO("Not yet implemented")
    }

    override fun hasBehavior(behaviorId: Identifier): Boolean {
        TODO("Not yet implemented")
    }

    override fun <T : EntityBehavior> getBehaviorOrNull(behaviorId: Identifier): T? {
        TODO("Not yet implemented")
    }

    override fun <T : EntityBehavior> getBehavior(behaviorId: Identifier): T {
        TODO("Not yet implemented")
    }

    override fun getAllBehaviors(): Set<EntityBehavior> {
        TODO("Not yet implemented")
    }
}