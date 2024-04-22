package xyz.mastriel.cutapi.behavior

import kotlin.reflect.full.hasAnnotation

/**
 * By default, behaviors are not repeatable in any sort of behavior holder. However,
 * you can change this by annotating your behavior with this class.
 *
 * This is only recommended for behaviors that do not hold data. If a behavior holder has 2
 * of the same behavior (with the same id), they both will have the same data, which could
 * lead to unpredictable behavior.
 *
 * An [IllegalStateException](kotlin.IllegalStateException) will occur if you try to add 2 of
 * the same component to a behavior holder without this annotation.
 */
@Target(AnnotationTarget.CLASS)
@MustBeDocumented
annotation class RepeatableBehavior

fun Behavior.isRepeatable() =
    this::class.hasAnnotation<RepeatableBehavior>()
