package xyz.mastriel.cutapi.registry.descriptors


/**
 * A file in the `resources` of a plugin, which can contain useful info about anything needed.
 */
abstract class ResourceDescriptorFile(val path: String) {


    fun read() {
    }
}

