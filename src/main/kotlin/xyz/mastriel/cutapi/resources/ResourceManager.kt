package xyz.mastriel.cutapi.resources

import org.bukkit.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.resources.builtin.*
import xyz.mastriel.cutapi.resources.data.*
import xyz.mastriel.cutapi.resources.process.*
import xyz.mastriel.cutapi.utils.*
import java.io.*

public class ResourceManager {

    private data class LoaderSelection(
        val loader: ResourceFileLoader<*>,
        val document: ResourceDocument? = null
    )

    private val resources = mutableMapOf<ResourceRef<*>, Resource>()
    private val folders = mutableListOf<FolderRef>()
    private val locators: List<Locator> get() = resources.keys.toList() + folders

    private val resourceRoots = mutableListOf<ResourceRoot>()

    /**
     * Registers a resource in the resource manager.
     *
     * @param resource The resource to register.
     * @param overwrite Whether to overwrite an existing resource with the same reference.
     * @param log Whether to log the registration process.
     * @throws IllegalStateException if the resource is already registered and overwrite is false.
     */
    public fun register(resource: Resource, overwrite: Boolean = false, log: Boolean = true) {
        if (resource.ref in resources.keys && !overwrite) {
            error("Resource '${resource.ref}' already registered.")
        }
        resources[resource.ref] = resource
        registerFolders(resource.ref)

        if (log) Plugin.info("Registered resource ${resource.ref} [${resource::class.simpleName}]")

        resource.subResources.forEach { register(it) }
        resource.onRegister()
    }

    /**
     * Registers all parent folders of a given resource reference.
     *
     * @param ref The resource reference whose parent folders will be registered.
     */
    private fun registerFolders(ref: ResourceRef<*>) {
        val folders = mutableListOf<FolderRef>()
        var currentRef: Locator = ref
        while (true) {
            currentRef = currentRef.parent ?: break

            if (this.folders.any { it == currentRef }) break
            folders.add(currentRef)
        }
        this.folders.addAll(folders)
    }

    /**
     * Retrieves a resource by its reference.
     *
     * @param ref The reference of the resource to retrieve.
     * @return The resource associated with the reference.
     * @throws IllegalStateException if the resource is not found.
     */
    public fun <T : Resource> getResource(ref: ResourceRef<T>): T {
        return getResourceOrNull(ref) ?: error("$ref is not a valid resource.")
    }

    /**
     * Retrieves a resource by its reference or returns null if not found.
     *
     * @param ref The reference of the resource to retrieve.
     * @return The resource associated with the reference, or null if not found.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Resource> getResourceOrNull(ref: ResourceRef<T>): T? {
        return resources[ref] as? T
    }

    /**
     * Checks if a resource is available and loaded.
     *
     * @param ref The reference of the resource to check.
     * @return True if the resource is available, false otherwise.
     */
    public fun isAvailable(ref: ResourceRef<*>): Boolean {
        return resources[ref] != null
    }

    /**
     * Retrieves the contents of a folder.
     *
     * @param root The root of the folder.
     * @param folderRef The reference to the folder.
     * @return A list of locators representing the folder's contents.
     */
    public fun getFolderContents(root: ResourceRoot, folderRef: FolderRef): List<Locator> {
        if (folderRef.isRoot) {
            return locators.filter { it.root == root }
                .filter { it.parent == null }
        }
        return locators.filter { it.root == root }
            .filter { it.parent == folderRef }
    }

    /**
     * Retrieves all resources currently registered in the resource manager.
     *
     * @return A list of all registered resources.
     */
    public fun getAllResources(): List<Resource> {
        return resources.values.toList()
    }


    private val tempFolder = Plugin.dataFolder.appendPath("resources-tmp/").absoluteFile

    /**
     * Clears the temporary folder used for resource operations.
     */
    internal fun clearTemp() {
        tempFolder.deleteRecursively()
        tempFolder.mkdir()
    }

    /**
     * Dumps plugin resources to a temporary folder.
     *
     * @param plugin The plugin whose resources will be dumped.
     */
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
     * Registers a new resource root for a plugin.
     *
     * @param resourceRoot The resource root to register.
     */
    public fun registerResourceRoot(resourceRoot: ResourceRoot) {
        resourceRoots += resourceRoot
    }

    /**
     * Retrieves a resource root by its namespace.
     *
     * @param root The namespace of the resource root.
     * @return The resource root, or null if not found.
     */
    public fun getResourceRoot(root: String): ResourceRoot? {
        return resourceRoots.find { it.namespace == root }
    }

    /**
     * Unregisters a resource root by its namespace.
     *
     * @param root The namespace of the resource root.
     * @return True if the resource root was unregistered, false otherwise.
     */
    public fun unregisterResourceRoot(root: ResourceRoot): Boolean {
        return resourceRoots.removeIf { it == root }
    }

    /**
     * Unregisters all resource roots associated with a plugin.
     *
     * @param plugin The plugin whose resource roots will be unregistered.
     */
    public fun unregisterAllResourceRoots(plugin: CuTPlugin) {
        resourceRoots.removeIf { it.cutPlugin == plugin }
    }

    /**
     * Loads all resources from a resource root.
     *
     * @param root The resource root to load resources from.
     */
    internal fun loadRootResources(root: ResourceRoot) {
        val namespace = root.namespace

        val found = mutableListOf<Pair<File, ResourceRef<*>>>()
        findResourcesInFolder(File(tempFolder, namespace), folderRef(root, ""), found)
        val sortedLoaders = ResourceFileLoader.getDependencySortedLoaders()
        val selectedLoaders = found.associate { (file, ref) ->
            ref to selectLoader(file, ref, sortedLoaders)
        }

        for (loader in sortedLoaders) {
            found.sortedByDescending { (file, _) ->
                when {
                    file.name == "apply.meta.folder" -> 2
                    file.extension == "template" -> 1
                    else -> 0
                }
            }.forEach { (file, ref) ->
                val selection = selectedLoaders[ref]
                if (selection?.loader == loader) {
                    loadResource(file, ref, loader, selection.document)
                }
            }
        }
    }

    private fun selectLoader(
        resourceFile: File,
        ref: ResourceRef<*>,
        loaders: List<ResourceFileLoader<*>>
    ): LoaderSelection? {
        val metadataFile = File(resourceFile.absolutePath + ".meta")
        try {
            if (metadataFile.exists()) {
                val document = ResourceYaml.parse(metadataFile.readText(), metadataFile.path)
                val tag = document.requireTypeId()
                val loader = ResourceFileLoader.getOrNull(tag)
                if (loader == null) {
                    Plugin.error("No resource loader is registered for metadata tag $tag on $ref.")
                    return null
                }
                if (!loader.acceptsExtension(ref.extension)) {
                    Plugin.error("Resource loader $tag does not accept extension '${ref.extension}' for $ref.")
                    return null
                }
                return LoaderSelection(loader, document)
            }

            val candidates = loaders.filter { it.acceptsExtension(ref.extension) }
            if (candidates.isEmpty()) return null

            val metadataResourceCandidates = candidates.filter { it.usesDataAsMetadata }
            if (metadataResourceCandidates.isNotEmpty()) {
                val document = ResourceYaml.parse(resourceFile.readText(), resourceFile.path)
                val dataTag = document.requireTypeId()
                metadataResourceCandidates.singleOrNull { it.id == dataTag }
                    ?.let { return LoaderSelection(it, document) }
                Plugin.error("No metadata resource loader for extension '${ref.extension}' accepts tag $dataTag on $ref.")
                return null
            }

            if (candidates.size == 1) return LoaderSelection(candidates.single())
            Plugin.error(
                "Resource $ref matches multiple loaders (${candidates.joinToString { it.id.toString() }}); " +
                    "add a tagged .meta file."
            )
        } catch (exception: Exception) {
            Plugin.error("Cannot select a resource loader for $ref: ${exception.message}")
            checkResourceLoading(ref.plugin)
        }
        return null
    }

    /**
     * Converts a [FolderRef] to a filesystem file handle, given a root to be based off of.
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
     * Recursively finds resources in a folder and adds them to a list.
     *
     * @param root The root folder to search in.
     * @param folder The current folder reference.
     * @param found The list to add found resources to.
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

                if (file.isResourceMetadataSidecar()) return@forEach

                found += file to folder.child<Resource>(file.name)
            }
    }

    /**
     * Loads a resource from a file and registers it.
     *
     * @param resourceFile The file to load the resource from.
     * @param ref The reference of the resource.
     * @param withLoader The loader to use for loading the resource.
     * @param options Additional options for loading the resource.
     * @return The result of the resource loading process.
     */
    public fun loadResource(
        resourceFile: File,
        ref: ResourceRef<*>,
        withLoader: ResourceFileLoader<*>,
        options: ResourceLoadOptions.() -> Unit = { }
    ): ResourceLoadResult<*> = loadResource(resourceFile, ref, withLoader, null, options)

    private fun loadResource(
        resourceFile: File,
        ref: ResourceRef<*>,
        withLoader: ResourceFileLoader<*>,
        parsedDocument: ResourceDocument?,
        options: ResourceLoadOptions.() -> Unit = { }
    ): ResourceLoadResult<*> {
        @Suppress("UNCHECKED_CAST")
        when (val result =
            loadResourceWithoutRegistering(
                resourceFile,
                ref,
                withLoader as ResourceFileLoader<Resource>,
                parsedDocument,
                options
            )) {
            is ResourceLoadResult.Success -> {
                val loadOptions = ResourceLoadOptions().apply(options)
                if (ref.isAvailable()) {
                    if (loadOptions.log) Plugin.warn("Resource $ref is being overwritten in memory...")
                }
                register(result.resource, overwrite = true, log = loadOptions.log)
                return result
            }

            is ResourceLoadResult.Failure -> {
                val loadOptions = ResourceLoadOptions().apply(options)
                if (loadOptions.log) {
                    Plugin.error("Failed to load resource ${ref}.")
                    if (result.exception != null) {
                        result.exception.printStackTrace()
                    }
                }
                checkResourceLoading(ref.plugin)
                return result
            }

            is ResourceLoadResult.WrongType -> {
                return result
            }
        }
    }

    /**
     * Loads a resource from a file without registering it.
     *
     * @param resourceFile The file to load the resource from.
     * @param ref The reference of the resource.
     * @param loader The loader to use for loading the resource.
     * @param options Additional options for loading the resource.
     * @return The result of the resource loading process.
     */
    public fun <T : Resource> loadResourceWithoutRegistering(
        resourceFile: File,
        ref: ResourceRef<T>,
        loader: ResourceFileLoader<T>,
        options: ResourceLoadOptions.() -> Unit = { }
    ): ResourceLoadResult<T> = loadResourceWithoutRegistering(resourceFile, ref, loader, null, options)

    private fun <T : Resource> loadResourceWithoutRegistering(
        resourceFile: File,
        ref: ResourceRef<T>,
        loader: ResourceFileLoader<T>,
        parsedDocument: ResourceDocument?,
        options: ResourceLoadOptions.() -> Unit = { }
    ): ResourceLoadResult<T> {
        val loadOptions = ResourceLoadOptions().apply(options)

        val metadataFile = File(resourceFile.absolutePath + ".meta")

        try {
            val resourceBytes = resourceFile.readBytes()

            val metadataDocument = if (loadOptions.metadata != null) {
                null
            } else if (loader.usesDataAsMetadata) {
                parsedDocument ?: ResourceYaml.parse(resourceBytes.toString(Charsets.UTF_8), resourceFile.path)
            } else {
                loadMetadata(metadataFile, ref, parsedDocument)
            }

            return tryLoadResource(ref, resourceBytes, metadataDocument, loader, loadOptions)
        } catch (ex: Exception) {
            ex.printStackTrace()
            return ResourceLoadResult.Failure(ex)
        }
    }

    /**
     * Loads metadata for a resource from a file.
     *
     * @param metadataFile The file containing the metadata.
     * @param ref The reference of the resource.
     * @return The parsed metadata document, or null if not found.
     */
    private fun loadMetadata(
        metadataFile: File,
        ref: ResourceRef<*>,
        parsedFileDocument: ResourceDocument? = null
    ): ResourceDocument? {
        val folderDocument = getFolderDefaultDocument(ref)
        val fileDocument = parsedFileDocument ?: if (metadataFile.exists()) {
            ResourceYaml.parse(metadataFile.readText(), metadataFile.path).also { it.requireTypeId() }
        } else null

        var document = when {
            folderDocument != null && fileDocument != null -> folderDocument.merge(fileDocument, combineLists = true)
            fileDocument != null -> fileDocument
            folderDocument != null -> folderDocument
            else -> return null
        }

        var depth = 0
        while (metadataNeedsToProcessExtensions(document)) {
            depth++
            if (depth > 30) {
                throw ResourceDocumentException("Metadata templates for $ref recurse more than 30 levels.")
            }
            document = processTemplates(ref, document)
        }
        return document
    }

    /**
     * Checks if a metadata document needs to process template extensions.
     *
     * @return True if extensions need to be processed, false otherwise.
     */
    private fun metadataNeedsToProcessExtensions(document: ResourceDocument): Boolean {
        return document.requireMap()["extends"] != null
    }

    /**
     * Processes templates in a metadata document.
     *
     * @param ref The reference of the resource.
     * @return The processed metadata document.
     */
    private fun processTemplates(
        ref: ResourceRef<*>,
        document: ResourceDocument
    ): ResourceDocument {
        val root = document.requireMap()
        val extensions = root["extends"]?.requireMap() ?: return document
        var metadata = document.without("extends")

        for ((templatePath, argumentLists) in extensions) {
            val templateRef = ref<TemplateResource>(templatePath)
            val template = templateRef.getResource()
            if (template == null) {
                Plugin.error("Template $templateRef not found for resource $ref.")
                continue
            }
            for (arguments in argumentLists.requireList()) {
                metadata = metadata.merge(
                    template.getPatchedDocument(ref, arguments.requireMap()),
                    combineLists = true
                )
            }
        }
        return metadata
    }

    /**
     * Attempts to load a resource from its reference, resource bytes, and metadata document.
     *
     * @param ref The reference of the resource.
     * @param resourceBytes The resource data as a byte array.
     * @param metadataDocument The parsed metadata document.
     * @param loader The loader to use for loading the resource.
     * @param options Additional options for loading the resource.
     * @return The result of the resource loading process.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Resource> tryLoadResource(
        ref: ResourceRef<T>,
        resourceBytes: ByteArray,
        metadataDocument: ResourceDocument?,
        loader: ResourceFileLoader<T>,
        options: ResourceLoadOptions = ResourceLoadOptions()
    ): ResourceLoadResult<T> {

        when (val result = loader.loadResource(ref, resourceBytes, metadataDocument, options)) {
            is ResourceLoadResult.Success -> {
                createClones(result.resource, resourceBytes, metadataDocument, loader)
                return result as ResourceLoadResult<T>
            }

            is ResourceLoadResult.WrongType -> {
                return result
            }

            is ResourceLoadResult.Failure -> {
                return result as ResourceLoadResult<T>
            }
        }
    }

    /**
     * Creates clones of a resource based on its metadata.
     *
     * @param resource The resource to clone.
     * @param resourceBytes The resource data as a byte array.
     * @param metadataDocument The parsed metadata document.
     * @param loader The loader to use for loading the clones.
     */
    @Suppress("UNCHECKED_CAST")
    private fun createClones(
        resource: Resource,
        resourceBytes: ByteArray,
        metadataDocument: ResourceDocument?,
        loader: ResourceFileLoader<*>
    ) {
        if (metadataDocument == null) return

        val cloneBlocks = extractCloneBlocks(metadataDocument)

        for (cloneBlock in cloneBlocks) {
            try {
                val newMetadata = generateCloneMetadata(metadataDocument, cloneBlock)
                val newRef = createCloneReference(resource, newMetadata)
                val loadableMetadata = newMetadata.withRoot(newMetadata.requireMap().without("cloneSubId"))

                loadAndRegisterClone(newRef, resourceBytes, loadableMetadata, loader)
            } catch (ex: Exception) {
                Plugin.error("Failed to clone resource ${resource.ref}.")
                ex.printStackTrace()
            }
        }
    }

    private fun extractCloneBlocks(document: ResourceDocument): List<ResourceDocument> {
        val cloneValue = document.requireMap()["clone"] ?: return emptyList()
        return cloneValue.requireList().mapIndexed { index, value ->
            value.requireMap()
            document.subDocument(DataPath(listOf("clone", index.toString())))
        }
    }

    private fun generateCloneMetadata(
        origin: ResourceDocument,
        cloneBlock: ResourceDocument
    ): ResourceDocument {
        return origin.without("clone").merge(cloneBlock, combineLists = false)
    }

    private fun createCloneReference(resource: Resource, metadata: ResourceDocument): ResourceRef<*> {
        val subIdNode = metadata.requireMap()["cloneSubId"]
            ?: throw ResourceDocumentException("Clone block must have a 'cloneSubId' field.", metadata.span())
        val newSubId = (subIdNode as? Variant.String)?.value
            ?: throw ResourceDocumentException(
                "Clone 'cloneSubId' must be a string.",
                metadata.span(DataPath(listOf("cloneSubId")))
            )
        return resource.ref.cloneSubId(newSubId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadAndRegisterClone(
        newRef: ResourceRef<*>,
        resourceBytes: ByteArray,
        newMetadata: ResourceDocument,
        loader: ResourceFileLoader<*>
    ) {
        when (val result =
            tryLoadResource(newRef, resourceBytes, newMetadata, loader as ResourceFileLoader<Resource>)) {
            is ResourceLoadResult.Success -> register(result.resource, overwrite = true)
            is ResourceLoadResult.Failure -> Plugin.error("Failed to clone resource $newRef. (loading failure)")
            is ResourceLoadResult.WrongType -> Plugin.error("Failed to clone resource $newRef. (wrong type)")
        }
    }

    /**
     * Writes a resource and its metadata to a temporary folder if needed.
     *
     * @param plugin The plugin associated with the resource.
     * @param ref The reference of the resource.
     * @param resourceFile The file containing the resource data.
     * @param metadataFile The file containing the metadata.
     */
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
        if (!tempMetadataFile.exists()) metadataFile.copyTo(tempMetadataFile)

    }

    /**
     * Retrieves the default metadata document for a folder.
     *
     * @param ref The reference of the folder.
     * @return The default metadata document, or null if not found.
     */
    private fun getFolderDefaultDocument(ref: ResourceRef<*>): ResourceDocument? {
        val parent = ref.parent ?: return null

        val resource = parent.child<FolderApplyResource>("apply.meta.folder")
        val apply = resource.getResource()?.metadata?.apply ?: return null
        return ResourceDocument(apply, "${resource}.apply")
    }


    /**
     * Runs checks on all registered resources.
     *
     * @throws ResourceCheckException if a resource fails its check.
     */
    internal fun checkAllResources() {
        resources.forEach { (ref, res) ->
            try {
                res.check()
            } catch (e: ResourceCheckException) {
                Plugin.error("$ref failed being checked! " + e.message)
                val strictResourceLoading = CuTAPI.getDescriptor(ref.plugin).options.strictResourceLoading
                if (strictResourceLoading && ref.plugin != Plugin) {
                    Bukkit.getPluginManager().disablePlugin(ref.plugin.plugin)
                }
            }
        }
    }
}


public class ResourceLoadOptions(
    public var overwrite: Boolean = false,
    public var metadata: CuTMeta? = null,
    public var log: Boolean = true
)

internal fun File.isResourceMetadataSidecar(): Boolean = name.endsWith(".meta")
