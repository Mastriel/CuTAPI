package xyz.mastriel.cutapi.resources.data

import org.snakeyaml.engine.v2.api.*
import org.snakeyaml.engine.v2.api.lowlevel.*
import org.snakeyaml.engine.v2.common.*
import org.snakeyaml.engine.v2.exceptions.*
import org.snakeyaml.engine.v2.nodes.*
import org.snakeyaml.engine.v2.schema.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import java.math.*

/** Parser and writer for CuTAPI's schema-backed YAML resource language. */
public object ResourceYaml {
    private const val MAX_DEPTH: Int = 50

    private val loadSettings: LoadSettings = LoadSettings.builder()
        .setLabel("CuTAPI resource metadata")
        .setAllowDuplicateKeys(false)
        .setAllowRecursiveKeys(false)
        .setAllowNonScalarKeys(false)
        .setMaxAliasesForCollections(50)
        .setCodePointLimit(3_000_000)
        .setUseMarks(true)
        .setSchema(CoreSchema())
        .build()

    private val dumpSettings: DumpSettings = DumpSettings.builder()
        .setDefaultFlowStyle(FlowStyle.BLOCK)
        .setDefaultScalarStyle(ScalarStyle.PLAIN)
        .setIndent(2)
        .setIndicatorIndent(2)
        .setIndentWithIndicator(true)
        .setSplitLines(false)
        .setSchema(CoreSchema())
        .build()

    public fun parse(text: String, source: String = "<metadata>"): ResourceDocument {
        try {
            val nodes = Compose(loadSettings).composeAllFromString(text).toList()
            if (nodes.isEmpty()) {
                throw ResourceDocumentException(
                    "The YAML document is empty.",
                    ResourceSourceSpan(source, 1, 1)
                )
            }
            if (nodes.size != 1) {
                throw ResourceDocumentException(
                    "Only one YAML document is allowed per file.",
                    span(nodes[1], source)
                )
            }

            val spans = linkedMapOf<DataPath, ResourceSourceSpan>()
            val root = convert(unwrapAnchor(nodes.single()), source, DataPath(), 0, spans)
            return ResourceDocument(root, source, spans)
        } catch (exception: ResourceDocumentException) {
            throw exception
        } catch (exception: MarkedYamlEngineException) {
            val mark = exception.problemMark.orElse(exception.contextMark.orElse(null))
            val span = mark?.let { ResourceSourceSpan(source, it.line + 1, it.column + 1) }
            throw ResourceDocumentException(exception.problem ?: "Invalid YAML.", span, exception)
        } catch (exception: YamlEngineException) {
            throw ResourceDocumentException(exception.message ?: "Invalid YAML.", null, exception)
        }
    }

    public fun encode(document: ResourceDocument): String = encode(document.root)

    public fun encode(variant: Variant): String {
        val root = toNode(variant)
        val output = StringBuilder()
        Dump(dumpSettings).dumpNode(root, object : StreamDataWriter {
            override fun write(str: String) {
                output.append(str)
            }

            override fun write(str: String, offset: Int, length: Int) {
                output.append(str, offset, offset + length)
            }
        })
        return output.toString()
    }

    private fun convert(
        original: Node,
        source: String,
        path: DataPath,
        depth: Int,
        spans: MutableMap<DataPath, ResourceSourceSpan>,
    ): Variant {
        if (depth > MAX_DEPTH) {
            throw ResourceDocumentException("YAML nesting exceeds $MAX_DEPTH levels.", span(original, source))
        }

        val node = unwrapAnchor(original)
        span(node, source)?.let { spans[path] = it }
        val value = when (node) {
            is MappingNode -> {
                val values = linkedMapOf<String, Variant>()
                for (tuple in node.value) {
                    val keyNode = unwrapAnchor(tuple.keyNode) as? ScalarNode
                        ?: throw ResourceDocumentException("Mapping keys must be strings.", span(tuple.keyNode, source))
                    if (keyNode.tag != Tag.STR) {
                        throw ResourceDocumentException("Mapping keys must be strings.", span(keyNode, source))
                    }
                    if (values.containsKey(keyNode.value)) {
                        throw ResourceDocumentException(
                            "Duplicate mapping key '${keyNode.value}'.",
                            span(keyNode, source),
                        )
                    }
                    values[keyNode.value] = convert(
                        tuple.valueNode,
                        source,
                        path.child(keyNode.value),
                        depth + 1,
                        spans,
                    )
                }
                Variant.Map(values)
            }

            is SequenceNode -> Variant.List(
                node.value.mapIndexed { index, child ->
                    convert(child, source, path.child(index.toString()), depth + 1, spans)
                },
            )

            is ScalarNode -> parseScalar(node, source)
            else -> throw ResourceDocumentException(
                "Unsupported YAML node type ${node.nodeType}.",
                span(node, source),
            )
        }

        val tag = customTag(node.tag, node, source) ?: return value
        val map = value as? Variant.Map
            ?: throw ResourceDocumentException("Schema tags may only be applied to mappings.", span(node, source))
        if (SCHEMA_TYPE_DISCRIMINATOR in map) {
            throw ResourceDocumentException(
                "Tagged mappings cannot also declare '$SCHEMA_TYPE_DISCRIMINATOR'.",
                span(node, source),
            )
        }
        spans[path.child(SCHEMA_TYPE_DISCRIMINATOR)] = span(node, source) ?: return map
        return Variant.Map(
            linkedMapOf(SCHEMA_TYPE_DISCRIMINATOR to Variant.String(tag.toString())) + map.value,
        )
    }

    private fun parseScalar(node: ScalarNode, source: String): Variant = when (node.tag) {
        Tag.NULL -> Variant.Null
        Tag.STR -> Variant.String(node.value)
        Tag.BOOL -> when (node.value.lowercase()) {
            "true" -> Variant.Boolean(true)
            "false" -> Variant.Boolean(false)
            else -> throw ResourceDocumentException("Invalid Boolean '${node.value}'.", span(node, source))
        }
        Tag.INT -> parseInteger(node.value, node, source)
        Tag.FLOAT -> node.value.replace("_", "").toDoubleOrNull()
            ?.takeIf { it.isFinite() }
            ?.let(Variant::Double)
            ?: throw ResourceDocumentException("Invalid finite number '${node.value}'.", span(node, source))
        else -> throw ResourceDocumentException("Unsupported scalar tag '${node.tag.value}'.", span(node, source))
    }

    private fun parseInteger(text: String, node: Node, source: String): Variant {
        val normalized = text.replace("_", "")
        val negative = normalized.startsWith('-')
        val unsigned = normalized.removePrefix("+").removePrefix("-")
        val (radix, digits) = when {
            unsigned.startsWith("0x", true) -> 16 to unsigned.drop(2)
            unsigned.startsWith("0o", true) -> 8 to unsigned.drop(2)
            else -> 10 to unsigned
        }
        return try {
            val value = BigInteger(digits, radix).let { if (negative) it.negate() else it }.longValueExact()
            if (value in Int.MIN_VALUE..Int.MAX_VALUE) Variant.Int(value.toInt()) else Variant.Long(value)
        } catch (exception: Exception) {
            throw ResourceDocumentException(
                "Integer '$text' is outside the supported range.",
                span(node, source),
                exception,
            )
        }
    }

    private fun customTag(tag: Tag, node: Node, source: String): Identifier? {
        if (tag in STANDARD_TAGS) return null
        return try {
            id(tag.value)
        } catch (exception: Exception) {
            throw ResourceDocumentException(
                "Custom YAML tag '${tag.value}' must be a CuTAPI namespace:type identifier.",
                span(node, source),
                exception,
            )
        }
    }

    private fun toNode(value: Variant): Node = when (value) {
        Variant.Null -> ScalarNode(Tag.NULL, "null", ScalarStyle.PLAIN)
        is Variant.Boolean -> ScalarNode(Tag.BOOL, value.value.toString(), ScalarStyle.PLAIN)
        is Variant.Byte -> ScalarNode(Tag.INT, value.value.toString(), ScalarStyle.PLAIN)
        is Variant.Short -> ScalarNode(Tag.INT, value.value.toString(), ScalarStyle.PLAIN)
        is Variant.Int -> ScalarNode(Tag.INT, value.value.toString(), ScalarStyle.PLAIN)
        is Variant.Long -> ScalarNode(Tag.INT, value.value.toString(), ScalarStyle.PLAIN)
        is Variant.Float -> finiteFloatNode(value.value.toDouble())
        is Variant.Double -> finiteFloatNode(value.value)
        is Variant.String -> ScalarNode(Tag.STR, value.value, ScalarStyle.PLAIN)
        is Variant.Char -> ScalarNode(Tag.STR, value.value.toString(), ScalarStyle.PLAIN)
        is Variant.Identifier -> ScalarNode(Tag.STR, value.value.toString(), ScalarStyle.PLAIN)
        is Variant.ResourceRef -> ScalarNode(Tag.STR, value.value.toString(), ScalarStyle.PLAIN)
        is Variant.List -> SequenceNode(Tag.SEQ, value.value.map(::toNode), FlowStyle.BLOCK)
        is Variant.Map -> {
            val type = value[SCHEMA_TYPE_DISCRIMINATOR]
            val typeId = (type as? Variant.String)?.value
            if (type != null && typeId == null) {
                throw ResourceDocumentException("'$SCHEMA_TYPE_DISCRIMINATOR' must be a string identifier.")
            }
            MappingNode(
                typeId?.let(::Tag) ?: Tag.MAP,
                value.value
                    .filterKeys { it != SCHEMA_TYPE_DISCRIMINATOR }
                    .map { (key, child) ->
                        NodeTuple(ScalarNode(Tag.STR, key, ScalarStyle.PLAIN), toNode(child))
                    },
                FlowStyle.BLOCK,
            )
        }
    }

    private fun finiteFloatNode(value: Double): ScalarNode {
        require(value.isFinite()) { "Resource configuration numbers must be finite." }
        return ScalarNode(Tag.FLOAT, value.toString(), ScalarStyle.PLAIN)
    }

    private fun unwrapAnchor(node: Node): Node = if (node is AnchorNode) unwrapAnchor(node.realNode) else node

    private fun span(node: Node, source: String): ResourceSourceSpan? {
        val start = node.startMark.orElse(null) ?: return null
        val end = node.endMark.orElse(start)
        return ResourceSourceSpan(source, start.line + 1, start.column + 1, end.line + 1, end.column + 1)
    }

    private val STANDARD_TAGS: Set<Tag> = setOf(
        Tag.MAP,
        Tag.SEQ,
        Tag.STR,
        Tag.NULL,
        Tag.BOOL,
        Tag.INT,
        Tag.FLOAT,
    )
}
