package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

/** A reusable list of texture post-processors. */
public class PostProcessDefinitionsResource(
    override val ref: ResourceRef<PostProcessDefinitionsResource>,
    override val metadata: Data
) : MetadataResource<PostProcessDefinitionsResource.Data>(ref, metadata) {
    @ResourceMetadata(id = "cutapi:post_process_definitions")
    public data class Data(
        public val postProcess: List<TaggedResourceConfig>
    ) : CuTMeta() {
        public val postProcessors: List<TexturePostprocessTable>
            get() = postProcess.map(TexturePostprocessTable::fromConfig)
    }

    public companion object {
        public val Loader: ResourceFileLoader<PostProcessDefinitionsResource> =
            metadataResourceLoader<PostProcessDefinitionsResource, Data>(listOf("ppdef")) {
                success(PostProcessDefinitionsResource(ref, metadata!!))
            }
    }
}
