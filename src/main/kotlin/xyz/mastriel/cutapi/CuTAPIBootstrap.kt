@file:Suppress("UnstableApiUsage")

package xyz.mastriel.cutapi

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import xyz.mastriel.cutapi.block.CustomBlock
import xyz.mastriel.cutapi.block.CustomTileEntity
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockBootstrap
import xyz.mastriel.cutapi.item.nativeitem.NativeItemCodecInstrumentation

public class CuTAPIBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        NativeItemCodecInstrumentation.install()
        NativeBlockBootstrap.submit(CustomBlock.Unknown)
        NativeBlockBootstrap.submit(CustomTileEntity.Unknown)
    }
}
