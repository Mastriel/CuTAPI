package xyz.mastriel.cutapi.nms

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Used to mark a class as using NMS.
 *
 * If you're using CuTAPI, then you should avoid classes with this annotation.
 */
@Internal
@RequiresOptIn("This uses NMS, which is not recommended for use in plugins.")
@Retention(AnnotationRetention.BINARY)
public annotation class UsesNMS()
