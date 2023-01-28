package xyz.mastriel.brazil.classes

import kotlinx.serialization.Serializable
import xyz.mastriel.cutapi.registry.Identifiable
import xyz.mastriel.cutapi.registry.IdentifiableSerializer
import xyz.mastriel.cutapi.registry.Identifier
import xyz.mastriel.cutapi.registry.IdentifierRegistry

@Serializable(with = PlayerSubclass.Serializer::class)
abstract class PlayerSubclass(val parent: PlayerClass, override val id: Identifier) : Identifiable, Specialization() {

    abstract val name: String


    object Serializer : IdentifiableSerializer<PlayerSubclass>("player_subclass", PlayerSubclass)

    companion object : IdentifierRegistry<PlayerSubclass>("Player Subclasses")
}