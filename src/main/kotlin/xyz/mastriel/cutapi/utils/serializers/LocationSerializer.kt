package xyz.mastriel.cutapi.utils.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import org.bukkit.Bukkit
import org.bukkit.Location

object LocationSerializer : KSerializer<Location> {
    override fun deserialize(decoder: Decoder): Location =
        decoder.decodeStructure(descriptor) {
            var x = 0.0
            var y = 0.0
            var z = 0.0
            var worldName = ""

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> x = decodeDoubleElement(descriptor, 0)
                    1 -> y = decodeDoubleElement(descriptor, 1)
                    2 -> z = decodeDoubleElement(descriptor, 2)
                    3 -> worldName = decodeStringElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            val world1 = Bukkit.getWorld(worldName)!!
            Location(world1, x, y, z)
        }


    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Location") {
            element<Double>("x")
            element<Double>("y")
            element<Double>("z")
            element<String>("world")
        }

    override fun serialize(encoder: Encoder, value: Location) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.x)
            encodeDoubleElement(descriptor, 1, value.y)
            encodeDoubleElement(descriptor, 2, value.z)
            encodeStringElement(descriptor, 3, value.world.name)
        }
    }
}