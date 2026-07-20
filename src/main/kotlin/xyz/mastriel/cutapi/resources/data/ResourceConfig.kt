package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.registry.*

/** A location in a resource configuration file. Lines and columns are one-based. */
public data class ResourceSourceSpan(
    public val source: String,
    public val startLine: Int,
    public val startColumn: Int,
    public val endLine: Int = startLine,
    public val endColumn: Int = startColumn
) {
    override fun toString(): String = "$source:$startLine:$startColumn"
}

/** A value in CuTAPI's format-neutral resource configuration tree. */
public sealed interface ResourceConfigValue {
    public val span: ResourceSourceSpan?
}

public data class ResourceConfigScalar(
    public val value: Any?,
    override val span: ResourceSourceSpan? = null
) : ResourceConfigValue

public data class ResourceConfigList(
    public val values: List<ResourceConfigValue> = emptyList(),
    override val span: ResourceSourceSpan? = null
) : ResourceConfigValue, List<ResourceConfigValue> by values

public data class ResourceConfigMap(
    public val values: Map<String, ResourceConfigValue> = emptyMap(),
    override val span: ResourceSourceSpan? = null
) : ResourceConfigValue {
    public val keys: Set<String> get() = values.keys
    public val size: Int get() = values.size

    public operator fun get(key: String): ResourceConfigValue? = values[key]
    public operator fun iterator(): Iterator<Map.Entry<String, ResourceConfigValue>> = values.iterator()
    public fun getValue(key: String): ResourceConfigValue = values.getValue(key)
    public fun toMap(): Map<String, ResourceConfigValue> = values.toMap()
    public fun <R> mapValues(transform: (Map.Entry<String, ResourceConfigValue>) -> R): Map<String, R> =
        values.mapValues(transform)

    public fun without(vararg keys: String): ResourceConfigMap =
        copy(values = values.filterKeys { it !in keys })
}

public data class TaggedResourceConfig(
    public val id: Identifier,
    public val value: ResourceConfigValue,
    override val span: ResourceSourceSpan? = value.span
) : ResourceConfigValue

public data class ResourceConfigDocument(
    public val tag: Identifier?,
    public val root: ResourceConfigValue,
    public val source: String
) {
    public fun requireMap(): ResourceConfigMap = root as? ResourceConfigMap
        ?: throw ResourceConfigException("The document root must be a mapping.", root.span)

    public fun requireTag(): Identifier = tag
        ?: throw ResourceConfigException("The document must have a resource type tag.", root.span)
}

public class ResourceConfigException(
    message: String,
    public val sourceSpan: ResourceSourceSpan? = null,
    cause: Throwable? = null
) : IllegalArgumentException(
    if (sourceSpan == null) message else "$message (${sourceSpan})",
    cause
)

/**
 * Recursively merges two configuration values. Mapping keys from [overlay] win.
 * Lists are appended only when [combineLists] is true; otherwise the overlay replaces them.
 */
public fun ResourceConfigValue.combine(
    overlay: ResourceConfigValue,
    combineLists: Boolean = true
): ResourceConfigValue {
    if (this is ResourceConfigMap && overlay is ResourceConfigMap) {
        val combined = LinkedHashMap(values)
        for ((key, value) in overlay.values) {
            combined[key] = combined[key]?.combine(value, combineLists) ?: value
        }
        return ResourceConfigMap(combined, overlay.span ?: span)
    }

    if (combineLists && this is ResourceConfigList && overlay is ResourceConfigList) {
        return ResourceConfigList(values + overlay.values, overlay.span ?: span)
    }

    return overlay
}

public fun ResourceConfigValue.asConfigMap(): ResourceConfigMap = this as? ResourceConfigMap
    ?: throw ResourceConfigException("Expected a mapping.", span)

public fun ResourceConfigValue.asConfigList(): ResourceConfigList = this as? ResourceConfigList
    ?: throw ResourceConfigException("Expected a list.", span)

public fun ResourceConfigValue.asConfigScalar(): ResourceConfigScalar = this as? ResourceConfigScalar
    ?: throw ResourceConfigException("Expected a scalar value.", span)
