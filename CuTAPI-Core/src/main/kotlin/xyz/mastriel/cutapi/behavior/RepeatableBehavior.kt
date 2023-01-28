package xyz.mastriel.cutapi.behavior

import kotlin.reflect.full.hasAnnotation

/**
 * By default, components are not repeatable in any sort of component holder. However,
 * you can change this by annotating your component with this class.
 *
 * This is only recommended for components that do not hold data. If a component holder has 2
 * of the same component (with the same id), they both will have the same data, which could
 * lead to unpredictable behavior.
 *
 * An [IllegalStateException](kotlin.IllegalStateException) will occur if you try to add 2 of
 * the same component to a component holder without this annotation.
 */
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class RepeatableBehavior

fun Behavior.isRepeatable() =
    this::class.hasAnnotation<RepeatableBehavior>()
