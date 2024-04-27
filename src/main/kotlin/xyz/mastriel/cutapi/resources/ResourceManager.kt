package xyz.mastriel.cutapi.resources

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.copyResourceDirectory
import java.io.File

class ResourceManager {

    private val resources = mutableMapOf<ResourceRef<*>, Resource>()
    private val folders = mutableListOf<FolderRef>()
    private val locators: List<Locator> get() = resources.keys.toList() + folders

    private val resourceRoots = mutableListOf<Pair<Plugin, File>>()

    fun register(resource: Resource, overwrite: Boolean = false) {
        if (resource.ref in resources.keys && !overwrite) {
            error("Resource '${resource.ref}' already registered.")
        }
        resources[resource.ref] = resource
    }


    fun <T : Resource> getResource(ref: ResourceRef<T>): T {
        return getResourceOrNull(ref) ?: error("$ref is not a valid resource.")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Resource> getResourceOrNull(ref: ResourceRef<T>): T? {
        return resources[ref] as? T
    }

    fun isAvailable(ref: ResourceRef<*>): Boolean {
        return resources[ref] != null
    }

    fun getFolderContents(plugin: Plugin, folderRef: FolderRef): List<Locator> {
        return locators.filter { it.plugin == plugin }
            .filter { it.parent == folderRef }
    }

    fun getAllResources(): List<Resource> {
        return resources.values.toList()
    }


    private val tempFolder = Plugin.dataFolder.appendPath("resources-tmp/").absoluteFile

    internal fun dumpPluginResourcesToTemp(plugin: Plugin) {
        val descriptor = CuTAPI.getDescriptor(plugin)
        val options = descriptor.options
        val packFolder = options.packFolder

        val dumpFolder = File(tempFolder, descriptor.namespace)
        dumpFolder.mkdir()

        val packFolderURI = plugin::class.java.getResource("/$packFolder")
        if (packFolderURI != null) {
            Plugin.info("Pack folder found for $plugin.")
            copyResourceDirectory(packFolderURI, dumpFolder)
        } else {
            Plugin.warn("Pack folder not found for $plugin.")
        }
    }

    /**
     * Create a new resource root for your plugin. Resources are loaded from here
     * whenever the resource pack is generated.
     */
    fun registerResourceRoot(plugin: Plugin, folder: File) {
        resourceRoots += plugin to folder
    }

    /**
     * Removes a resource root from your plugin.
     *
     * @return true if a resource root was unregistered, false otherwise.
     */
    fun unregisterResourceRoot(plugin: Plugin, folder: File): Boolean {
        if (plugin to folder !in resourceRoots) {
            return false
        }
        resourceRoots -= plugin to folder
        return true
    }

    /**
     * Remove all resource roots from your plugin.
     */
    fun unregisterAllResourceRoots(plugin: Plugin) {
        resourceRoots.removeIf { it.first == plugin }
    }

    internal fun loadPluginResources(plugin: Plugin) {
        val namespace = CuTAPI.getDescriptor(plugin).namespace

        loadResourcesFromFolder(File(tempFolder, namespace), folderRef(plugin, ""))

        for ((_, root) in resourceRoots.filter { it.first == plugin }) {
            loadResourcesFromFolder(root, folderRef(plugin, ""))
        }
    }

    /**
     * Converts a [FolderRef] to a filesystem file, given a root to be based off of.
     *
     * @param root The root folder that this is loading from.
     * @param folderRef The [FolderRef] of this folder.
     *
     * @return The folder location of this ref. Might not exist.
     */
    private fun folderRefToFile(root: File, folderRef: FolderRef): File {
        var path = root
        for (folder in folderRef.pathList) {
            path = File(path, folder)
        }
        return path
    }

    /**
     * Converts a [ResourceRef] to a filesystem file, given a root to be based off of.
     *
     * @param root The root folder that this is loading from.
     * @param ref The [ResourceRef] of this file.
     *
     * @return The file location of this ref. Might not exist.
     */
    private fun resourceRefToFile(root: File, ref: ResourceRef<*>): File {
        return folderRefToFile(root, ref.parent!!).appendPath(ref.name)
    }

    /**
     * Loads all resources from a folder [root] recursively.
     *
     * @param root The root folder this is loading from. Used for relative pathing
     * @param folder The current [FolderRef].
     *
     * Example:
     * Loading from /CuTAPI/custom, where custom is the root, and contains a folder
     * named 'abc' which itself has a file `texture.png`, [folder] could be 'cutapi://abc', and that
     * would load the `texture.png` file into 'cutapi://abc/texture.png'
     */
    private fun loadResourcesFromFolder(root: File, folder: FolderRef) {
        folderRefToFile(root, folder).listFiles()?.toList()?.forEach { file ->
            val name = folder / file.name

            if (file.isDirectory) {
                loadResourcesFromFolder(root, name)
                return@forEach
            }

            loadResource(file, folder.child<Resource>(file.name))
        }
    }

    /**
     * Load a resource from a file into a ResourceRef.
     *
     * @param resourceFile The file being loaded
     * @param ref The resource ref this file will be associated with
     */
    @Suppress("UNCHECKED_CAST")
    fun loadResource(resourceFile: File, ref: ResourceRef<*>): Boolean {
        val metadataFile = File(resourceFile.absolutePath + ".meta")

        try {
            val resourceBytes = resourceFile.readBytes()
            val metadataBytes = try {
                metadataFile.readBytes()
            } catch (e: Exception) {
                null
            }

            // try this plugin's loaders first
            val loaders =
                ResourceFileLoader.getBy(ref.plugin).toMutableList() as MutableList<ResourceFileLoader<Resource>>
            loaders += ResourceFileLoader.getAllValues()
                .filter { it.id.plugin != ref.plugin } as List<ResourceFileLoader<Resource>>

            if (loaders.isEmpty()) {
                Plugin.error("No loaders found for resource $ref.")
                checkResourceLoading(ref.plugin)
                return false
            }

            for (loader in loaders) {
                when (val result = loader.loadResource(ref, resourceBytes, metadataBytes)) {
                    is ResourceLoadResult.Success -> {
                        if (ref.isAvailable()) {
                            Plugin.warn("Resource $ref is being overwritten in memory...")
                        }
                        register(result.resource, overwrite = true)
                        Plugin.info("Registered resource $ref [${result.resource::class.simpleName}]")
                        return true
                    }

                    is ResourceLoadResult.WrongType -> continue
                    is ResourceLoadResult.Failure -> {
                        Plugin.error("Failed to load resource ${ref}.")
                        checkResourceLoading(ref.plugin)
                        return false
                    }
                }
            }
            if (!resourceFile.name.endsWith(".meta")) {
                Plugin.error("No loaders found for resource $ref. [tried ${loaders.joinToString { it.id.toString() }}]")
                checkResourceLoading(ref.plugin)
            }
            return false
        } catch (e: Exception) {
            Plugin.error(e)
            return false
        }
    }


    /**
     * Runs [Resource.check] on all resources.
     */
    internal fun checkAllResources() {
        resources.forEach { (ref, res) ->
            try {
                res.check()
            } catch (e: ResourceCheckException) {
                Plugin.error("$ref failed being checked! " + e.message)
                val strictResourceLoading = CuTAPI.getDescriptor(ref.plugin).options.strictResourceLoading
                if (strictResourceLoading) {
                    Bukkit.getPluginManager().disablePlugin(ref.plugin)
                }
            }
        }
    }
}


