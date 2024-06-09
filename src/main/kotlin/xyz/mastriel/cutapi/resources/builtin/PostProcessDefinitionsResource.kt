package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

/**
 * A resource that contains a list of post-processors to apply to textures.
 *
 * This can be applied in a texture's .meta file with "extend_post_process" and
 * the resource reference to this resource.
 *
 * Example: `extend_post_process = ["cutapi://file_name.ppdef.toml"]`
 */
public class PostProcessDefinitionsResource(
    override val ref: ResourceRef<PostProcessDefinitionsResource>,
    override val metadata: Data
) : MetadataResource<PostProcessDefinitionsResource.Data>(ref, metadata) {


    @Serializable
    public data class Data(
        @SerialName("post_process")
        public val postProcessors: List<TexturePostprocessTable>
    ) : CuTMeta()

    public companion object {

        public val Loader: ResourceFileLoader<PostProcessDefinitionsResource> = metadataResourceLoader(
            extensions = listOf("ppdef.toml"),
            resourceTypeId = id(Plugin, "post_process_definitions"),
            serializer = Data.serializer()
        ) {
            success(PostProcessDefinitionsResource(ref, metadata!!))
        }
    }
}