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
    resourceGenerator<MinecraftModel, InventoryTextureGeneratorOptions>(
        optionsSchema = InventoryTextureGeneratorOptions,
        stage = ResourceGenerationStage.BeforeProcessors
    ) {
        val model = ref<MinecraftModel>(Plugin, "ui/inventory_bg.model.json").getResource()!!
        val newModel = MinecraftModel(
            ref = ref.cast(),
            data = model.data.copy(
                textures = model.data.textures + ("2" to options.texture.toString())
            ),
            metadata = MinecraftModel.Metadata(),
        )
        register(newModel)
    }
