package xyz.mastriel.cutapi.item.systems

import net.kyori.adventure.text.format.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.attachment.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*

/**
 * Tags all items with an origin data source.
 */
public object ItemOriginSystem :
    ItemSystem by generalItemSystem(id(Plugin, "item_origin"), priority = RegistryPriority(1100)) {

    private val enabled: Boolean by cutConfigValue("item-origin-formatter.enabled") { true }
    private val default: String by cutConfigValue("item-origin-formatter.default") { "<blue>{}" }


    override fun onRender(context: ItemRenderContext) {
        if (enabled) {
            if (context.prerenderStack.hasAttachment<ItemOriginImmune>()) return;
            val prerenderHandle = context.prerenderStack.handle

            val id = prerenderHandle.itemIdentity.logicalId

            val displayName = id.plugin?.displayName ?: id.namespace
            val configObject = cutConfigValue<String?>("item-origin-formatter.${id.namespace}") { null }

            val formatter = configObject.get() ?: default

            context.item.handle.appendLore(
                *formatter
                    .replace("{}", displayName)
                    .replace("{small}", displayName.toSmallCaps())
                    .split("<br>")
                    .map {
                        it.miniMessage
                            .decoration(TextDecoration.ITALIC, false)
                    }
                    .toTypedArray()
            )
        }

    }
}

/**
 * Prevents item origins from showing up on items with this attachment. Useful for UI.
 */
public object ItemOriginImmune : ItemAttachment,
    Schema<ItemOriginImmune> by singletonSchema(id(Plugin, "item_origin_immune")) {}

private const val UnicodeSmallCaps = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘꞯʀꜱᴛᴜᴠᴡxʏᴢ"

internal fun String.toSmallCaps(): String = buildString(length) {
    for (character in this@toSmallCaps) {
        val alphabetIndex = when (character) {
            in 'a'..'z' -> character - 'a'
            in 'A'..'Z' -> character - 'A'
            else -> -1
        }
        if (alphabetIndex >= 0) {
            append(UnicodeSmallCaps[alphabetIndex])
        } else {
            append(character)
        }
    }
}
