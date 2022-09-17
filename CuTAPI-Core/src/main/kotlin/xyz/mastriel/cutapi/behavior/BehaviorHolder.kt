package xyz.mastriel.cutapi.behavior

import kotlin.reflect.KClass

interface BehaviorHolder<B : Behavior> {

    fun hasBehavior(behavior: KClass<out B>) : Boolean

    fun <T: B> getBehavior(behavior: KClass<T>) : T

    fun <T: B> getBehaviorOrNull(behavior: KClass<T>) : T?

    fun getAllBehaviors() : Set<B>
}