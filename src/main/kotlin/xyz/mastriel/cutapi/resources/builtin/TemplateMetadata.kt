package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

public class TemplateResource(
    ref: ResourceRef<TemplateResource>,
    metadata: Metadata,
    private val body: ResourceDocument
) : Resource(ref, metadata) {
    public class Metadata : CuTMeta() {
        public companion object : Schema<Metadata> by schema(id("cutapi:template"), {
            extends { CuTMeta }
        })
    }

    public fun getPatchedDocument(baseRef: ResourceRef<*>, arguments: Variant.Map): ResourceDocument =
        body.withRoot(patch(body.root, baseRef, arguments).requireMap())

    private fun patch(value: Variant, baseRef: ResourceRef<*>, arguments: Variant.Map): Variant = when (value) {
        is Variant.Map -> Variant.Map(value.mapValues { (_, child) -> patch(child, baseRef, arguments) })
        is Variant.List -> Variant.List(value.map { patch(it, baseRef, arguments) })
        is Variant.String -> {
            val exactKey = PLACEHOLDER.matchEntire(value.value)?.groupValues?.get(1)
            if (exactKey != null && exactKey != "@ref") arguments[exactKey] ?: value
            else {
                var replaced = value.value.replace("{{@ref}}", baseRef.toString())
                for ((key, replacement) in arguments) {
                    val scalar = replacement.scalarText() ?: continue
                    replaced = replaced.replace("{{${key}}}", scalar)
                }
                Variant.String(replaced)
            }
        }

        else -> value
    }

    private fun Variant.scalarText(): String? = when (this) {
        Variant.Null -> "null"
        is Variant.String -> value
        is Variant.Boolean -> value.toString()
        is Variant.Byte -> value.toString()
        is Variant.Short -> value.toString()
        is Variant.Int -> value.toString()
        is Variant.Long -> value.toString()
        is Variant.Float -> value.toString()
        is Variant.Double -> value.toString()
        is Variant.Char -> value.toString()
        is Variant.Identifier -> value.toString()
        is Variant.ResourceRef -> value.toString()
        is Variant.List, is Variant.Map -> null
    }

    private companion object {
        private val PLACEHOLDER: Regex = """\{\{([^}]+)}}""".toRegex()
    }
}

public val TemplateResourceLoader: ResourceFileLoader<TemplateResource> =
    resourceLoader<TemplateResource, TemplateResource.Metadata>(
        extensions = listOf("template"),
        metadataSchema = TemplateResource.Metadata
    ) {
        val document = ResourceYaml.parse(dataAsString, ref.toString())
        if (document.typeId() != null) {
            return@resourceLoader failure(
                ResourceDocumentException("Template bodies must be untagged mappings.", document.span(DataPath()))
            )
        }
        document.requireMap()
        success(TemplateResource(ref.cast(), metadata ?: TemplateResource.Metadata(), document))
    }

public typealias SerializableTemplateRef = ResourceRef<TemplateResource>
