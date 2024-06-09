package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.utils.*


@Serializable
private data class HorizontalAtlasTextureGeneratorOptions(
    @SerialName("metadata")
    val generatedMetadata: Texture2D.Metadata,
    // For non-square textures, this must be supplied.
    @SerialName("width")
    val width: Int? = null,
    @SerialName("specific")
    val specificMetadata: MutableMap<Int, Texture2D.Metadata> = mutableMapOf()
)

// Splits the textures horizontally and creates an individual resource for each of them.
public val HorizontalAtlasTextureGenerator: ResourceGenerator = resourceGenerator<Texture2D>(
    id(Plugin, "h_atlas"),
    ResourceGenerationStage.BeforeProcessors
) {
    val options = castOptions(HorizontalAtlasTextureGeneratorOptions.serializer())

    val texture = this.resource
    if (texture.data.width % texture.data.height != 0 && options.width == null) {
        Plugin.warn("Texture ${texture.ref} is not square and no width was supplied. Results may be wonky.")
    }
    val width = options.width ?: (texture.data.height)

    val amountOfTextures = texture.data.width / width

    val subIdTemplate = this.suppliedSubId


    for (i in 0 until amountOfTextures) {
        val subTexture = texture.data.getSubimage(i * width, 0, width, texture.data.height).copy()
        val subId = subIdTemplate.replace("#", i.toString())
        var metadata = options.generatedMetadata.copy()
        if (options.specificMetadata.containsKey(i)) {
            metadata = metadata.apply(options.specificMetadata[i]!!, Texture2D.Metadata.serializer())
        }
        val subTextureResource = Texture2D(
            ref = texture.ref.generatedSubId(subId),
            data = subTexture,
            metadata = metadata
        )
        register(subTextureResource)
    }
}