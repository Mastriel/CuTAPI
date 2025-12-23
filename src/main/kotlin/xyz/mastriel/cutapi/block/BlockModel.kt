package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.minecraft.*

public sealed class BlockTextures {

    public data class Single(val texture: ResourceRef<Texture2D>) : BlockTextures() {
        override fun getAll(): All = All(texture, texture, texture, texture, texture, texture)
        override fun getVanillaModelParent(): ResourceRef<Model3D> = ref(MinecraftAssets, "block/cube_all.json")
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
        override fun getVanillaModelParent(): ResourceRef<Model3D> = ref(MinecraftAssets, "block/cube.json")
    }

    public data class Column(
        val up: ResourceRef<Texture2D>,
        val down: ResourceRef<Texture2D>,
        val side: ResourceRef<Texture2D>,
    ) : BlockTextures() {
        override fun getAll(): All = All(up, down, side, side, side, side)
        override fun getVanillaModelParent(): ResourceRef<Model3D> = ref(MinecraftAssets, "block/cube_column.json")
    }


    public abstract fun getAll(): All
    public abstract fun getVanillaModelParent(): ResourceRef<Model3D>
}

public sealed class BlockModel {
    public data class Cubic(val textures: BlockTextures) : BlockModel() {
        internal val model: Model3D = textures.getVanillaModelParent().getResource()!!
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
