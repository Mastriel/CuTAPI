package xyz.mastriel.cutapi

import org.bukkit.plugin.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.utils.*
import java.io.*


public interface CuTPlugin : ResourceRoot {
    // Either is the bukkit plugin that owns this plugin,
    // or its CuTAPI.
    public val plugin: Plugin get() = this as? Plugin ?: Plugin
    public override val cutPlugin: CuTPlugin get() = this
    public val descriptor: PluginDescriptor get() = CuTAPI.getDescriptor(this)
    public val displayName: String get() = descriptor.options.displayName
    public override val namespace: String get() = descriptor.namespace
    public val isFromJar: Boolean get() = descriptor.options.isFromJar

    override fun getResourcesFolder(): File {
        return CuTAPI.resourcePackManager.tempFolder.appendPath(namespace)
    }
}

/**
 * A data class holding useful information about a plugin, pertaining to CuTAPI.
 *
 * @see Plugin
 * @see CuTAPI
 * */
@ConsistentCopyVisibility
public data class PluginDescriptor internal constructor(
    val plugin: CuTPlugin,
    val namespace: String,
    val options: PluginOptions = defaultPluginOptions(plugin)
)

@ConsistentCopyVisibility
public data class PluginOptions internal constructor(
    val packFolder: String,
    val strictResourceLoading: Boolean,
    val isFromJar: Boolean,
    val displayName: String,
)

public class PluginOptionsBuilder(private val cutPlugin: CuTPlugin) {
    /**
     * The folder in `src/main/resources/` that will be used to generate a resource pack.
     *
     * `pack` by default.
     */
    public var packFolder: String = "pack"

    /**
     * When true, if a resource is attempted to be loaded from a file and that operation fails, then your plugin
     * will be disabled. Otherwise, an error will be logged and everything will continue as normal (minus that resource,
     * of course).
     *
     * `false` by default
     */
    public var strictResourceLoading: Boolean = false

    /**
     * When true, this indicates that this plugin comes from a jar file,
     * and all of its resources are in a jar file.
     */
    public var isFromJar: Boolean = true

    /**
     * The display name of your CuTPlugin. By default, this is either:
     * 1. Your plugin name, if your CuTPlugin also implements Bukkit's Plugin interface
     * 2. Your namespace
     */
    public var displayName: String = (cutPlugin as? Plugin)?.name ?: cutPlugin.namespace

    public fun build(): PluginOptions {
        return PluginOptions(packFolder, strictResourceLoading, isFromJar, displayName)
    }
}

public fun defaultPluginOptions(plugin: CuTPlugin): PluginOptions = PluginOptionsBuilder(plugin).build()
