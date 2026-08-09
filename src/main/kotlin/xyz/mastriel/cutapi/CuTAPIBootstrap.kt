@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import xyz.mastriel.cutapi.item.nativeitem.NativeItemCodecInstrumentation

public class CuTAPIBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        NativeItemCodecInstrumentation.install()
    }
}
