package xyz.mastriel.cutapi.resources.builtin

import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

public class TemplateResource(
    ref: ResourceRef<TemplateResource>,
    metadata: Metadata,
    private val body: ResourceConfigMap
) : Resource(ref, metadata) {
    @ResourceMetadata(id = "cutapi:template")
    public class Metadata : CuTMeta()

    public fun getPatchedConfig(
        baseRef: ResourceRef<*>,
        arguments: ResourceConfigMap
    ): ResourceConfigMap = patch(body, baseRef, arguments).asConfigMap()

    private fun patch(
        value: ResourceConfigValue,
        baseRef: ResourceRef<*>,
        arguments: ResourceConfigMap
    ): ResourceConfigValue = when (value) {
        is ResourceConfigMap -> value.copy(values = value.mapValues { (_, child) -> patch(child, baseRef, arguments) })
        is ResourceConfigList -> value.copy(values = value.map { patch(it, baseRef, arguments) })
        is TaggedResourceConfig -> value.copy(value = patch(value.value, baseRef, arguments))
        is ResourceConfigScalar -> {
            val text = value.value as? String ?: return value
            val exactKey = PLACEHOLDER.matchEntire(text)?.groupValues?.get(1)
            if (exactKey != null && exactKey != "@ref") return arguments[exactKey] ?: value

            var replaced = text.replace("{{@ref}}", baseRef.toString())
            for ((key, replacement) in arguments) {
                val scalar = replacement as? ResourceConfigScalar ?: continue
                replaced = replaced.replace("{{${key}}}", scalar.value?.toString() ?: "null")
            }
            value.copy(value = replaced)
        }
    }

    private companion object {
        private val PLACEHOLDER: Regex = """\{\{([^}]+)}}""".toRegex()
    }
}

public val TemplateResourceLoader: ResourceFileLoader<TemplateResource> =
    resourceLoader<TemplateResource, TemplateResource.Metadata>(listOf("template")) {
        val document = ResourceYaml.parse(dataAsString, ref.toString())
        if (document.tag != null) {
            return@resourceLoader failure(
                ResourceConfigException("Template bodies must be untagged mappings.", document.root.span)
            )
        }
        success(TemplateResource(ref.cast(), metadata ?: TemplateResource.Metadata(), document.requireMap()))
    }

public typealias SerializableTemplateRef = ResourceRef<TemplateResource>
