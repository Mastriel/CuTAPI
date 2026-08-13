package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*

public data class InventoryTextureGeneratorOptions(public val texture: ResourceRef<Texture2D>) {
    public companion object : Schema<InventoryTextureGeneratorOptions> by
        schema(id(Plugin, "inventory_texture"), {
            property(InventoryTextureGeneratorOptions::texture, VariantSerializer.ResourceRef<Texture2D>())
        })
}

public val InventoryTextureGenerator: ResourceGenerator<InventoryTextureGeneratorOptions> =
    resourceGenerator<Model3D, InventoryTextureGeneratorOptions>(
        optionsSchema = InventoryTextureGeneratorOptions,
        stage = ResourceGenerationStage.BeforeProcessors
    ) {
        val model = ref<Model3D>(Plugin, "ui/inventory_bg.model3d.json").getResource()!!
        val newModel = Model3D(
            ref = ref.cast(),
            modelJson = model.modelJson.copy(
                textures = model.modelJson.textures + ("2" to options.texture.toMinecraftLocator())
            ),
            metadata = model.metadata.copy(
                textures = model.metadata.textures + ("2" to options.texture)
            )
        )
        register(newModel)
    }
