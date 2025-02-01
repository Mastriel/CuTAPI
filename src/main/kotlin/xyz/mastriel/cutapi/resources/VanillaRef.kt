package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*

@JvmInline
@Serializable
public value class VanillaRef(public val ref: String) {
}