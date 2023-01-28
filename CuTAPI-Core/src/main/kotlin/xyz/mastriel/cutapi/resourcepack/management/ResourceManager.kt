package xyz.mastriel.cutapi.resourcepack.management

import kotlinx.serialization.encodeToString
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.resourcepack.resourcetypes.ResourceProcessor
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.createAndWrite
import xyz.mastriel.cutapi.utils.resourcePathFromFile
import java.io.File
import java.util.function.Predicate
import kotlin.reflect.KClass
import kotlin.reflect.KFunction2
import kotlin.reflect.jvm.isAccessible

private data class ResourceClass<T : ResourceWithMeta<*>>(
    val ctor: KFunction2<Plugin, String, T>,
    val kClass: KClass<out T>,
    val extensions: List<String>,
    val processor: ResourceProcessor,
    val condition: Predicate<T>?
)

class ResourceManager {
    private val resources = mutableMapOf<ResourcePath, ResourceWithMeta<*>>()

    private val resourceTypes = mutableListOf<ResourceClass<*>>()

    /**
     * Adds a resource based on its path to the map of available resources.
     *
     * @param resourcePath The path to the requested resource.
     * @return A [ResourceWithMeta] from this path.
     */
    fun addResource(resourcePath: ResourcePath): ResourceWithMeta<*>? {
        if (resources[resourcePath] != null) return getResource(resourcePath)

        val resource = loadResource(resourcePath) ?: return null

        processGenerateBlock(resource, resourcePath.plugin)

        resources[resourcePath] = resource
        return resource
    }

    private fun processGenerateBlock(resource: ResourceWithMeta<*>, plugin: Plugin) {
        resource.meta.generate.forEach {
            val meta = resource.meta

            val folder = File(resource.resourceFile.parent) appendPath it.subfolder
            folder.mkdirs()

            val resourceFile = folder appendPath resource.resourceFile.name
            val metaFile = folder appendPath resource.metaFile.name

            resource.resourceFile.copyTo(resourceFile)
            val newMeta = meta.copy(
                isGenerated = true,
                generate = listOf()
            ).apply(it.meta)

            val metaString = CuTAPI.json.encodeToString(newMeta)
            metaFile.createAndWrite(metaString)

            addResource(resourcePathFromFile(plugin, resourceFile))
        }
    }

    /**
     * Check if a resource is loaded at a certain path.
     *
     * @param path The path to the resource.
     * @return true if the resource is loaded, false otherwise.
     */
    fun hasResourceAt(path: ResourcePath): Boolean = resources.contains(path)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAllResourcesOfType(kClass: KClass<T>): Set<T> {
        return resources.values.filter { it::class == kClass }.toSet() as Set<T>
    }

    private fun loadResource(resourcePath: ResourcePath): ResourceWithMeta<*>? {
        if (resourcePath.resourceExtension == "cutmeta") return null
        val resourceClass = resourceTypes
            .find { resourcePath.resourceExtension in it.extensions }
            ?: throw UnsupportedOperationException(".${resourcePath.resourceExtension} is not a valid resource type. $resourcePath")

        val plugin = resourcePath.plugin
        val path = resourcePath.rawPath

        resourceClass.ctor.isAccessible = true
        val instance = resourceClass.ctor.invoke(plugin, path)
        resourceClass.ctor.isAccessible = false

        resources[resourcePath] = instance
        return instance
    }

    /**
     * Get a resource at a specified path. Will attempt to load if it hasn't been loaded yet.
     *
     * @param resourcePath The path to the resource.
     * @return A [ResourceWithMeta] conforming to T.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : ResourceWithMeta<*>> getResource(resourcePath: ResourcePath): T {
        val value = resources[resourcePath]
        if (value == null) addResource(resourcePath)
        return resources[resourcePath] as T
    }

    fun getAllProcessors(): List<ResourceProcessor> {
        return resourceTypes.map { it.processor }
    }

    /**
     * Add a new resource type which can be processed into the final resource pack.
     *
     * @param ctor A `T.(plugin: Plugin, path: String)` constructor used to initialize this resource. This can be
     *             private/internal.
     * @param kClass The KClass of this resource type.
     * @param extensions The extensions that this resource type can handle.
     * @param processor The resource processor for this resource type.
     * @param condition An additonal predicate that any resource must pass to be considered this type of resource.
     */
    fun <T : ResourceWithMeta<*>> addResourceType(
        ctor: KFunction2<Plugin, String, T>,
        kClass: KClass<out T>,
        extensions: List<String>,
        processor: ResourceProcessor,
        condition: Predicate<T>? = null
    ) {
        resourceTypes += ResourceClass(ctor, kClass, extensions, processor, condition)
    }

    /**
     * Gets the file of a resource.
     *
     * @param plugin The plugin this resource belongs to.
     * @param path The path of this resource.
     */
    fun getResourceFile(plugin: Plugin, path: String) : File {
        return getResourcesFolder(plugin) appendPath "/$path"
    }

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param namespace The namespace of the plugin.
     */
    fun getTexturesFolder(namespace: String) : File {
        return File(CuTAPI.resourcePackManager.tempPackFolder, "assets/minecraft/textures/custom/$namespace")
    }

    /**
     * Gets the textures folder of a plugin in the resource pack.
     *
     * @param plugin The plugin. Will fail if this plugin is not registered in CuTAPI.
     */
    fun getTexturesFolder(plugin: Plugin) : File {
        val namespace = CuTAPI.getDescriptor(plugin).namespace
        return getTexturesFolder(namespace)
    }

    /**
     * Gets the resources folder of a plugin. This is a dump of the plugin's `packFolder`.
     */
    fun getResourcesFolder(plugin: Plugin) : File {
        return File(CuTAPI.resourcePackManager.tempFolder, CuTAPI.getDescriptor(plugin).namespace)
    }

}

/**
 * Convenience method to retrieve a resource. May throw an error if this resource is not yet available/loaded.
 * Use [ref(Plugin, String)](ref) to get a reference to a resource, which then you can validate if it is ready
 * or not.
 *
 * @param plugin The plugin of the resource.
 * @param path The path to the resource.
 * @return A [ResourceWithMeta] if it is loaded, otherwise an exception is thrown.
 */
fun <T : ResourceWithMeta<*>> resource(plugin: Plugin, path: String): T {
    return CuTAPI.resourceManager.getResource(path(plugin, path))
}