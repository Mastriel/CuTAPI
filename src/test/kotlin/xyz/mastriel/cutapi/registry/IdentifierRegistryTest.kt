package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.testing.*
import kotlin.test.*

public class IdentifierRegistryTest : MockBukkitTest() {
    @Test
    public fun `registry identity is its identifier`() {
        val registryId = id("test:registry/example")
        val registry = IdentifierRegistry<TestValue>(registryId)

        assertIs<Identifiable>(registry)
        assertEquals(registryId, registry.id)
        assertSame(registry, IdentifierRegistry.AllRegistries.get(registryId))
        assertSame(
            IdentifierRegistry.AllRegistries,
            IdentifierRegistry.AllRegistries.get(id("cutapi:registries"))
        )
    }
}

private data class TestValue(override val id: Identifier) : Identifiable
