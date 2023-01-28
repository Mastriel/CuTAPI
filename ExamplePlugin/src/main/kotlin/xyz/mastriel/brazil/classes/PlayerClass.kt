package xyz.mastriel.brazil.classes

import kotlinx.serialization.Serializable
import xyz.mastriel.brazil.classes.spelunker.RaiderSubclass
import xyz.mastriel.brazil.classes.spelunker.SpelunkerClass
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.IdentifiableSerializer
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry


@Serializable(with = PlayerClass.Serializer::class)
abstract class PlayerClass(override val id: Identifier) : Identifiable, Specialization() {

    abstract val name : String

    object Serializer : IdentifiableSerializer<PlayerClass>("player_class", PlayerClass)

    companion object : IdentifierRegistry<PlayerClass>("Player Classes") {


        fun init() {
            register(SpelunkerClass)
            PlayerSubclass.register(RaiderSubclass)

        }
    }
}
