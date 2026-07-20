package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

public class FolderApplyResource(
    ref: ResourceRef<FolderApplyResource>,
    metadata: Metadata
) : MetadataResource<FolderApplyResource.Metadata>(ref, metadata) {
    @ResourceMetadata(id = "cutapi:folder_apply")
    public data class Metadata(
        val apply: TaggedResourceConfig
    ) : CuTMeta()
}

public val FolderApplyResourceLoader: ResourceFileLoader<FolderApplyResource> =
    metadataResourceLoader<FolderApplyResource, FolderApplyResource.Metadata>(listOf("meta.folder")) {
        if (ref.name != "apply.meta.folder") return@metadataResourceLoader wrongType()
        success(FolderApplyResource(ref, metadata!!))
    }
