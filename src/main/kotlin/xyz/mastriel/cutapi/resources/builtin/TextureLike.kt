package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.json.JsonObject
import xyz.mastriel.cutapi.resources.Resource

interface TextureLike : CustomModelDataAllocated {

    fun createItemModelData(): JsonObject

    val materials: List<String>

    val resource: Resource
}