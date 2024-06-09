package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.resources.*

public interface TextureLike : CustomModelDataAllocated {

    public fun createItemModelData(): JsonObject

    public val materials: List<String>

    public val resource: Resource
}