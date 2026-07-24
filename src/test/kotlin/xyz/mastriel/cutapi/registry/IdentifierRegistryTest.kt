package xyz.mastriel.cutapi.registry

import kotlin.test.*

public class IdentifierRegistryTest {
    @Test
    public fun `registry identity is its identifier`() {
        val registryId = id("test:registry/example")
        val registry = IdentifierRegistry<TestValue>(registryId)

        assertIs<Identifiable>(registry)
        assertEquals(registryId, registry.id)
    }
}

private data class TestValue(override val id: Identifier) : Identifiable
