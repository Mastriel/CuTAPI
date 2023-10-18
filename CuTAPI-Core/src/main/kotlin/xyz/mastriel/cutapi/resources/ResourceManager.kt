package xyz.mastriel.cutapi.resources

import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI
import xyz.mastriel.cutapi.Plugin
import xyz.mastriel.cutapi.utils.appendPath
import xyz.mastriel.cutapi.utils.copyResourceDirectory
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
        return resources.toList()
            .find { it.first.name == ref.name && it.first.plugin.name == ref.plugin.name }
            ?.second as? T?
    }

    fun getFolderContents(plugin: Plugin, folderRef: FolderRef) : List<Locator> {
        return locators.filter { it.plugin == plugin }
            .filter { it.parent == folderRef }
    }


    private val tempFolder = Plugin.dataFolder.appendPath("resources-tmp/")

    fun loadResourcesFromPlugin(plugin: Plugin) {
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
}


