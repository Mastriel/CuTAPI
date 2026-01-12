package xyz.mastriel.cutapi.pdc.tags

import kotlinx.serialization.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*

/**
 * A reference to a tag of type T.
 */
public interface TagRef<T : Any> {
    public val id: Identifier

    public val converter: TagConverter<*, T>
}

private class TagRefImpl<T : Any>(
    override val id: Identifier,
    override val converter: TagConverter<*, T>
) : TagRef<T> {

}

public fun <T : Any, C : TagConverter<*, T>> tagRef(id: Identifier, type: C): TagRef<T> {
    return TagRefImpl(id, type)
}

public fun <T : Any, C : TagConverter<*, T>> tagRef(namespace: CuTPlugin, name: String, type: C): TagRef<T> {
    return tagRef(id(namespace, name), type)
}

public inline fun <reified T : Any> objectTagRef(
    namespace: CuTPlugin,
    name: String,
    serializer: KSerializer<T>
): TagRef<T> {
    return tagRef(id(namespace, name), ObjectTagConverter(T::class, serializer))
}

public fun <T : Any> TagRef<T>.toIdentifier(): Identifier {
    return this.id
}

public fun <T : Any> TagContainer.get(tagRef: TagRef<T>): T? {
    return this.get(tagRef.id, tagRef.converter)
}

public fun <T : Any> TagContainer.set(tagRef: TagRef<T>, value: T?) {
    this.set(tagRef.id, value, tagRef.converter)
}