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

    @Test
    public fun `deferred contributions cannot commit after registry closure`() {
        val registry = IdentifierRegistry<TestValue>(id("test:registry/closed"))
        val deferred = registry.defer()
        deferred.register { TestValue(id("test:late")) }
        registry.initialize()

        assertFailsWith<IllegalStateException> {
            deferred.commitToRegistry()
        }
    }
}

private data class TestValue(override val id: Identifier) : Identifiable
