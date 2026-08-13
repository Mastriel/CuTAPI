package xyz.mastriel.cutapi.item.systems

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.commands.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*

public object ItemDebugSystem : ItemSystem by generalItemSystem(id(Plugin, "item_debug"), RegistryPriority.Low) {

    override fun onRender(context: ItemRenderContext) {
        if (context.viewer?.debugModeEnabled == true)
            context.item.handle.appendLore("&8${context.prerenderStack.identity.logicalId}".colored)
    }
}