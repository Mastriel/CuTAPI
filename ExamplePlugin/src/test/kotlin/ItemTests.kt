import be.seeseemelk.mockbukkit.MockBukkit
import xyz.mastriel.exampleplugin.ExamplePlugin

class ItemTests {
    val server = MockBukkit.mock()
    val plugin = MockBukkit.load(ExamplePlugin::class.java)

}