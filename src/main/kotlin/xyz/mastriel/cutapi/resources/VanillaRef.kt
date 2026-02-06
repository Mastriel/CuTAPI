package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*

/**
 * Represents a reference to a vanilla resource.
 * This is a lightweight wrapper around a string that holds the reference.
 *
 * @property ref The string representation of the vanilla resource reference.
 */
@JvmInline
@Serializable
public value class VanillaRef(public val ref: String) {
}
