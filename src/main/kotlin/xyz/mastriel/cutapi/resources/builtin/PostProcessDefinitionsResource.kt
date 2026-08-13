package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

/** A reusable list of texture post-processors. */
public class PostProcessDefinitionsResource(
    override val ref: ResourceRef<PostProcessDefinitionsResource>,
    override val metadata: Data
) : MetadataResource<PostProcessDefinitionsResource.Data>(ref, metadata) {
    public data class Data(
        public val postProcess: List<TexturePostprocessTable<*>>
    ) : CuTMeta() {
        public val postProcessors: List<TexturePostprocessTable<*>> get() = postProcess

        public companion object : Schema<Data> by schema(id("cutapi:post_process_definitions"), {
            extends { CuTMeta }
            property(Data::postProcess, VariantSerializer.ListOf(TexturePostprocessTableSerializer))
        })
    }

    public companion object {
        public val Loader: ResourceFileLoader<PostProcessDefinitionsResource> =
            metadataResourceLoader(listOf("ppdef"), Data) {
                success(PostProcessDefinitionsResource(ref, metadata!!))
            }
    }
}
