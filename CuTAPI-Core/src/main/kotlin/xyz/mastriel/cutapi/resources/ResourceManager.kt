package xyz.mastriel.cutapi.resources

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.copyResourceDirectory
import kotlin.collections.plusAssign
import java.io.File

class ResourceManager {

    private val resources = mutableMapOf<ResourceRef<*>, Resource>()
    private val folders = mutableListOf<FolderRef>()
    private val locators : List<Locator> get() = resources.keys.toList() + folders

    fun register(resource: Resource) {
        if (resource.ref in resources.keys) {
            error("Resource '${resource.ref}' already registered.")
        }
        resources[resource.ref] = resource
    }


    fun <T: Resource> getResource(ref: ResourceRef<T>) : T {
        return getResourceOrNull(ref) ?: error("$ref is not a valid resource.")
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Resource> getResourceOrNull(ref: ResourceRef<T>) : T? {
        return resources[ref] as? T
    }

    fun isAvailable(ref: ResourceRef<*>) : Boolean {
        return resources[ref] != null
    }

    fun getFolderContents(plugin: Plugin, folderRef: FolderRef) : List<Locator> {
        return locators.filter { it.plugin == plugin }
            .filter { it.parent == folderRef }
    }

    fun getAllResources() : List<Resource> {
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

    internal fun loadPluginResources(plugin: Plugin) {
        loadResourcesFromFolder(folderRef(plugin, ""), plugin)
    }

    private fun folderRefToFile(folderRef: FolderRef) : File {
        val namespace = CuTAPI.getDescriptor(folderRef.plugin).namespace
        var path = File(tempFolder, namespace)
        for (folder in folderRef.pathList) {
            path = File(path, folder)
        }
        return path
    }

    private fun resourceRefToFile(ref: ResourceRef<*>) : File {
        return folderRefToFile(ref.parent!!).appendPath(ref.name)
    }

    private fun loadResourcesFromFolder(folder: FolderRef, plugin: Plugin) {
        folderRefToFile(folder).listFiles()?.toList()?.forEach { file ->
            val name = folder / file.name

            if (file.isDirectory) {
                loadResourcesFromFolder(name, plugin)
                return@forEach
            }

            loadResource(file, folder.child<Resource>(file.name))

        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadResource(resourceFile: File, ref: ResourceRef<*>) {
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

            for (loader in loaders) {
                when (val result = loader.loadResource(ref, resourceBytes, metadataBytes)) {
                    is ResourceLoadResult.Success -> {
                        register(result.resource)
                        Plugin.info("Registered resource $ref [${result.resource::class.simpleName}]")
                    }

                    is ResourceLoadResult.WrongType -> continue
                    is ResourceLoadResult.Failure -> {
                        Plugin.error("Failed to load resource ${ref}.")
                        checkResourceLoading(ref.plugin)
                    }
                }
            }
        } catch (e: Exception) {
            Plugin.error(e)
        }
    }



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


