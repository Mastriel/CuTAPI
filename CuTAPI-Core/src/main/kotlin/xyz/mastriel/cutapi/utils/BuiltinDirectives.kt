package xyz.mastriel.cutapi.utils

import net.peanuuutz.tomlkt.*


@DirectiveName("no-merge")
class NoMerge : SerialDirective() {

    fun isValid() : Boolean {
        val literal = content as TomlLiteral? ?: return false
        return literal.toBooleanOrNull() == true
    }
}



internal fun registerBuiltinDirectives() {
    SerialDirective.register<NoMerge>(::NoMerge)
}