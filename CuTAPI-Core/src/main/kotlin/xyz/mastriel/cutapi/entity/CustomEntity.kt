package xyz.mastriel.cutapi.entity

import org.bukkit.entity.Entity
import org.bukkit.event.Listener
import xyz.mastriel.cutapi.behavior.BehaviorHolder
import xyz.mastriel.cutapi.entity.behaviors.EntityBehavior
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.Identifier
import kotlin.reflect.KClass

class CustomEntity<T : Entity>(
    override val id: Identifier,
    val type: KClass<T>,
    val descriptor: EntityDescriptor
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