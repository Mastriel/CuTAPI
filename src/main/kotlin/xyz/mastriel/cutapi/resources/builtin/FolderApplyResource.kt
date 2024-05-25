package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.TomlTable
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.registry.id
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.CuTMeta

class FolderApplyResource(
    ref: ResourceRef<FolderApplyResource>,
    metadata: Metadata
) : MetadataResource<FolderApplyResource.Metadata>(ref, metadata) {

    @Serializable
    data class Metadata(
        @SerialName("apply")
        val applyTable: TomlTable = TomlTable()
    ) : CuTMeta()
}


val FolderApplyResourceLoader = metadataResourceLoader(
    listOf("meta.folder"),
    id(Plugin, "folder_apply"),
    FolderApplyResource.Metadata.serializer()
) {

    if (ref.name != "apply.meta.folder") {
        return@metadataResourceLoader wrongType()
    }
    val meta = CuTAPI.toml.decodeFromString<FolderApplyResource.Metadata>(metadataBytes!!.toString(Charsets.UTF_8))
    success(FolderApplyResource(ref.cast(), metadata!!))
}