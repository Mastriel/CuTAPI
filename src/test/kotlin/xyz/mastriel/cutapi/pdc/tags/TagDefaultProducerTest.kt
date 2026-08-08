package xyz.mastriel.cutapi.pdc.tags

import org.bukkit.*
import xyz.mastriel.cutapi.pdc.tags.converters.*
import xyz.mastriel.cutapi.registry.*
import kotlin.test.*

public class TagDefaultProducerTest {
    @Test
    public fun `tag default producers create one independent value per delegate`() {
        var produced = 0
        val firstTag = EmptyTagContainer.locationTag(id("test:first_location")) {
            produced++
            Location(null, 1.0, 2.0, 3.0)
        }
        val secondTag = EmptyTagContainer.locationTag(id("test:second_location")) {
            produced++
            Location(null, 1.0, 2.0, 3.0)
        }

        val first = firstTag.get()
        assertSame(first, firstTag.get())
        val second = secondTag.get()

        assertEquals(2, produced)
        assertNotSame(first, second)
        first.x = 10.0
        assertEquals(1.0, second.x)
    }

    @Test
    public fun `tag default producers permit explicitly shared values`() {
        val shared = Location(null, 1.0, 2.0, 3.0)
        val first = EmptyTagContainer.locationTag(id("test:first_shared_location")) { shared }
        val second = EmptyTagContainer.locationTag(id("test:second_shared_location")) { shared }

        assertSame(shared, first.get())
        assertSame(first.get(), second.get())
    }

    @Test
    public fun `nullable tag producers are used when no value is stored`() {
        var produced = 0
        val tag = EmptyTagContainer.nullableLocationTag(id("test:nullable_location")) {
            produced++
            Location(null, 1.0, 2.0, 3.0)
        }

        val value = assertNotNull(tag.get())
        assertSame(value, tag.get())
        assertEquals(1, produced)
    }
}

private object EmptyTagContainer : TagContainer {
    override fun <P : Any, C : Any> set(
        id: Identifier,
        complexValue: C?,
        converter: TagConverter<P, C>
    ) {
        error("This test container does not store values")
    }

    override fun <P : Any, C : Any> get(
        id: Identifier,
        converter: TagConverter<P, C>
    ): C? = null

    override fun has(id: Identifier): Boolean = false

    override fun isNull(id: Identifier): Boolean = false
}
