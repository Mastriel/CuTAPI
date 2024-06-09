package xyz.mastriel.cutapi.behavior

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

public class ListBehaviorHolder<B : Behavior> : BehaviorHolder<B>, MutableList<B> by mutableListOf() {
    override fun hasBehavior(behavior: KClass<out B>): Boolean {
        return getBehaviorOrNull(behavior) != null
    }

    override fun hasBehavior(behaviorId: Identifier): Boolean {
        return any { it.id == behaviorId }
    }

    override fun getAllBehaviors(): Set<B> {
        return this.toSet()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : B> getBehaviorOrNull(behaviorId: Identifier): T? {
        return find { it.id == behaviorId } as? T?
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : B> getBehaviorOrNull(behavior: KClass<T>): T? {
        return find { it::class == behavior } as? T?
    }

    override fun <T : B> getBehavior(behaviorId: Identifier): T {
        return getBehaviorOrNull(behaviorId) ?: error("Behavior $behaviorId doesn't exist on this item.")
    }

    override fun <T : B> getBehavior(behavior: KClass<T>): T {
        return getBehaviorOrNull(behavior) ?: error("Behavior ${behavior.qualifiedName} doesn't exist on this item.")
    }
}