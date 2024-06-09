package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*

public sealed class BlockTextures {

    public data class Single(val texture: ResourceRef<Texture2D>) : BlockTextures() {
        override fun getAll(): All = All(texture, texture, texture, texture, texture, texture)
    }

    public data class All(
        val up: ResourceRef<Texture2D>,
        val down: ResourceRef<Texture2D>,
        val north: ResourceRef<Texture2D>,
        val south: ResourceRef<Texture2D>,
        val west: ResourceRef<Texture2D>,
        val east: ResourceRef<Texture2D>
    ) : BlockTextures() {
        override fun getAll(): All = this
    }

    public data class Column(
        val up: ResourceRef<Texture2D>,
        val down: ResourceRef<Texture2D>,
        val side: ResourceRef<Texture2D>,
    ) : BlockTextures() {
        override fun getAll(): All = All(up, down, side, side, side, side)
    }


    public abstract fun getAll(): All
}

public sealed class BlockModel {
    public data class Cubic(val textures: BlockTextures) : BlockModel() {
        internal val model: Model3D = TODO()
    }

    public data class Model(val model: ResourceRef<Model3D>) : BlockModel() {
        public constructor(plugin: CuTPlugin, path: String) : this(ref(plugin, path))

        public constructor(stringPath: String) : this(ref(stringPath))
    }

    public fun toModel3D(): Model3D? {
        return when (this) {
            is Model -> model.getResource()
            is Cubic -> model
        }
    }
}
