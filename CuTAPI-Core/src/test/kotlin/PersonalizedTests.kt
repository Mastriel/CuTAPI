import be.seeseemelk.mockbukkit.MockBukkit
import xyz.mastriel.cutapi.utils.personalized.or
import xyz.mastriel.cutapi.utils.personalized.personalized
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonalizedTests {

    val server = MockBukkit.mock()

    @Test
    fun `combining PersonalizedWithDefault with constantValue using or`() {
        val personalized = personalized(20L)

        assert(personalized.getDefault() == 20L) { "personalized(constantValue) not returning constant value" }

        val player = server.addPlayer()
        assert(personalized withViewer player == 20L) { "personalized(constantValue) should return same getDefault() as withViewer()" }

        assert((personalized or 30L) withViewer player == 20L) { "PersonalizedWithDefault.or should do nothing and disregard the new default." }

        assert((personalized or 30L).getDefault() == 20L) { "PersonalizedWithDefault.or should do nothing and disregard the new default." }
    }

    @Test
    fun `Personalized alterResult`() {
        var personalized = personalized { 20L }
        var constantPersonalizedWithDefault = personalized(20L)
        var childPersonalizedWithDefault = personalized { 20L } or 30L
        val player = server.addPlayer()

        personalized = personalized alterResult { _, long ->
            long + 20
        }

        assertEquals(personalized withViewer player, 40L,
            "Personalized.alterResult does not change withViewer value")

        constantPersonalizedWithDefault = constantPersonalizedWithDefault alterResult { long ->
            long + 20
        }

        assertEquals(constantPersonalizedWithDefault withViewer player, 40L,
            "ConstantPersonalizedWithDefault.alterResult does not change withViewer value")
        assertEquals(constantPersonalizedWithDefault.getDefault(), 20L,
            "ConstantPersonalizedWithDefault.alterResult changes getDefault value")

        childPersonalizedWithDefault = childPersonalizedWithDefault alterResult { long ->
            long + 20
        }

        assertEquals(childPersonalizedWithDefault withViewer player, 40L,
            "ChildPersonalizedWithDefault.alterResult does not change withViewer value")
        assertEquals(childPersonalizedWithDefault.getDefault(), 30L,
            "ChildPersonalizedWithDefault.alterResult changes getDefault value")


    }

}