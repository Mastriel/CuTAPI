package xyz.mastriel.cutapi.item.systems

import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.item.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.utils.*

/**
 * Tags all items with an origin data source.
 */
public object ItemOriginSystem : ItemSystem by generalItemSystem(id(Plugin, "item_origin")) {

    private val itemOriginFormatter: String by cutConfigValue("item-origin-formatter") { "&9{}" }

    override fun onRender(context: ItemRenderContext) {
        if (itemOriginFormatter != $$"$DISABLE") {
            val prerenderHandle = context.prerenderStack.handle

            val id = prerenderHandle.itemIdentity.logicalId

            val displayName = id.plugin?.displayName ?: id.namespace

            context.item.handle.appendLore(
                itemOriginFormatter
                    .replace("{}", displayName)
                    .replace("{small}", displayName.toSmallCaps())
                    .colored
            )
        }

    }
}

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
