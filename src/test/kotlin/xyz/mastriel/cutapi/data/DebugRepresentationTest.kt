package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.test.*

class DebugRepresentationTest {
    @Test
    fun `serializable identifiable values use their serializer as the default debug view`() {
        val value = SerializableDebugValue(id("test:debug_value"), "visible")

        assertSame(SerializableDebugValueSerializer, value.debugView)
        assertEquals(
            Variant.String("visible"),
            value.debugView!!.serializeUnchecked(value).getOrThrow()
        )
    }

    @Test
    fun `custom debug views serialize their type and only their declared properties`() {
        val representation = debugView<PartialDebugValue>(id("test:partial_debug_value")) {
            property(PartialDebugValue::visible, VariantSerializer.String)
        }

        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to
                        Variant.String("test:partial_debug_value"),
                    Variant.String("visible") to Variant.String("shown")
                )
            ),
            representation.serialize(PartialDebugValue("shown", "hidden")).getOrThrow()
        )
    }

    @Test
    fun `custom debug views can extend multiple parent representations`() {
        val firstParent = debugView<FirstDebugParent>(id("test:first_debug_parent")) {
            property(FirstDebugParent::first, VariantSerializer.String)
        }
        val secondParent = debugView<SecondDebugParent>(id("test:second_debug_parent")) {
            property(SecondDebugParent::second, VariantSerializer.Int)
        }
        val child = debugView<ExtendedDebugValue>(id("test:extended_debug_value")) {
            extends { firstParent }
            extends { secondParent }
            property(ExtendedDebugValue::own, VariantSerializer.Boolean)
        }

        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to
                        Variant.String("test:extended_debug_value"),
                    Variant.String("first") to Variant.String("parent"),
                    Variant.String("second") to Variant.Int(2),
                    Variant.String("own") to Variant.Boolean(true)
                )
            ),
            child.serialize(ExtendedDebugValue("parent", 2, true)).getOrThrow()
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun DebugRepresentation<*>.serializeUnchecked(value: Any): SerializeResult =
        (this as DebugRepresentation<Any>).serialize(value)
}

private data class PartialDebugValue(
    val visible: String,
    val hidden: String
)

private interface FirstDebugParent {
    val first: String
}

private interface SecondDebugParent {
    val second: Int
}

private data class ExtendedDebugValue(
    override val first: String,
    override val second: Int,
    val own: Boolean
) : FirstDebugParent, SecondDebugParent

private data class SerializableDebugValue(
    override val id: Identifier,
    val value: String
) : Identifiable, Serializable<SerializableDebugValue> {
    override val serializer: Serializer<SerializableDebugValue>
        get() = SerializableDebugValueSerializer
}

private val SerializableDebugValueSerializer: Serializer<SerializableDebugValue> = serializer(
    serialize = { Variant.String(it.value) },
    deserialize = {
        val value = (it as? Variant.String)?.value
            ?: throw VariantTypeException("string", it)
        SerializableDebugValue(id("test:debug_value"), value)
    }
)
