package xyz.mastriel.cutapi.behavior

import xyz.mastriel.cutapi.items.behaviors.ItemBehavior
import kotlin.reflect.KClass

interface BehaviorHolder<B : Behavior> {

    fun hasBehavior(behavior: KClass<out B>) : Boolean

    fun <T: B> getBehavior(behavior: KClass<T>) : T

    fun <T: B> getBehaviorOrNull(behavior: KClass<T>) : T?

    fun getAllBehaviors() : Set<B>
}

inline fun <reified B: ItemBehavior, H: BehaviorHolder<B>> H.hasBehavior() = hasBehavior(B::class)
inline fun <reified B: ItemBehavior, H: BehaviorHolder<B>> H.getBehavior() = getBehavior(B::class)
inline fun <reified B: ItemBehavior, H: BehaviorHolder<B>> H.getBehaviorOrNull() = getBehaviorOrNull(B::class)