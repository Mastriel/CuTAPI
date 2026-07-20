package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*

private data class InventoryTextureGeneratorOptions(
    val texture: ResourceRef<Texture2D>
)

public val InventoryTextureGenerator: ResourceGenerator = resourceGenerator<Model3D>(
    id(Plugin, "inventory_texture"),
    ResourceGenerationStage.BeforeProcessors
) {

    val options = castOptions<InventoryTextureGeneratorOptions>()

    val model = ref<Model3D>(Plugin, "ui/inventory_bg.model3d.json").getResource()!!

    val newModel = Model3D(
        ref = ref.cast(),
        modelJson = model.modelJson.copy(
            textures = model.modelJson.textures.toMutableMap().also {
                it["2"] = options.texture.toMinecraftLocator()
            }
        ),
        metadata = model.metadata.copy(
            textures = model.metadata.textures.toMutableMap().also {
                it["2"] = options.texture
            }
        )
    )

    register(newModel)
}
