package xyz.mastriel.cutapi

import org.bukkit.plugin.Plugin


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

    /**
     * Register a plugin with CuTAPI. This is used to namespace any items registered with the API, and
     * is used to hold some other additional information about the plugin.
     *
     * @param plugin The plugin being registered.
     * @param namespace The namespace being used for the plugin. By default, this is just the plugin's name
     * in all lowercase. A namespace must follow Regex `[a-z0-9_]+`, must be between 3 and 64 characters, and must
     * not start or end with an underscore (_). Also, namespaces must be unique between plugins, and cannot be shared.
     * @throws IllegalArgumentException If the namespace is invalid.
     * @throws IllegalStateException If the namespace is already in use.
     * @throws IllegalStateException If the plugin is already registered.
     */
    fun registerPlugin(plugin: Plugin, namespace: String=plugin.name.lowercase()) {
        requireNotRegistered(plugin)
        requireValidNamespace(namespace)

        val descriptor = PluginDescriptor(plugin, namespace)
        plugins[plugin] = descriptor

        Plugin.info("Plugin $plugin registered!")
    }

    /**
     * Deregister a plugin with CuTAPI. This should be done when a plugin is disabled. A plugin *probably* should
     * not unregister other plugins, although there are no automatic safeguards to prevent this.
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
     * Gets a plugin from its namespace. This only tests for exact matches, and is CaSe-Sensitive.
     *
     * @throws IllegalStateException If no plugin exists with this namespace.
     */
    fun getPluginFromNamespace(namespace: String) : Plugin {
        return plugins.values.find { it.namespace == namespace }?.plugin
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


    private val namespaceRegex = "[a-z0-9_]+".toRegex()
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
        if (namespace.length !in 3..64) throw IllegalArgumentException("Namespace $namespace is not valid! (not within 3-64 chars)")

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

}


/**
 * A data class holding useful information about a plugin, pertaining to CuTAPI.
 *
 * @see Plugin
 * @see CuTAPI
 * */
data class PluginDescriptor internal constructor(
    val plugin: Plugin,
    val namespace: String
) {

    /**
     * A very human-readable way of displaying the descriptor.
     */
    override fun toString(): String {
        return """
            Plugin ${plugin.name} descriptor:
              namespace: $namespace
        """.trimIndent()
    }
}