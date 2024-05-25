package xyz.mastriel.cutapi.behavior

import xyz.mastriel.cutapi.registry.Identifier
import kotlin.reflect.KClass

interface BehaviorHolder<B : Behavior> {

    fun hasBehavior(behavior: KClass<out B>): Boolean

    fun hasBehavior(behaviorId: Identifier): Boolean

    fun <T : B> getBehavior(behavior: KClass<T>): T

    fun <T : B> getBehavior(behaviorId: Identifier): T

    fun <T : B> getBehaviorOrNull(behavior: KClass<T>): T?

    fun <T : B> getBehaviorOrNull(behaviorId: Identifier): T?


    fun getAllBehaviors(): Set<B>
}

// worst generics of all time.
// 0/10

/**
 * NOTE: You must supply a type argument!
 */
inline fun <reified B : Behavior> BehaviorHolder<in B>.hasBehavior() =
    hasBehavior(B::class)

/**
 * NOTE: You must supply a type argument!
 */
inline fun <reified B : Behavior> BehaviorHolder<in B>.getBehavior() =
    getBehavior(B::class)

/**
 * NOTE: You must supply a type argument!
 */
inline fun <reified B : Behavior> BehaviorHolder<in B>.getBehaviorOrNull() =
    getBehaviorOrNull(B::class)

inline fun <reified B : Behavior> BehaviorHolder<*>.getBehaviorsOfType(): List<B> {
    return this.getAllBehaviors().filterIsInstance<B>()
}


/**
 * @throws IllegalStateException if this is not a repeatable behavior and it's already in the holder.
 */
inline fun BehaviorHolder<*>.requireRepeatableIfExists(behavior: Behavior) {
    if (this.getAllBehaviors().any { it.id == behavior.id } && !behavior.isRepeatable())
        error("${behavior.id} lacks a RepeatableBehavior annotation to be repeatable.")
}