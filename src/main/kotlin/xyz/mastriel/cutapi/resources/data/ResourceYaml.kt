package xyz.mastriel.cutapi.resources.data

import org.snakeyaml.engine.v2.api.*
import org.snakeyaml.engine.v2.api.lowlevel.*
import org.snakeyaml.engine.v2.common.*
import org.snakeyaml.engine.v2.exceptions.*
import org.snakeyaml.engine.v2.nodes.*
import org.snakeyaml.engine.v2.schema.*
import xyz.mastriel.cutapi.registry.*
import java.math.*

/** Parser and writer for CuTAPI's constrained YAML resource language. */
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

    public fun parse(text: String, source: String = "<metadata>"): ResourceConfigDocument {
        try {
            val nodes = Compose(loadSettings).composeAllFromString(text).toList()
            if (nodes.isEmpty()) throw ResourceConfigException("The YAML document is empty.")
            if (nodes.size != 1) throw ResourceConfigException("Only one YAML document is allowed per file.")

            val root = unwrapAnchor(nodes.single())
            val documentTag = customTag(root.tag, root, source)
            return ResourceConfigDocument(
                documentTag,
                convert(root, source, 0, ignoreTag = documentTag != null),
                source
            )
        } catch (exception: ResourceConfigException) {
            throw exception
        } catch (exception: MarkedYamlEngineException) {
            val mark = exception.problemMark.orElse(exception.contextMark.orElse(null))
            val span = mark?.let { ResourceSourceSpan(source, it.line + 1, it.column + 1) }
            throw ResourceConfigException(exception.problem ?: "Invalid YAML.", span, exception)
        } catch (exception: YamlEngineException) {
            throw ResourceConfigException(exception.message ?: "Invalid YAML.", null, exception)
        }
    }

    public fun encode(document: ResourceConfigDocument): String {
        val root = toNode(document.root)
        if (document.tag != null) root.tag = Tag(document.tag.toString())

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
        depth: Int,
        ignoreTag: Boolean = false
    ): ResourceConfigValue {
        if (depth > MAX_DEPTH) {
            throw ResourceConfigException("YAML nesting exceeds $MAX_DEPTH levels.", span(original, source))
        }

        val node = unwrapAnchor(original)
        val value = when (node) {
            is MappingNode -> {
                val values = linkedMapOf<String, ResourceConfigValue>()
                for (tuple in node.value) {
                    val keyNode = unwrapAnchor(tuple.keyNode) as? ScalarNode
                        ?: throw ResourceConfigException("Mapping keys must be strings.", span(tuple.keyNode, source))
                    if (keyNode.tag != Tag.STR) {
                        throw ResourceConfigException("Mapping keys must be strings.", span(keyNode, source))
                    }
                    if (values.containsKey(keyNode.value)) {
                        throw ResourceConfigException("Duplicate mapping key '${keyNode.value}'.", span(keyNode, source))
                    }
                    values[keyNode.value] = convert(tuple.valueNode, source, depth + 1)
                }
                ResourceConfigMap(values, span(node, source))
            }

            is SequenceNode -> ResourceConfigList(
                node.value.map { convert(it, source, depth + 1) },
                span(node, source)
            )

            is ScalarNode -> ResourceConfigScalar(parseScalar(node, source), span(node, source))
            else -> throw ResourceConfigException("Unsupported YAML node type ${node.nodeType}.", span(node, source))
        }

        val tag = if (ignoreTag) null else customTag(node.tag, node, source)
        return if (tag == null) value else TaggedResourceConfig(tag, value, value.span)
    }

    private fun parseScalar(node: ScalarNode, source: String): Any? = when (node.tag) {
        Tag.NULL -> null
        Tag.STR -> node.value
        Tag.BOOL -> when (node.value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw ResourceConfigException("Invalid Boolean '${node.value}'.", span(node, source))
        }

        Tag.INT -> parseInteger(node.value, node, source)
        Tag.FLOAT -> node.value.replace("_", "").toDoubleOrNull()
            ?.takeIf { it.isFinite() }
            ?: throw ResourceConfigException("Invalid finite number '${node.value}'.", span(node, source))

        else -> throw ResourceConfigException("Unsupported scalar tag '${node.tag.value}'.", span(node, source))
    }

    private fun parseInteger(text: String, node: Node, source: String): Long {
        val normalized = text.replace("_", "")
        val negative = normalized.startsWith('-')
        val unsigned = normalized.removePrefix("+").removePrefix("-")
        val (radix, digits) = when {
            unsigned.startsWith("0x", true) -> 16 to unsigned.drop(2)
            unsigned.startsWith("0o", true) -> 8 to unsigned.drop(2)
            else -> 10 to unsigned
        }
        return try {
            val parsed = BigInteger(digits, radix).let { if (negative) it.negate() else it }
            parsed.longValueExact()
        } catch (exception: Exception) {
            throw ResourceConfigException("Integer '$text' is outside the supported range.", span(node, source), exception)
        }
    }

    private fun customTag(tag: Tag, node: Node, source: String): Identifier? {
        if (tag in STANDARD_TAGS) return null
        return try {
            id(tag.value)
        } catch (exception: Exception) {
            throw ResourceConfigException(
                "Custom YAML tag '${tag.value}' must be a CuTAPI namespace:type identifier.",
                node.startMark.orElse(null)?.let {
                    ResourceSourceSpan(source, it.line + 1, it.column + 1)
                },
                exception
            )
        }
    }

    private fun toNode(value: ResourceConfigValue): Node = when (value) {
        is ResourceConfigMap -> MappingNode(
            Tag.MAP,
            value.values.map { (key, child) ->
                NodeTuple(ScalarNode(Tag.STR, key, ScalarStyle.PLAIN), toNode(child))
            },
            FlowStyle.BLOCK
        )

        is ResourceConfigList -> SequenceNode(Tag.SEQ, value.values.map(::toNode), FlowStyle.BLOCK)
        is ResourceConfigScalar -> when (val scalar = value.value) {
            null -> ScalarNode(Tag.NULL, "null", ScalarStyle.PLAIN)
            is Boolean -> ScalarNode(Tag.BOOL, scalar.toString(), ScalarStyle.PLAIN)
            is Byte, is Short, is Int, is Long -> ScalarNode(Tag.INT, scalar.toString(), ScalarStyle.PLAIN)
            is Float -> finiteFloatNode(scalar.toDouble())
            is Double -> finiteFloatNode(scalar)
            is String -> ScalarNode(Tag.STR, scalar, ScalarStyle.PLAIN)
            else -> throw ResourceConfigException("Cannot encode scalar type ${scalar::class.qualifiedName}.", value.span)
        }

        is TaggedResourceConfig -> toNode(value.value).also { it.tag = Tag(value.id.toString()) }
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
        Tag.FLOAT
    )
}
