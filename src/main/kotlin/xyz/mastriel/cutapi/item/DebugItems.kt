package xyz.mastriel.cutapi.item

import org.bukkit.inventory.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.attachments.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*

@Suppress("UnstableApiUsage")
public object DebugItems : DeferredRegistry<CustomItem<*>> by CustomItem.defer() {

    public val AwesomeStick: CustomItem<*> by registerCustomItem(
        id(Plugin, "debug/awesome_stick"),
        ItemType.STICK,
    ) {

        display {
            name = "Awesome Stick".colored
        }
    }

    public object Extensions : DeferredRegistry<ItemIdentityExtension> by ItemIdentityExtension.defer() {

        public val StickExtension: ItemIdentityExtension by registerItemIdentityExtension(
            id(Plugin, "debug/stick_extension"),
            ItemType.STICK,
        ) {
            attach { Shiny }
        }
    }
}