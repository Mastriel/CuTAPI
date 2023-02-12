package xyz.mastriel.cutapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import org.bukkit.plugin.Plugin
import xyz.mastriel.cutapi.packets.PacketManager
import xyz.mastriel.cutapi.periodic.PeriodicManager
import xyz.mastriel.cutapi.resourcepack.management.ResourceManager
import xyz.mastriel.cutapi.resourcepack.management.ResourcePackManager
import xyz.mastriel.cutapi.utils.ServiceManager


/**
 * The general manager for all the API. Plugins should be registered here, so they can be properly managed.
 *
 * @see Plugin
 * @see PluginDescriptor
 */
object CuTAPI {

    /**
     * A map of all plugins registered, along with their plugin descriptors.
     */
    private val plugins = mutableMapOf<Plugin, PluginDescriptor>()
    val registedPlugins : Set<Plugin> get() = plugins.keys

    internal val packetManager = PacketManager(Plugin)
    internal val packetEventManager = packetManager.eventManager

    val resourcePackManager = ResourcePackManager()
    val resourceManager = ResourceManager()
    val packGenerator = resourcePackManager.generator
    val periodicManager = PeriodicManager()
    val serviceManager = ServiceManager()

    /**
     * Register a plugin with CuTAPI. This is used to namespace any items registered with the API, and
     * is used to hold some other additional information about the plugin.
     *
     * @param plugin The plugin being registered.
     * @param namespace The namespace being used for the plugin. By default, this is just the plugin's name
     * in all lowercase. A namespace must follow Regex `[a-z0-9_]+`, must be between 3 and 64 characters, and must
     * not start or end with an underscore (_). Also, namespaces must be unique between plugins, and cannot be shared.
     * @param options The additional (optional) options that this plugin can have to alter behavior.
     * @throws IllegalArgumentException If the namespace is invalid.
     * @throws IllegalStateException If the namespace is already in use.
     * @throws IllegalStateException If the plugin is already registered.
     */
    fun registerPlugin(plugin: Plugin, namespace: String=plugin.name.lowercase(), options: (PluginOptionsBuilder.() -> Unit)? = null) {
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
    fun unregisterPlugin(plugin: Plugin) {
        requireRegistered(plugin)
        plugins.remove(plugin)
    }

    /**
     * Fetch the descriptor used for the [plugin]. This contains some information such as the namespace.
     *
     * @param plugin The plugin whose descriptor is being fetched.
     * @returns The descriptor used for the [plugin].
     * @see PluginDescriptor
     */
    fun getDescriptor(plugin: Plugin) : PluginDescriptor {
        requireRegistered(plugin)
        return plugins[plugin] ?: error("Plugin $plugin not registered when it should be?")
    }

    /**
     * Check if the [plugin] is registered or not.
     *
     * @param plugin The plugin.
     * @returns true if the plugin is registered, false otherwise.
     */
    fun isRegistered(plugin: Plugin) : Boolean {
        if (plugin in plugins) return true
        return false
    }

    /**
     * Gets a plugin from its namespace. This only tests for exact matches, and is not CaSe-Sensitive,
     * because namespaces must all be lowercase anyway.
     *
     * @throws IllegalStateException If no plugin exists with this namespace.
     */
    fun getPluginFromNamespace(namespace: String) : Plugin {
        return plugins.values.find { it.namespace.equals(namespace, true) }?.plugin
            ?: error("Namespace $namespace not found.")
    }


    /**
     * Throw an error if the [plugin] is not already registered.
     *
     * @param plugin The plugin being checked.
     * @throws IllegalStateException If the plugin isn't registered.
     */
    internal fun requireRegistered(plugin: Plugin) {
        if (plugin !in plugins) error("Plugin ${plugin.name} is not registered.")
    }

    /**
     * Throw an error if the [plugin] is already registered.
     *
     * @param plugin The plugin being checked.
     * @throws IllegalStateException If the plugin is registered.
     */
    internal fun requireNotRegistered(plugin: Plugin) {
        if (plugin in plugins) error("Plugin ${plugin.name} is already registered.")
    }


    private val namespaceRegex = "[a-zA-Z0-9/_]+".toRegex()
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
        if (namespace.length !in 1..64) throw IllegalArgumentException("Namespace $namespace is not valid! (not within 1-64 chars)")

        if (!namespaceRegex.matches(namespace)) {
            throw IllegalArgumentException("Namespace $namespace is not valid! (does not match ${namespaceRegex.pattern})")
        }
        // The plugin that is using this namespace, if there is one.
        val namespaceOwner = plugins.values.find { it.namespace == namespace }

        if (namespaceOwner != null) {
            val pluginName = namespaceOwner.plugin.name
            error("Namespace $namespace already exists! (used by ${pluginName})")
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
}
