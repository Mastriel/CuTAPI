package xyz.mastriel.cutapi.resources.data

import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*

/** A location in a resource document. Lines and columns are one-based. */
public data class ResourceSourceSpan(
    public val source: String,
    public val startLine: Int,
    public val startColumn: Int,
    public val endLine: Int = startLine,
    public val endColumn: Int = startColumn,
) {
    override fun toString(): String = "$source:$startLine:$startColumn"
}

/** A schema-independent resource document backed by the canonical [Variant] tree. */
@ConsistentCopyVisibility
public data class ResourceDocument internal constructor(
    public val root: Variant,
    public val source: String,
    internal val sourceSpans: Map<DataPath, ResourceSourceSpan>,
) {
    public constructor(root: Variant, source: String = "<generated>") : this(root, source, emptyMap())

    public fun requireMap(): Variant.Map = root as? Variant.Map
        ?: throw ResourceDocumentException("The document root must be a mapping.", span())

    public fun typeId(): Identifier? {
        val value = (root as? Variant.Map)?.get(SCHEMA_TYPE_DISCRIMINATOR) ?: return null
        val serialized = (value as? Variant.String)?.value
            ?: throw ResourceDocumentException(
                "The document type must be a string identifier.",
                span(DataPath(listOf(SCHEMA_TYPE_DISCRIMINATOR))),
            )
        return try {
            id(serialized)
        } catch (exception: Exception) {
            throw ResourceDocumentException(
                "Invalid document type '$serialized'.",
                span(DataPath(listOf(SCHEMA_TYPE_DISCRIMINATOR))),
                exception,
            )
        }
    }

    public fun requireTypeId(): Identifier = typeId()
        ?: throw ResourceDocumentException("The document must have a resource type tag.", span())

    public fun span(path: DataPath = DataPath()): ResourceSourceSpan? =
        sourceSpans[path] ?: generateSequence(path) { current ->
            if (current.segments.isEmpty()) null else DataPath(current.segments.dropLast(1))
        }.mapNotNull(sourceSpans::get).firstOrNull()

    public fun <T> decode(serializer: Serializer<T>): T {
        val normalized = try {
            root.normalize(serializer.descriptor)
        } catch (exception: VariantNormalizationException) {
            throw ResourceDocumentException(
                exception.message ?: "Resource document does not match ${serializer.descriptor.id}.",
                span(exception.path),
                exception,
            )
        }

        return when (val result = serializer.deserialize(normalized)) {
            is DeserializeResult.Success -> result.value
            is DeserializeResult.Failure -> throw ResourceDocumentException(
                result.error.message ?: "Could not deserialize ${serializer.descriptor.id}.",
                span(pathFrom(result.error)),
                result.error,
            )
        }
    }

    public fun withRoot(value: Variant): ResourceDocument = copy(root = value)

    public fun without(vararg keys: String): ResourceDocument {
        val map = requireMap()
        val removed = keys.toSet()
        return copy(
            root = map.without(*keys),
            sourceSpans = sourceSpans.filterKeys { path ->
                path.segments.firstOrNull() !in removed
            },
        )
    }

    public fun subDocument(path: DataPath): ResourceDocument {
        val value = valueAt(path)
        val prefixSize = path.segments.size
        val spans = sourceSpans.mapNotNull { (candidate, span) ->
            if (!candidate.startsWith(path)) return@mapNotNull null
            DataPath(candidate.segments.drop(prefixSize)) to span
        }.toMap()
        return ResourceDocument(value, source, spans)
    }

    public fun merge(
        overlay: ResourceDocument,
        combineLists: Boolean = true,
    ): ResourceDocument {
        val merged = mergeAt(
            base = root,
            overlay = overlay.root,
            baseDocument = this,
            overlayDocument = overlay,
            basePath = DataPath(),
            overlayPath = DataPath(),
            resultPath = DataPath(),
            combineLists = combineLists,
        )
        return ResourceDocument(merged.value, overlay.source, merged.spans)
    }

    private fun valueAt(path: DataPath): Variant {
        var current = root
        for (segment in path.segments) {
            current = when (current) {
                is Variant.Map -> current[segment]
                    ?: throw ResourceDocumentException("No value exists at $path.", span(path))
                is Variant.List -> current.getOrNull(segment.toIntOrNull() ?: -1)
                    ?: throw ResourceDocumentException("No value exists at $path.", span(path))
                else -> throw ResourceDocumentException("No value exists at $path.", span(path))
            }
        }
        return current
    }
}

private data class MergeResult(
    val value: Variant,
    val spans: Map<DataPath, ResourceSourceSpan>,
)

private fun mergeAt(
    base: Variant,
    overlay: Variant,
    baseDocument: ResourceDocument,
    overlayDocument: ResourceDocument,
    basePath: DataPath,
    overlayPath: DataPath,
    resultPath: DataPath,
    combineLists: Boolean,
): MergeResult {
    if (base is Variant.Map && overlay is Variant.Map) {
        val values = linkedMapOf<String, Variant>()
        val spans = linkedMapOf<DataPath, ResourceSourceSpan>()
        (overlayDocument.span(overlayPath) ?: baseDocument.span(basePath))?.let { spans[resultPath] = it }
        for (key in base.keys + overlay.keys) {
            val baseValue = base[key]
            val overlayValue = overlay[key]
            val childResultPath = resultPath.child(key)
            when {
                baseValue != null && overlayValue != null -> {
                    val merged = mergeAt(
                        baseValue,
                        overlayValue,
                        baseDocument,
                        overlayDocument,
                        basePath.child(key),
                        overlayPath.child(key),
                        childResultPath,
                        combineLists,
                    )
                    values[key] = merged.value
                    spans += merged.spans
                }
                overlayValue != null -> {
                    values[key] = overlayValue
                    spans += overlayDocument.copySpans(overlayPath.child(key), childResultPath)
                }
                baseValue != null -> {
                    values[key] = baseValue
                    spans += baseDocument.copySpans(basePath.child(key), childResultPath)
                }
            }
        }
        return MergeResult(Variant.Map(values), spans)
    }

    if (combineLists && base is Variant.List && overlay is Variant.List) {
        val spans = linkedMapOf<DataPath, ResourceSourceSpan>()
        (overlayDocument.span(overlayPath) ?: baseDocument.span(basePath))?.let { spans[resultPath] = it }
        base.value.indices.forEach { index ->
            spans += baseDocument.copySpans(basePath.child(index.toString()), resultPath.child(index.toString()))
        }
        overlay.value.indices.forEach { index ->
            spans += overlayDocument.copySpans(
                overlayPath.child(index.toString()),
                resultPath.child((base.size + index).toString()),
            )
        }
        return MergeResult(Variant.List(base.value + overlay.value), spans)
    }

    return MergeResult(overlay, overlayDocument.copySpans(overlayPath, resultPath))
}

private fun ResourceDocument.copySpans(
    sourcePrefix: DataPath,
    targetPrefix: DataPath,
): Map<DataPath, ResourceSourceSpan> = sourceSpans.mapNotNull { (path, span) ->
    if (!path.startsWith(sourcePrefix)) return@mapNotNull null
    val suffix = path.segments.drop(sourcePrefix.segments.size)
    DataPath(targetPrefix.segments + suffix) to span
}.toMap()

private fun DataPath.startsWith(prefix: DataPath): Boolean =
    segments.size >= prefix.segments.size && segments.take(prefix.segments.size) == prefix.segments

private fun pathFrom(exception: Throwable): DataPath =
    exception.serializedDataPath() ?: DataPath()

public class ResourceDocumentException(
    message: String,
    public val sourceSpan: ResourceSourceSpan? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(
    if (sourceSpan == null) message else "$message ($sourceSpan)",
    cause,
)
