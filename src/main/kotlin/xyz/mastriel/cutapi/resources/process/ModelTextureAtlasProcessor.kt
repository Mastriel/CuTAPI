package xyz.mastriel.cutapi.resources.process

import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*
import java.io.*

/**
 * Adds textures reached by emitted item definitions to Minecraft's model atlases.
 *
 * Minecraft 1.21.11 no longer keeps item and block model textures in one atlas. A texture's
 * CuTAPI ref remains folder-independent; the models that consume it determine the atlas entry.
 */
public val ModelTextureAtlasProcessor: ResourceProcessor = resourceProcessor<Resource> {
    generateModelTextureAtlases(resources)
}

internal enum class ModelTextureAtlas(
    val fileName: String,
    val automaticDirectory: String,
) {
    Items("items", "item"),
    Blocks("blocks", "block"),
}

internal data class ModelTextureAtlasAssignments(
    val items: Set<ResourceRef<Texture2D>>,
    val blocks: Set<ResourceRef<Texture2D>>,
)

internal fun generateModelTextureAtlases(resources: List<Resource>) {
    val assignments = resolveModelTextureAtlasAssignments(resources)
    writeTextureAtlas(ModelTextureAtlas.Items, assignments.items)
    writeTextureAtlas(ModelTextureAtlas.Blocks, assignments.blocks)
}

internal fun resolveModelTextureAtlasAssignments(resources: List<Resource>): ModelTextureAtlasAssignments {
    val modelsByLocator = resources.filterIsInstance<MinecraftModel>()
        .associateBy { it.ref.toMinecraftModelLocator() }
    val texturesByLocator = resources.filterIsInstance<Texture2D>()
        .associateBy { it.ref.toMinecraftLocator() }
    val assignments = linkedMapOf<ResourceRef<Texture2D>, ModelTextureAtlas>()

    resources.filterIsInstance<ItemModel>()
        .filter { it.metadata.emit }
        .forEach { itemModel ->
            val dependencies = itemModel.findModelTextureDependencies(modelsByLocator, texturesByLocator)
            require(dependencies.atlasHints.size <= 1) {
                "Item model ${itemModel.ref} mixes textures from the items and blocks atlases. " +
                    "Minecraft 1.21.11 requires every texture in one item model to use the same atlas."
            }
            val atlas = dependencies.atlasHints.singleOrNull() ?: ModelTextureAtlas.Items

            dependencies.textures
                .filter { it.metadata.emit }
                .filterNot { it.isAutomaticallyIncludedIn(atlas) }
                .forEach { texture ->
                    val previous = assignments.putIfAbsent(texture.ref, atlas)
                    require(previous == null || previous == atlas) {
                        "Texture ${texture.ref} is required in both the ${previous!!.fileName} and " +
                            "${atlas.fileName} atlases. Minecraft 1.21.11 requires a sprite name to " +
                            "belong to only one model atlas."
                    }
                }
        }

    return ModelTextureAtlasAssignments(
        items = assignments.filterValues { it == ModelTextureAtlas.Items }.keys,
        blocks = assignments.filterValues { it == ModelTextureAtlas.Blocks }.keys,
    )
}

private data class ModelTextureDependencies(
    val textures: Set<Texture2D>,
    val atlasHints: Set<ModelTextureAtlas>,
)

private fun ItemModel.findModelTextureDependencies(
    modelsByLocator: Map<String, MinecraftModel>,
    texturesByLocator: Map<String, Texture2D>,
): ModelTextureDependencies {
    val textures = linkedSetOf<Texture2D>()
    val atlasHints = linkedSetOf<ModelTextureAtlas>()
    val visitedModels = mutableSetOf<String>()

    fun visitModel(locator: String) {
        if (!visitedModels.add(locator)) return
        val model = modelsByLocator[locator] ?: return

        (model.json["textures"] as? JsonObject)?.values?.forEach { value ->
            val primitive = value as? JsonPrimitive ?: return@forEach
            if (!primitive.isString || primitive.content.startsWith("#")) return@forEach
            val textureLocator = primitive.content.toMinecraftTextureLookupLocator()
            textureLocator.defaultModelAtlas()?.let(atlasHints::add)
            texturesByLocator[textureLocator]?.let(textures::add)
        }

        val parent = model.json["parent"] as? JsonPrimitive
        if (parent?.isString == true) visitModel(parent.content.toMinecraftModelLookupLocator())
    }

    json.collectMinecraftModelReferences().forEach { visitModel(it.toMinecraftModelLookupLocator()) }
    return ModelTextureDependencies(textures, atlasHints)
}

private fun JsonElement.collectMinecraftModelReferences(): List<String> = buildList {
    fun collect(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (key in modelReferenceFields && value is JsonPrimitive && value.isString) {
                    add(value.content)
                } else {
                    collect(value)
                }
            }
            is JsonArray -> element.forEach(::collect)
            else -> Unit
        }
    }
    collect(this@collectMinecraftModelReferences)
}

private fun String.toMinecraftModelLookupLocator(): String = toMinecraftLookupLocator("model.json")

private fun String.toMinecraftTextureLookupLocator(): String = toMinecraftLookupLocator("png")

private fun String.toMinecraftLookupLocator(extension: String): String {
    if ("://" !in this) return if (":" in this) this else "minecraft:$this"
    val (rootName, resourcePath) = split("://", limit = 2)
    val namespace = rootName.substringBefore(Locator.ROOT_SEPARATOR)
    val path = sanitizeResourcePath(resourcePath.removeSuffix(".$extension"))
    return "$namespace:$path"
}

private fun String.defaultModelAtlas(): ModelTextureAtlas? {
    val path = substringAfter(":", missingDelimiterValue = this)
    return when {
        path == "item" || path.startsWith("item/") -> ModelTextureAtlas.Items
        path == "block" || path.startsWith("block/") -> ModelTextureAtlas.Blocks
        else -> null
    }
}

private fun Texture2D.isAutomaticallyIncludedIn(atlas: ModelTextureAtlas): Boolean {
    val path = sanitizeResourcePath(ref.path(withExtension = false))
    return path == atlas.automaticDirectory || path.startsWith("${atlas.automaticDirectory}/")
}

internal fun textureAtlasJson(textures: Collection<ResourceRef<Texture2D>>): JsonObject = buildJsonObject {
    put("sources", buildJsonArray {
        textures.map(ResourceRef<Texture2D>::toMinecraftLocator)
            .distinct()
            .sorted()
            .forEach { locator ->
                add(buildJsonObject {
                    put("type", "single")
                    put("resource", locator)
                })
            }
    })
}

private fun writeTextureAtlas(atlas: ModelTextureAtlas, textures: Set<ResourceRef<Texture2D>>) {
    if (textures.isEmpty()) return
    val file = File(
        CuTAPI.resourcePackManager.tempFolder,
        "assets/minecraft/atlases/${atlas.fileName}.json",
    )
    writePackResource(
        file,
        CuTAPI.json.encodeToString(JsonObject.serializer(), textureAtlasJson(textures)).toByteArray(),
    )
}

private val modelReferenceFields: Set<String> = setOf("model", "base")
