package xyz.mastriel.cutapi.data

/** Uses a tagged schema as an untagged nested object while retaining its full descriptor. */
public fun <T : Any> Schema<T>.embedded(): Serializer<T> {
    val objectDescriptor = descriptor.shape as? SerializerShape.Object
        ?: error("Only object schemas can be embedded.")
    val embeddedDescriptor = SerializerDescriptor.`object`(
        id = id.appendSubId("embedded"),
        type = type,
        tagged = false,
        strict = strict,
        properties = objectDescriptor.properties
    )
    return serializer(
        descriptor = embeddedDescriptor,
        serialize = { value ->
            val root = serialize(value).getOrThrow() as? Variant.Map
                ?: error("Schema $id did not serialize to a map.")
            root.without(SCHEMA_TYPE_DISCRIMINATOR)
        },
        deserialize = { variant ->
            val root = variant.requireMap()
            deserialize(
                Variant.Map(root.value + (SCHEMA_TYPE_DISCRIMINATOR to Variant.String(id.toString())))
            ).getOrThrow()
        }
    )
}

/** Schema-aware object merge used by metadata overlays. */
public fun <T : Any> Schema<T>.merge(base: T, overlay: T): T {
    val baseVariant = serialize(base).getOrThrow()
    val overlayVariant = serialize(overlay).getOrThrow()
    val combined = baseVariant.merge(overlayVariant, combineLists = true).normalize(descriptor)
    return deserialize(combined).getOrThrow()
}
