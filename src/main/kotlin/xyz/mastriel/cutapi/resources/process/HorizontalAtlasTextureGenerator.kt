package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*

public data class HorizontalAtlasTextureGeneratorOptions(
    public val metadata: Texture2D.Metadata,
    public val width: Int? = null,
    public val specificMetadata: MutableMap<Int, Texture2D.Metadata> = mutableMapOf()
) {
    public companion object : Schema<HorizontalAtlasTextureGeneratorOptions> by schema(id(Plugin, "h_atlas"), {
        property(HorizontalAtlasTextureGeneratorOptions::metadata, Texture2D.Metadata.embedded())
        property(HorizontalAtlasTextureGeneratorOptions::width, VariantSerializer.Int.nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(HorizontalAtlasTextureGeneratorOptions::specificMetadata, BuiltinSerializers.SpecificTextureMetadata) {
            optional(omitDefaults = true) { mutableMapOf() }
        }
    })
}

// Initialize the generator after its options schema has finished static initialization.
public val HorizontalAtlasTextureGenerator: ResourceGenerator<HorizontalAtlasTextureGeneratorOptions> by lazy {
    resourceGenerator<Texture2D, HorizontalAtlasTextureGeneratorOptions>(
        optionsSchema = HorizontalAtlasTextureGeneratorOptions,
        stage = ResourceGenerationStage.BeforeProcessors
    ) {
        val texture = resource
        if (texture.data.width % texture.data.height != 0 && options.width == null) {
            Plugin.warn("Texture ${texture.ref} is not square and no width was supplied. Results may be wonky.")
        }
        val width = options.width ?: texture.data.height
        val amountOfTextures = texture.data.width / width
        val template = ref.toString()

        for (index in 0 until amountOfTextures) {
            val image = texture.data.getSubimage(index * width, 0, width, texture.data.height).copy()
            val generatedRef = template.replace("*", index.toString())
            val metadata = options.specificMetadata[index]?.let {
                Texture2D.Metadata.merge(options.metadata, it)
            } ?: options.metadata.copy()
            register(Texture2D(ref(generatedRef), image, metadata))
        }
    }
}
