package xyz.mastriel.cutapi.testing

import org.mockbukkit.mockbukkit.*
import kotlin.test.*

public abstract class MockBukkitTest {
    protected lateinit var server: ServerMock

    @BeforeTest
    public fun setUpMockBukkit() {
        server = MockBukkit.mock()
    }

    @AfterTest
    public fun tearDownMockBukkit() {
        MockBukkit.unmock()
    }
}
