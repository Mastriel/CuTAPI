package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

public class FolderApplyResource(
    ref: ResourceRef<FolderApplyResource>,
    metadata: Metadata
) : MetadataResource<FolderApplyResource.Metadata>(ref, metadata) {
    public data class Metadata(
        val apply: Variant.Map
    ) : CuTMeta() {
        public companion object : Schema<Metadata> by schema(id("cutapi:folder_apply"), {
            extends { CuTMeta }
            property(Metadata::apply, VariantSerializer.mapped(
                VariantSerializer.AnyVariant,
                serialize = { it },
                deserialize = Variant::requireMap
            ))
        })
    }
}

public val FolderApplyResourceLoader: ResourceFileLoader<FolderApplyResource> =
    metadataResourceLoader<FolderApplyResource, FolderApplyResource.Metadata>(
        listOf("meta.folder"),
        FolderApplyResource.Metadata
    ) {
        if (ref.name != "apply.meta.folder") return@metadataResourceLoader wrongType()
        success(FolderApplyResource(ref, metadata!!))
    }
