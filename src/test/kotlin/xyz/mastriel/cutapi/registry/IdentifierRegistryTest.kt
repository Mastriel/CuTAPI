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

    @Test
    public fun `deferred references preserve producer identity and early materialization`() {
        val registry = IdentifierRegistry<TestValue>(id("test:registry/deferred_identity"))
        val deferred = registry.defer()
        val valueId = id("test:deferred_identity")
        var invocations = 0
        val producer: () -> TestValue = {
            invocations++
            TestValue(valueId)
        }
        val reference = deferred.register(producer)
        deferred.associateId(producer, valueId)

        assertFalse(reference.hasEarlyInit())
        assertEquals(valueId, deferred.getByProducer(producer))
        assertEquals(0, invocations)
        val early = reference.get()
        assertTrue(reference.hasEarlyInit())
        assertSame(early, reference.get())
        assertEquals(1, invocations)

        deferred.commitToRegistry()
        assertFailsWith<IllegalStateException> { deferred.commitToRegistry() }
        registry.initialize()

        assertEquals(1, invocations)
        assertSame(early, registry.get(valueId))
    }

    @Test
    public fun `deferred registry priority controls contribution order`() {
        val registry = IdentifierRegistry<TestValue>(id("test:registry/deferred_priority"))
        val order = mutableListOf<Identifier>()
        registry.addHook(HookPriority.First) { order += item.id }
        val low = registry.defer(RegistryPriority.Low)
        val high = registry.defer(RegistryPriority.High)
        val lowId = id("test:deferred_low")
        val highId = id("test:deferred_high")

        low.register { TestValue(lowId) }
        high.register { TestValue(highId) }
        low.commitToRegistry()
        high.commitToRegistry()
        registry.initialize()

        assertEquals(listOf(lowId, highId), order)
    }
}

private data class TestValue(override val id: Identifier) : Identifiable
