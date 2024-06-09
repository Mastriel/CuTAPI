package xyz.mastriel.cutapi.behavior

import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*

public interface BehaviorHolder<B : Behavior> {

    public fun hasBehavior(behavior: KClass<out B>): Boolean

    public fun hasBehavior(behaviorId: Identifier): Boolean

    public fun <T : B> getBehavior(behavior: KClass<T>): T

    public fun <T : B> getBehavior(behaviorId: Identifier): T

    public fun <T : B> getBehaviorOrNull(behavior: KClass<T>): T?

    public fun <T : B> getBehaviorOrNull(behaviorId: Identifier): T?


    public fun getAllBehaviors(): Set<B>
}

// worst generics of all time.
// 0/10

/**
 * NOTE: You must supply a type argument!
 */
public inline fun <reified B : Behavior> BehaviorHolder<in B>.hasBehavior(): Boolean =
    hasBehavior(B::class)

/**
 * NOTE: You must supply a type argument!
 */
public inline fun <reified B : Behavior> BehaviorHolder<in B>.getBehavior(): B =
    getBehavior(B::class)

/**
 * NOTE: You must supply a type argument!
 */
public inline fun <reified B : Behavior> BehaviorHolder<in B>.getBehaviorOrNull(): B? =
    getBehaviorOrNull(B::class)

public inline fun <reified B : Behavior> BehaviorHolder<*>.getBehaviorsOfType(): List<B> {
    return this.getAllBehaviors().filterIsInstance<B>()
}


/**
 * @throws IllegalStateException if this is not a repeatable behavior and it's already in the holder.
 */
public fun BehaviorHolder<*>.requireRepeatableIfExists(behavior: Behavior) {
    if (this.getAllBehaviors().any { it.id == behavior.id } && !behavior.isRepeatable())
        error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
}