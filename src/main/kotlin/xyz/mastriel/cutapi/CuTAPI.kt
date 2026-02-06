package xyz.mastriel.cutapi

import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import kotlinx.serialization.json.*
import net.peanuuutz.tomlkt.*
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.CuTAPI.registerPlugin
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.block.breaklogic.*
import xyz.mastriel.cutapi.nms.*
import xyz.mastriel.cutapi.periodic.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.minecraft.*
import xyz.mastriel.cutapi.utils.*


/**
 * The general manager for all the API. Plugins should be registered here, so they can be properly managed.
 *
 * @see Plugin
 * @see PluginDescriptor
 */
@OptIn(UsesNMS::class)
public object CuTAPI {

    /**
     * A map of all plugins registered, along with their plugin descriptors.
     */
    private val plugins = mutableMapOf<CuTPlugin, PluginDescriptor>()
    public val registeredPlugins: Set<CuTPlugin> get() = plugins.keys

    public val resourceManager: ResourceManager = ResourceManager()
    public val resourcePackManager: ResourcePackManager = ResourcePackManager()
    public val periodicManager: PeriodicManager = PeriodicManager()
    public val serviceManager: ServiceManager = ServiceManager()
    public val blockManager: CustomBlockManager by lazy { CustomBlockManager() }
    public val minecraftAssetLoader: MinecraftAssetLoader = MinecraftAssetLoader()

    @UsesNMS
    public val playerPacketManager: PlayerPacketManager = PlayerPacketManager()

    @UsesNMS
    public val packetEventManager: PacketEventManager = PacketEventManager()
    internal val blockBreakManager = BlockBreakManager()

    public val experimentalBlockSupport: Boolean by cutConfigValue("experimental_block_support", false)

    /**
     * Most registries should probably be initialized here.
     */
    public val serverReady: EventHandlerList<Unit> = EventHandlerList()

    /**
     * Register a plugin with CuTAPI. This is used to namespace any items registered with the API, and
     * is used to hold some other additional information about the plugin.
     *
     * @param plugin The plugin being registered.
     * @param namespace The namespace being used for the plugin. By default, this is just the plugin's name
     * in all lowercase. A namespace must follow Regex `[a-zA-Z0-9/_+]+`, must be between 1 and 1024 characters, and must
     * not start or end with an underscore (_). Also, namespaces must be unique between plugins, and cannot be shared.
     * @param options The additional (optional) options that this plugin can have to alter behavior.
     * @throws IllegalArgumentException If the namespace is invalid.
     * @throws IllegalStateException If the namespace is already in use.
     * @throws IllegalStateException If the plugin is already registered.
     */
    public fun registerPlugin(
        plugin: CuTPlugin,
        namespace: String,
        options: (PluginOptionsBuilder.() -> Unit)? = null
    ) {
        requireNotRegistered(plugin)
        requireValidNamespace(namespace)

        val optionsBuilder = PluginOptionsBuilder()
        val pluginOptions = if (options != null) optionsBuilder.apply(options).build() else defaultPluginOptions()

        val descriptor = PluginDescriptor(plugin, namespace, pluginOptions)
        plugins[plugin] = descriptor

        Plugin.info("Plugin $plugin registered!")
    }

    /**
     * Deregister a plugin with CuTAPI. This should be done when a plugin is disabled. A plugin *probably* should
     * not unregister other plugins.
     *
     * @param plugin The plugin being unregistered.
     * @throws IllegalStateException If the plugin is not registered.
     */
    public fun unregisterPlugin(plugin: CuTPlugin) {
        requireRegistered(plugin)
        IdentifierRegistry.unregisterPluginGlobally(plugin)
        periodicManager.cancelAll(plugin)
        plugins.remove(plugin)
    }

    /**
     * Fetch the descriptor used for the [plugin]. This contains some information such as the namespace.
     *
     * @param plugin The plugin whose descriptor is being fetched.
     * @returns The descriptor used for the [plugin].
     * @see PluginDescriptor
     */
    @Throws(IllegalStateException::class)
    public fun getDescriptor(plugin: CuTPlugin): PluginDescriptor {
        requireRegistered(plugin)
        return plugins[plugin]!!
    }

    /**
     * Check if the [plugin] is registered or not.
     *
     * @param plugin The plugin.
     * @returns true if the plugin is registered, false otherwise.
     */
    public fun isRegistered(plugin: CuTPlugin): Boolean {
        return plugin in plugins
    }

    /**
     * Gets a plugin from its namespace. This only tests for exact matches, and is not CaSe-Sensitive,
     * because namespaces must all be lowercase anyway.
     *
     * @throws IllegalStateException If no plugin exists with this namespace.
     */
    public fun getPluginFromNamespace(namespace: String): CuTPlugin {
        return plugins.values.find { it.namespace.equals(namespace, true) }?.plugin
            ?: error("Namespace $namespace not found.")
    }


    /**
     * Throw an error if the [plugin] is not already registered.
     *
     * @param plugin The plugin being checked.
     * @throws IllegalStateException If the plugin isn't registered.
     */
    internal fun requireRegistered(plugin: CuTPlugin) {
        if (plugin !in plugins) error("Plugin $plugin is not registered.")
    }

    /**
     * Throw an error if the [plugin] is already registered.
     *
     * @param plugin The plugin being checked.
     * @throws IllegalStateException If the plugin is registered.
     */
    internal fun requireNotRegistered(plugin: CuTPlugin) {
        if (plugin in plugins) error("Plugin $plugin is already registered.")
    }


    private val namespaceRegex = "[a-z0-9/_+.]+".toRegex()

    /**
     * Validate a namespace string, to ensure that it won't cause problems. See [registerPlugin]
     * for more info about namespace requirements
     *
     * @param namespace The namespace being validated.
     * @see registerPlugin
     * @throws IllegalArgumentException If the namespace is invalid
     * @throws IllegalStateException If the namespace is already in use.
     * */
    internal fun requireValidNamespace(namespace: String) {
        if (namespace.length !in 1..1024) throw IllegalArgumentException("Namespace $namespace is not valid! (not within 1-1024 chars)")

        if (!namespaceRegex.matches(namespace)) {
            throw IllegalArgumentException("Namespace $namespace is not valid! (does not match ${namespaceRegex.pattern})")
        }
        if (namespace.startsWith("_") || namespace.endsWith("_")) {
            throw IllegalArgumentException("Namespace $namespace is not valid! (starts/ends with _)")
        }
        // The plugin that is using this namespace, if there is one.
        val namespaceOwner = plugins.values.find { it.namespace == namespace }

        if (namespaceOwner != null) {
            val pluginName = namespaceOwner.plugin.namespace
            val bukkitPluginName = namespaceOwner.plugin.plugin.name
            error("Namespace $namespace already exists! (used by ${pluginName}/${bukkitPluginName})")
        }
    }


    @OptIn(ExperimentalSerializationApi::class)
    internal val cbor = Cbor {
        this.ignoreUnknownKeys = true
    }

    internal val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    internal val toml = Toml {
        ignoreUnknownKeys = true
    }
}
