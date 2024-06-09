package xyz.mastriel.cutapi.resources

public class ResourceException(ref: ResourceRef<*>, message: String?) :
    Exception("[$ref] ${message ?: "Error, no further information"}")


public fun resError(ref: ResourceRef<*>, message: String?) : Nothing = throw ResourceException(ref, message)

public fun resError(res: Resource, message: String?) : Nothing = throw ResourceException(res.ref, message)