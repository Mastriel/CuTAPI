package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.test.*

class EncodeOnlySerializerTest {
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
    fun `custom debug views can extend companion and external parent views`() {
        val secondParentView = debugView<SecondDebugParent>(id("test:second_debug_parent")) {
            property(SecondDebugParent::second, VariantSerializer.Int)
        }
        val child = debugView<ExtendedDebugValue>(id("test:extended_debug_value")) {
            extends { FirstDebugParent }
            extends { secondParentView }
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

    @Test
    fun `debug view extensions can exclude renamed parent properties`() {
        val child = debugView<ExcludedDebugValue>(id("test:excluded_debug_value")) {
            extends { ExcludedDebugParent }
                .exclude { ExcludedDebugParent::name }
                .exclude { ExcludedDebugParent::age }
            property(ExcludedDebugValue::own, VariantSerializer.Boolean)
        }

        assertEquals(
            Variant.Map(
                linkedMapOf(
                    Variant.String(SCHEMA_TYPE_DISCRIMINATOR) to
                        Variant.String("test:excluded_debug_value"),
                    Variant.String("own") to Variant.Boolean(true)
                )
            ),
            child.serialize(ExcludedDebugValue(true)).getOrThrow()
        )
    }

    @Test
    fun `identifiable values discover debug views from their companion`() {
        val value = CompanionDebugValue(id("test:companion_debug_value"), "visible")
        val companionView = DebugViewProvider.fromCompanion<CompanionDebugValue>()

        assertSame(companionView, value.debugView)
        assertSame(companionView, CompanionDebugValue.provideDebugView())
    }

    @Test
    fun `debug view inheritance rejects unrelated parent types`() {
        val invalid = debugView<PartialDebugValue>(id("test:invalid_debug_child")) {
            extends { FirstDebugParent }
        }
        val failure = assertIs<SerializeResult.Failure>(
            invalid.serialize(PartialDebugValue("shown", "hidden"))
        )

        assertContains(failure.error.message.orEmpty(), "must inherit")
    }

    @Suppress("UNCHECKED_CAST")
    private fun EncodeOnlySerializer<*>.serializeUnchecked(value: Any): SerializeResult =
        (this as EncodeOnlySerializer<Any>).serialize(value)
}

private data class PartialDebugValue(
    val visible: String,
    val hidden: String
)

private interface FirstDebugParent {
    val first: String

    companion object : DebugViewProvider<FirstDebugParent> by debugView(
        id("test:first_debug_parent"),
        {
            property(FirstDebugParent::first, VariantSerializer.String)
        }
    )
}

private interface SecondDebugParent {
    val second: Int
}

private data class ExtendedDebugValue(
    override val first: String,
    override val second: Int,
    val own: Boolean
) : FirstDebugParent, SecondDebugParent

private interface ExcludedDebugParent {
    val name: String
    val age: Int

    companion object : DebugViewProvider<ExcludedDebugParent> by debugView(
        id("test:excluded_debug_parent"),
        {
            property(ExcludedDebugParent::name, VariantSerializer.String, name = "display_name")
            property(ExcludedDebugParent::age, VariantSerializer.Int)
        }
    )
}

private data class ExcludedDebugValue(
    val own: Boolean
) : ExcludedDebugParent {
    override val name: String = "fixed"
    override val age: Int = 21
}

private data class CompanionDebugValue(
    override val id: Identifier,
    val value: String
) : Identifiable {
    companion object : DebugViewProvider<CompanionDebugValue> by debugView(
        id("test:companion_debug_value"),
        {
            property(CompanionDebugValue::id, VariantSerializer.Id)
            property(CompanionDebugValue::value, VariantSerializer.String)
        }
    )
}

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
