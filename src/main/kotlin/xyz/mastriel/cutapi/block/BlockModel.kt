package xyz.mastriel.cutapi.block

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.minecraft.*

public interface BlockTextures {

    public data class Single(val texture: ResourceRef<Texture2D>) : BlockTextures {
        override fun getAll(): All = All(texture, texture, texture, texture, texture, texture)
        override fun getVanillaModelParent(): ResourceRef<MinecraftModel> = ref(MinecraftAssets, "block/cube_all.model.json")
    }

    public data class All(
        val up: ResourceRef<Texture2D>,
        val down: ResourceRef<Texture2D>,
        val north: ResourceRef<Texture2D>,
        val south: ResourceRef<Texture2D>,
        val west: ResourceRef<Texture2D>,
        val east: ResourceRef<Texture2D>
    ) : BlockTextures {
        override fun getAll(): All = this
        override fun getVanillaModelParent(): ResourceRef<MinecraftModel> = ref(MinecraftAssets, "block/cube.model.json")
    }

    public data class Column(
        val up: ResourceRef<Texture2D>,
        val down: ResourceRef<Texture2D>,
        val side: ResourceRef<Texture2D>,
    ) : BlockTextures {
        override fun getAll(): All = All(up, down, side, side, side, side)
        override fun getVanillaModelParent(): ResourceRef<MinecraftModel> = ref(MinecraftAssets, "block/cube_column.model.json")
    }

    public data class Orientable(
        val up: ResourceRef<Texture2D>,
        val down: ResourceRef<Texture2D> = up,
        val front: ResourceRef<Texture2D>,
        val side: ResourceRef<Texture2D>,
    ) : BlockTextures {
        override fun getAll(): All = All(up, down, front, side, side, side)
        override fun getVanillaModelParent(): ResourceRef<MinecraftModel> =
            ref(MinecraftAssets, "block/orientable_with_bottom.model.json")
    }


    public fun getAll(): All
    public fun getVanillaModelParent(): ResourceRef<MinecraftModel>
}

public sealed class BlockModel {
    public data class Cubic(val textures: BlockTextures) : BlockModel() {
        internal val model: MinecraftModel by lazy { textures.getVanillaModelParent().getResource()!! }
    }

    public data class Model(val model: ResourceRef<MinecraftModel>) : BlockModel() {
        public constructor(plugin: CuTPlugin, path: String) : this(ref(plugin, path))

        public constructor(stringPath: String) : this(ref(stringPath))
    }

    public fun toMinecraftModel(): MinecraftModel? {
        return when (this) {
            is Model -> model.getResource()
            is Cubic -> model
        }
    }
}
