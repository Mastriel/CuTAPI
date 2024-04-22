package xyz.mastriel.cutapi.utils

/**
 * Describes a piece of public code that *__will__* be changed in the future.
 *
 * Typically, this is used in places that are intended to be improved on in the future.
 *
 * Temporary APIs are not implicitly going to be removed in a future update unless marked
 * as deprecated and stated to be removed. However, they will eventually be marked as deprecated
 * and will have a better solution instead of whatever is currently present. This is here
 * just to mark that a better way of using what you're trying to use will be implemented.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class TemporaryAPI()
