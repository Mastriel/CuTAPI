package xyz.mastriel.cutapi.resources.process

import xyz.mastriel.cutapi.resources.*


public fun <T : Resource> ResourceRef<T>.cloneSubId(string: String): ResourceRef<T> {
    return ref(root, "${this.path()}${Locator.CLONE_SEPARATOR}$string.${this.extension}")
}