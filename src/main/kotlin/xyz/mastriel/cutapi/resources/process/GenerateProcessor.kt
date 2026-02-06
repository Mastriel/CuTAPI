package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.*
import net.peanuuutz.tomlkt.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.*


public class GenerateResource(
    override val ref: ResourceRef<GenerateResource>,
    override val metadata: Metadata
) : MetadataResource<GenerateResource.Metadata>(ref, metadata) {

    @Serializable
    public data class Metadata(
        @SerialName("gen_id")
        val generatorId: Identifier,
        @SerialName("base_id")
        val baseId: ResourceRef<@Contextual Resource>,
        val options: TomlTable
    ) : CuTMeta()

    public companion object {
        public val Loader: ResourceFileLoader<GenerateResource> = metadataResourceLoader(
            extensions = listOf("gen.toml"),
            resourceTypeId = id(Plugin, "generate"),
            serializer = Metadata.serializer(),
        ) {
            success(GenerateResource(ref, CuTAPI.toml.decodeFromString(dataAsString)))
        }
    }
}

// processes all resources and generates new resources based on the generate blocks
internal fun generateResources(resources: List<Resource>, stage: ResourceGenerationStage) {
    for (resource in resources) {
        if (resource is GenerateResource) {
            // If the resource is a GenerateResource, we handle it separately
            val generator = ResourceGenerator.getOrNull(resource.metadata.generatorId)
                ?: error("'${resource.metadata.generatorId}' is not a valid Resource Generator for ${resource::class.simpleName}.")
            if (generator.stage != stage) continue

            val ref = resource.ref.toString().removeSuffix("gen.toml") + resource.ref.extension

            val generateBlock = object : GenerateBlock() {
                override val generatorId: Identifier = resource.metadata.generatorId
                override val subId: String? = null
                override val options: TomlTable = resource.metadata.options
            }

            val newResources = mutableListOf<Resource>()
            fun register(resource: Resource) {
                CuTAPI.resourceManager.register(resource)
                newResources.add(resource)
            }

            val baseResource = resource.metadata.baseId.getResource()
                ?: error("Base resource for GenerateResource '${resource.ref}' not found.")

            val ctx = ResourceGeneratorContext(baseResource, generateBlock, ref(ref), ::register)

            generator.generate(ctx)

            for (newResource in newResources) {
                generateResources(newResources, stage)
            }
            continue
        }

        val generators = resource.metadata?.generateBlocks ?: continue
        for (generateBlock in generators) {
            try {
                val generator = ResourceGenerator.getOrNull(generateBlock.generatorId)
                    ?: error("'${generateBlock.generatorId}' is not a valid Resource Generator for ${resource::class.simpleName}.")
                if (generator.stage != stage) return
                val subId = generateBlock.subId ?: error("No subId supplied.")

                val newResources = mutableListOf<Resource>()
                fun register(resource: Resource) {
                    CuTAPI.resourceManager.register(resource)
                    newResources.add(resource)
                }

                val ctx =
                    ResourceGeneratorContext(resource, generateBlock, resource.ref.generatedSubId(subId), ::register)

                generator.generate(ctx)

                for (newResource in newResources) {
                    generateResources(newResources, stage)
                }

            } catch (ex: Exception) {
                val subid = resource.ref.generatedSubId(generateBlock.subId ?: "<no subid>")
                Plugin.error("Failed to generate resource '$subid' from '${resource.ref}'")
                ex.printStackTrace()
            }
        }
    }
}

public fun <T : Resource> ResourceRef<T>.generatedSubId(string: String): ResourceRef<T> {
    return ref(root, "${this.path()}${Locator.GENERATED_SEPARATOR}$string.${this.extension}")
}