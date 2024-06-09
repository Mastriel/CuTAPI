package xyz.mastriel.cutapi.resources

import kotlinx.serialization.*
import net.peanuuutz.tomlkt.*
import org.bukkit.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.utils.*
import java.io.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

public class ResourceManager {

    private val resources = mutableMapOf<ResourceRef<*>, Resource>()
    private val folders = mutableListOf<FolderRef>()
    private val locators: List<Locator> get() = resources.keys.toList() + folders

    private val resourceRoots = mutableListOf<Triple<CuTPlugin, File, String>>()

    public fun register(resource: Resource, overwrite: Boolean = false) {
        if (resource.ref in resources.keys && !overwrite) {
            error("Resource '${resource.ref}' already registered.")
        }
        resources[resource.ref] = resource
        Plugin.info("Registered resource ${resource.ref} [${resource::class.simpleName}]")

    }


    public fun <T : Resource> getResource(ref: ResourceRef<T>): T {
        return getResourceOrNull(ref) ?: error("$ref is not a valid resource.")
    }

    @Suppress("UNCHECKED_CAST")
    public fun <T : Resource> getResourceOrNull(ref: ResourceRef<T>): T? {
        return resources[ref] as? T
    }

    public fun isAvailable(ref: ResourceRef<*>): Boolean {
        return resources[ref] != null
    }

    public fun getFolderContents(plugin: CuTPlugin, folderRef: FolderRef): List<Locator> {
        return locators.filter { it.plugin == plugin }
            .filter { it.parent == folderRef }
    }

    public fun getAllResources(): List<Resource> {
        return resources.values.toList()
    }


    private val tempFolder = Plugin.dataFolder.appendPath("resources-tmp/").absoluteFile

    internal fun clearTemp() {
        tempFolder.deleteRecursively()
        tempFolder.mkdir()
    }

    internal fun dumpPluginResourcesToTemp(plugin: CuTPlugin) {
        val descriptor = CuTAPI.getDescriptor(plugin)
        val options = descriptor.options
        val packFolder = options.packFolder

        val dumpFolder = File(tempFolder, descriptor.namespace)
        dumpFolder.mkdir()

        if (!options.isFromJar) {
            Plugin.warn("Plugin '${plugin.namespace}' is not from a jar, so no resources will be dumped.")
            return
        }
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
    public fun registerResourceRoot(plugin: CuTPlugin, alias: String, folder: File) {
        resourceRoots += Triple(plugin, folder, alias)
    }

    /**
     * Removes a resource root from your plugin.
     *
     * @return true if a resource root was unregistered, false otherwise.
     */
    public fun unregisterResourceRoot(plugin: CuTPlugin, alias: String): Boolean {
        return resourceRoots.removeIf { it.first == plugin && it.third == alias }
    }

    /**
     * Remove all resource roots from your plugin.
     */
    public fun unregisterAllResourceRoots(plugin: CuTPlugin) {
        resourceRoots.removeIf { it.first == plugin }
    }

    internal fun loadPluginResources(plugin: CuTPlugin) {
        val namespace = CuTAPI.getDescriptor(plugin).namespace

        val found = mutableListOf<Pair<File, ResourceRef<*>>>()
        findResourcesInFolder(File(tempFolder, namespace), folderRef(plugin, ""), found)

        for ((_, root) in resourceRoots.filter { it.first == plugin }) {
            findResourcesInFolder(root, folderRef(plugin, ""), found)
        }
        // make sure templates go first
        // TODO make this more robust
        found.sortedByDescending { "template" in it.second.extension }.forEach {
            val (file, ref) = it
            loadResource(file, ref)
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
    private fun findResourcesInFolder(root: File, folder: FolderRef, found: MutableList<Pair<File, ResourceRef<*>>>) {
        folderRefToFile(root, folder).listFiles()?.toList()
            ?.sortedByDescending { if (it.name == "apply.meta.folder") 1 else 0 }
            ?.forEach { file ->

                val name = folder / file.name

                if (file.isDirectory) {
                    findResourcesInFolder(root, name, found)
                    return@forEach
                }

                found += file to folder.child<Resource>(file.name)
            }
    }

    /**
     * Load a resource from a file into a ResourceRef, and registers it.
     *
     * @param resourceFile The file being loaded
     * @param ref The resource ref this file will be associated with
     */
    public fun loadResource(resourceFile: File, ref: ResourceRef<*>): Boolean {
        when (val result = loadResourceWithoutRegistering(resourceFile, ref)) {
            is ResourceLoadResult.Success -> {
                if (ref.isAvailable()) {
                    Plugin.warn("Resource $ref is being overwritten in memory...")
                }
                register(result.resource, overwrite = true)
                return true
            }

            is ResourceLoadResult.Failure -> {
                Plugin.error("Failed to load resource ${ref}.")
                checkResourceLoading(ref.plugin)
                return false
            }

            else -> return false
        }
    }

    /**
     * Loads a resource from a file without registering it.
     *
     * A null return value means that the resource was a metadata file.
     */
    public fun <T : Resource> loadResourceWithoutRegistering(
        resourceFile: File,
        ref: ResourceRef<T>
    ): ResourceLoadResult<T>? {
        val metadataFile = File(resourceFile.absolutePath + ".meta")

        try {
            val resourceBytes = resourceFile.readBytes()

            // TODO we're deserializing this a lot. we should cache it or something.
            val metadataBytes = try {
                val folderTable = getFolderDefaultTable(ref)

                if (folderTable == null) {
                    metadataFile.readBytes()
                } else {
                    val metadataTable = if (metadataFile.exists())
                        CuTAPI.toml.parseToTomlTable(metadataFile.readText())
                    else
                        TomlTable()

                    val newTable = folderTable.combine(metadataTable, false)
                    CuTAPI.toml.encodeToString(newTable).toByteArray(Charsets.UTF_8)
                }
            } catch (e: Exception) {
                null
            }.let { bytes ->
                if (bytes == null) return@let null

                val depthLimit = 30
                var depth = 0
                var table = CuTAPI.toml.parseToTomlTable(bytes.toString(Charsets.UTF_8))
                while (metadataNeedsToProcessExtensions(table)) {
                    depth++
                    if (depth > depthLimit) {
                        Plugin.error("Metadata extensions for $ref infinitely (or excessively) recurse.")
                        return ResourceLoadResult.Failure()
                    }
                    table = processTemplates(table)
                }

                CuTAPI.toml.encodeToString(table).toByteArray(Charsets.UTF_8)
            }



            return tryLoadResource(ref, resourceBytes, metadataBytes)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return ResourceLoadResult.Failure()
        }
    }

    private fun metadataNeedsToProcessExtensions(table: TomlTable): Boolean {
        return table["extends"]?.asTomlTable() != null
    }


    private fun processTemplates(table: TomlTable): TomlTable {
        var metadata = table

        val extensions = metadata["extends"]?.asTomlTable() ?: return metadata

        metadata = TomlTable(metadata.filter { (k, _) -> k != "extends" })

        val refs = extensions.map { (k, v) ->
            val templateRef = ref<TemplateResource>(k)
            val map = v.asTomlArray().map { i -> i.asTomlTable().mapValues { (_, v) -> v.asTomlLiteral() } }
            templateRef to map
        }
        println(refs)


        val newMetadata = refs.fold(metadata) { acc, r ->
            val templateTable = r.first.getResource() ?: return@fold acc
            val patchedTemplates = r.second.map { templateTable.getPatchedTable(it) }

            patchedTemplates.fold(acc) { acc2, t ->
                acc2.combine(t, true)
            }
        }

        return newMetadata
    }

    /**
     * Loads a resource from just a ref, resourceBytes, and metadataBytes.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Resource> tryLoadResource(
        ref: ResourceRef<T>,
        resourceBytes: ByteArray,
        metadataBytes: ByteArray?
    ): ResourceLoadResult<T>? {
        // make sure that the loaders are sorted by dependencies
        // otherwise we can get some bad errors.
        // circulars aren't allowed
        val loaders = ResourceFileLoader.getDependencySortedLoaders() as List<ResourceFileLoader<Resource>>

        if (loaders.isEmpty()) {
            Plugin.error("No loaders found for resource $ref.")
            checkResourceLoading(ref.plugin)
            return ResourceLoadResult.Failure()
        }

        for (loader in loaders) {
            when (val result = loader.loadResource(ref, resourceBytes, metadataBytes)) {
                is ResourceLoadResult.Success -> {
                    createClones(result.resource, resourceBytes, metadataBytes)
                    return result as ResourceLoadResult<T>
                }

                is ResourceLoadResult.WrongType -> continue

                is ResourceLoadResult.Failure -> {
                    return result as ResourceLoadResult<T>
                }
            }
        }
        // ignore .meta files not loading because we can assume
        // they aren't resources.
        if (!ref.extension.endsWith("meta")) {
            Plugin.error("No loaders found for resource $ref. [tried ${loaders.joinToString { it.id.toString() }}]")
            checkResourceLoading(ref.plugin)
            return ResourceLoadResult.Failure()
        } else {
            return null
        }
    }

    private fun createClones(resource: Resource, resourceBytes: ByteArray, metadataBytes: ByteArray?) {
        if (metadataBytes == null) return
        val originMetadataTable = CuTAPI.toml.parseToTomlTable(metadataBytes.toString(Charsets.UTF_8))
        val cloneBlocks = try {
            originMetadataTable.getArrayOrNull("clone") ?: listOf()
        } catch (ex: Exception) {
            ex.printStackTrace()
            listOf()
        }


        for (cloneBlock in cloneBlocks.mapNotNull { it as? TomlTable }) {
            try {
                val originMetadataMap = originMetadataTable.toMutableMap()
                // we don't want infinite cloning
                originMetadataMap.remove("clone")

                val patchedMetadata = TomlTable(originMetadataMap)

                val newTable = patchedMetadata.combine(cloneBlock, false)

                val newRefSubId = newTable.getStringOrNull("clone_sub_id")
                    ?: throw SerializationException("Clone block must have a 'clone_sub_id' field.")

                val newMetadataBytes = CuTAPI.toml.encodeToString(newTable).toByteArray(Charsets.UTF_8)
                val newRef = resource.ref.cloneSubId(newRefSubId)

                when (val result = tryLoadResource(newRef, resourceBytes, newMetadataBytes)) {
                    is ResourceLoadResult.Success -> {
                        register(result.resource, overwrite = true)
                    }

                    is ResourceLoadResult.Failure -> {
                        Plugin.error("Failed to clone resource ${resource.ref}. (wrong type)")
                        checkResourceLoading(resource.plugin)
                    }

                    is ResourceLoadResult.WrongType -> {
                        Plugin.error("Failed to clone resource ${resource.ref}. (wrong type)")
                        checkResourceLoading(resource.plugin)
                    }

                    else -> {}
                }

            } catch (ex: Exception) {
                Plugin.error("Failed to clone resource ${resource.ref}.")
                ex.printStackTrace()
            }

        }
    }

    public fun writeToResourceTmpIfNeeded(
        plugin: CuTPlugin,
        ref: ResourceRef<*>,
        resourceFile: File,
        metadataFile: File
    ) {
        var pluginFolder = CuTAPI.resourcePackManager.tempFolder.appendPath(plugin.namespace)

        for (path in ref.pathList.dropLast(1)) {
            pluginFolder = pluginFolder.appendPath(path)
        }

        val tempResourceFile = pluginFolder.appendPath(ref.name)
        val tempMetadataFile = pluginFolder.appendPath(ref.name + ".meta")

        if (!tempResourceFile.exists()) resourceFile.copyTo(tempResourceFile)
        if (!tempMetadataFile.exists()) resourceFile.copyTo(tempMetadataFile)

    }

    private fun getFolderDefaultTable(ref: ResourceRef<*>): TomlTable? {
        val parent = ref.parent ?: return null

        val resource = parent.child<FolderApplyResource>("apply.meta.folder")
        return resource.getResource()?.metadata?.applyTable
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
                if (strictResourceLoading && ref.plugin.plugin != Plugin) {
                    Bukkit.getPluginManager().disablePlugin(ref.plugin.plugin)
                }
            }
        }
    }
}


