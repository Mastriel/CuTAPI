package xyz.mastriel.cutapi.gui

import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.builtin.*

public open class CuTGUI(
    private val bgTexture: ResourceRef<Texture2D>,

    ) {

}


public open class PaginatedGUI {


}


public fun <T : CuTGUI> gui(base: T, block: T.() -> Unit): T {
    return base.apply(block)
}