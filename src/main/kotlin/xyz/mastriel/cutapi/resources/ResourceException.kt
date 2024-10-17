package xyz.mastriel.cutapi.resources

public class ResourceException(ref: ResourceRef<*>, message: String?, cause: Throwable? = null) :
    Exception("[$ref] ${message ?: "Error, no further information"}", cause)


public fun resError(ref: ResourceRef<*>, message: String?, cause: Throwable? = null): Nothing =
    throw ResourceException(ref, message, cause)

public fun resError(res: Resource, message: String?, cause: Throwable? = null): Nothing =
    throw ResourceException(res.ref, message, cause)