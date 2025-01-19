package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.minecraft.*

public interface TextureLike {

    public fun createItemModelData(): JsonObject

    public fun getItemModel(): VanillaItemModel

    public val materials: List<String>

    public val resource: Resource
}


@JvmInline
public value class VanillaItemModel(public val location: String) {

    public fun toIdentifier(): Identifier {
        if (":" !in location) {
            return id(MinecraftAssets, location)
        }
        return id(location)
    }
}