package xyz.mastriel.cutapi.resources

class ResourceException(ref: ResourceRef<*>, message: String?) :
    Exception("[$ref] ${message ?: "Error, no further information"}")


fun resError(ref: ResourceRef<*>, message: String?) : Nothing = throw ResourceException(ref, message)

fun resError(res: Resource, message: String?) : Nothing = throw ResourceException(res.ref, message)