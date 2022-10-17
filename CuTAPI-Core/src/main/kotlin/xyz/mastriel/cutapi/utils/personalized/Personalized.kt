package xyz.mastriel.cutapi.utils.personalized

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


interface Personalized<T> {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): (Player) -> T {
        return ::withViewer
    }

    infix fun withViewer(viewer: Player): T
}




fun <T> personalized(block: Personalized<T>.(Player) -> T): Personalized<T> {
    return SimplePersonalized(block)
}
