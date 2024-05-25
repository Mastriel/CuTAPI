package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.CuTPlugin
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.builtin.Model3D
import xyz.mastriel.cutapi.resources.builtin.Texture2D
import xyz.mastriel.cutapi.resources.ref

sealed class BlockTextures {

    data class Single(val texture: ResourceRef<Texture2D>) : BlockTextures() {
        override fun getAll(): All = All(texture, texture, texture, texture, texture, texture)
    }

    data class All(
        val up: ResourceRef<Texture2D>,
        val down: ResourceRef<Texture2D>,
        val north: ResourceRef<Texture2D>,
        val south: ResourceRef<Texture2D>,
        val west: ResourceRef<Texture2D>,
        val east: ResourceRef<Texture2D>
    ) : BlockTextures() {
        override fun getAll(): All = this
    }

    data class Column(
        val up: ResourceRef<Texture2D>,
        val down: ResourceRef<Texture2D>,
        val side: ResourceRef<Texture2D>,
    ) : BlockTextures() {
        override fun getAll(): All = All(up, down, side, side, side, side)
    }


    abstract fun getAll(): All
}

sealed class BlockModel {
    data class Cubic(val textures: BlockTextures) : BlockModel() {
        internal val model: Model3D = TODO()
    }

    data class Model(val model: ResourceRef<Model3D>) : BlockModel() {
        constructor(plugin: CuTPlugin, path: String) : this(ref(plugin, path))

        constructor(stringPath: String) : this(ref(stringPath))
    }

    fun toModel3D(): Model3D? {
        return when (this) {
            is Model -> model.getResource()
            is Cubic -> model
        }
    }
}
