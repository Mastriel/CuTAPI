package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.*

public class GenerateResource(
    override val ref: ResourceRef<GenerateResource>,
    override val metadata: Metadata
) : MetadataResource<GenerateResource.Metadata>(ref, metadata) {
    public class Metadata(
        public val generation: GenerateBlock<*>,
        public val baseId: ResourceRef<Resource>
    ) : CuTMeta() {
        public val generatorId: Identifier get() = generation.generatorId
        public val options: Any get() = generation.options

        public companion object : Schema<Metadata> by schema(id("cutapi:generate"), {
            extends { CuTMeta }
            val generator = property(
                name = "generatorId",
                serializer = VariantSerializer.Identifiable(ResourceGenerator),
                getProperty = { it.generation.generator },
                constructorParameterName = null
            )
            val base = property(Metadata::baseId, VariantSerializer.ResourceRef<Resource>())
            val optionMap = property(
                name = "options",
                serializer = VariantSerializer.Map,
                getProperty = Metadata::serializedOptions,
                constructorParameterName = null
            )
            constructs {
                val selected = value(generator)
                val rawOptions = Variant.Map(
                    value(optionMap) +
                        (SCHEMA_TYPE_DISCRIMINATOR to Variant.String(selected.id.toString()))
                )
                val block = try {
                    selected.decodeBlock(rawOptions, subId = null)
                } catch (exception: VariantNormalizationException) {
                    throw VariantNormalizationException(
                        exception.message ?: "Invalid generator options.",
                        exception.path.prepend("options"),
                        exception.expected,
                        exception.actual,
                        exception
                    )
                }
                Metadata(block, value(base))
            }
        })

        private fun serializedOptions(): Map<String, Variant> {
            @Suppress("UNCHECKED_CAST")
            val selected = generation.generator as ResourceGenerator<Any>
            return selected.optionsSchema.serialize(generation.options).getOrThrow()
                .requireMap().without(SCHEMA_TYPE_DISCRIMINATOR).value
        }
    }

    public companion object {
        public val Loader: ResourceFileLoader<GenerateResource> = metadataResourceLoader(
            extensions = listOf("gen"),
            metadataSchema = Metadata
        ) {
            success(GenerateResource(ref, metadata!!))
        }
    }
}

internal fun generateResources(resources: List<Resource>, stage: ResourceGenerationStage) {
    for (resource in resources) {
        if (resource is GenerateResource) {
            val block = resource.metadata.generation
            val generator = block.generator
            if (generator.stage != stage) continue
            val generatedRef = ref<Resource>(
                resource.ref.toString().removeSuffix("gen") + resource.ref.extension
            )
            val baseResource = resource.metadata.baseId.getResource()
                ?: error("Base resource for GenerateResource '${resource.ref}' not found.")
            val newResources = generate(generator, baseResource, block, generatedRef)
            generateResources(newResources, stage)
            continue
        }

        for (block in resource.metadata?.generateBlocks.orEmpty()) {
            try {
                val generator = block.generator
                if (generator.stage != stage) continue
                val newResources = generate(
                    generator,
                    resource,
                    block,
                    block.subId?.let(resource.ref::generatedSubId) ?: resource.ref
                )
                generateResources(newResources, stage)
            } catch (exception: Exception) {
                val subId = resource.ref.generatedSubId(block.subId ?: "<no subid>")
                Plugin.error("Failed to generate resource '$subId' from '${resource.ref}'")
                exception.printStackTrace()
            }
        }
    }
}

private fun generate(
    generator: ResourceGenerator<*>,
    resource: Resource,
    block: GenerateBlock<*>,
    ref: ResourceRef<*>
): List<Resource> {
    val generated = mutableListOf<Resource>()
    fun register(resource: Resource) {
        CuTAPI.resourceManager.register(resource)
        generated += resource
    }
    generator.generateUntyped(resource, block, ref, ::register)
    return generated
}

public fun <T : Resource> ResourceRef<T>.generatedSubId(string: String): ResourceRef<T> =
    ref(root, "${path()}${Locator.GENERATED_SEPARATOR}$string.$extension")
