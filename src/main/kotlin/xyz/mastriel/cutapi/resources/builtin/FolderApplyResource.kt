package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import kotlinx.serialization.decodeFromString
import net.peanuuutz.tomlkt.TomlTable
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

public class FolderApplyResource(
    ref: ResourceRef<FolderApplyResource>,
    metadata: Metadata
) : MetadataResource<FolderApplyResource.Metadata>(ref, metadata) {

    @Serializable
    public data class Metadata(
        @SerialName("apply")
        val applyTable: TomlTable = TomlTable()
    ) : CuTMeta()
}


public val FolderApplyResourceLoader: ResourceFileLoader<MetadataResource<FolderApplyResource.Metadata>> =
    metadataResourceLoader(
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