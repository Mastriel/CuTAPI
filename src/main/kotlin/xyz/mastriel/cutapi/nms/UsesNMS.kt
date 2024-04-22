package xyz.mastriel.cutapi.nms

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Used to mark a class as using NMS.
 *
 * If you're using CuTAPI, then you should avoid classes with this annotation.
 */
@Internal
@Retention(AnnotationRetention.SOURCE)
annotation class UsesNMS()
