package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import xyz.mastriel.cutapi.resources.ResourceRef
import xyz.mastriel.cutapi.resources.data.CuTMeta


@Serializable
open class TemplateMetadata : CuTMeta() {
}


// what the fuck
typealias SerializableTemplateMetadataRef = ResourceRef<@Contextual MetadataResource<@Contextual TemplateMetadata>>