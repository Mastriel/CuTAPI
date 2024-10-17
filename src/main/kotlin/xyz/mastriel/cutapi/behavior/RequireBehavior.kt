package xyz.mastriel.cutapi.behavior

import kotlin.reflect.*
import kotlin.reflect.full.*


/**
 * Requires other behaviors to be applied for this to function. Putting this on a behavior guarentees that
 * [behaviors] will be accessible with [BehaviorHolder.getBehavior].
 *
 * @param behaviors The behaviors required for this [Behavior] to function.
 */
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Repeatable
public annotation class RequireBehavior(vararg val behaviors: KClass<out Behavior>)

public val Behavior.requiredBehaviors: List<KClass<out Behavior>>
    get() {
        return this::class.findAnnotations(RequireBehavior::class)
            .map { it.behaviors.toList() }
            .flatten()
    }