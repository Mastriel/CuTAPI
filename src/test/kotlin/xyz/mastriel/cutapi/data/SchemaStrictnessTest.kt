package xyz.mastriel.cutapi.data

import xyz.mastriel.cutapi.registry.*
import kotlin.test.*

class SchemaStrictnessTest {
    @Test
    fun `schemas are strict by default`() {
        assertTrue(StrictValue.strict)
        val shape = assertIs<SerializerShape.Object>(StrictValue.descriptor.shape)
        assertTrue(shape.strict)
        assertIs<DeserializeResult.Failure>(
            StrictValue.deserialize(
                Variant.Map(
                    mapOf(
                        SCHEMA_TYPE_DISCRIMINATOR to Variant.String(StrictValue.id.toString()),
                        "name" to Variant.String("value"),
                        "unknown" to Variant.Boolean(true)
                    )
                )
            )
        )
    }

    @Test
    fun `child inherits non-strict parent and can explicitly override it`() {
        assertFalse(PermissiveParent.strict)
        assertFalse(InheritedChild.strict)
        assertTrue(StrictChild.strict)
        assertFalse(ExplicitPermissiveChild.strict)

        val permissive = InheritedChild.deserializeJson(
            """{"parent":"base","child":4,"unknown":true}"""
        ).getOrThrow()
        assertEquals(InheritedChild("base", 4), permissive)

        assertIs<DeserializeResult.Failure>(
            StrictChild.deserializeJson(
                """{"parent":"base","child":4,"unknown":true}"""
            )
        )
    }

    @Test
    fun `polymorphic descriptors retain selected schema strictness`() {
        val descriptor = PolymorphicParent.descriptor.shape
        val childShape = descriptor.included.single { it.id == InheritedChild.id }.shape
        assertFalse(childShape.strict)

        val decoded = PolymorphicParent.deserialize(
            Variant.Map(
                mapOf(
                    SCHEMA_TYPE_DISCRIMINATOR to Variant.String(InheritedChild.id.toString()),
                    "parent" to Variant.String("base"),
                    "child" to Variant.Int(4),
                    "unknown" to Variant.Boolean(true)
                )
            )
        ).getOrThrow()
        assertIs<InheritedChild>(decoded)
    }
}

private data class StrictValue(val name: String) {
    companion object : Schema<StrictValue> by schema(id("test:strict_value"), {
        property(StrictValue::name, VariantSerializer.String)
    })
}

private open class PermissiveParent(open val parent: String) {
    companion object : Schema<PermissiveParent> by schema(id("test:permissive_parent"), {
        strict = false
        property(PermissiveParent::parent, VariantSerializer.String)
    })
}

private data class InheritedChild(
    override val parent: String,
    val child: Int
) : PermissiveParent(parent) {
    companion object : Schema<InheritedChild> by schema(id("test:inherited_child"), {
        extends { PermissiveParent }
        property(InheritedChild::child, VariantSerializer.Int)
    })
}

private data class StrictChild(
    override val parent: String,
    val child: Int
) : PermissiveParent(parent) {
    companion object : Schema<StrictChild> by schema(id("test:strict_child"), {
        extends { PermissiveParent }
        strict = true
        property(StrictChild::child, VariantSerializer.Int)
    })
}

private open class StrictParent(open val name: String) {
    companion object : Schema<StrictParent> by schema(id("test:strict_parent"), {
        property(StrictParent::name, VariantSerializer.String)
    })
}

private data class ExplicitPermissiveChild(override val name: String) : StrictParent(name) {
    companion object : Schema<ExplicitPermissiveChild> by schema(id("test:explicit_permissive_child"), {
        extends { StrictParent }
        strict = false
    })
}

private val PolymorphicParent: PolySchema<PermissiveParent> = polySchema(
    PermissiveParent::class,
    id("test:polymorphic_parent")
) {
    strict = false
    property(PermissiveParent::parent, VariantSerializer.String)
    include(InheritedChild)
}
