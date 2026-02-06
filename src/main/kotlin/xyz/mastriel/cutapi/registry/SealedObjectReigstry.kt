package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.*
import kotlin.reflect.*

/**
 * Used specifically to have a registry of sealed objects to consistency. Do not use on a sealed class
 * that has subclasses that are not singleton objects. This registry will ONLY contain objects of a sealed class.
 *
 * This DOES support nested sealed classes, such as a situation like this:
 * ```kt
 * sealed class Parent {
 *    object Child1 : Parent()
 *    sealed class Child2 : Parent()
 *
 *    object Grandchild1 : Child2()
 *    object Grandchild2 : Child2()
 * }
 * ```
 * In that case, Child1, Grandchild1, and Grandchild2 will be registered.
 */
public open class SealedObjectRegistry<T : Identifiable>(name: String, sealedClass: KClass<T>) :
    IdentifierRegistry<T>("$name (sealed)") {

    private var registrationsOpen = true

    init {
        if (!sealedClass.isSealed) {
            Plugin.logger.warning("${sealedClass.simpleName} is not a sealed class, and it's being used in a '${name}'!")
        }
        registerClass(sealedClass)
        registrationsOpen = false
    }

    private fun registerClass(kClass: KClass<out T>) {
        if (kClass.isSealed) {
            kClass.sealedSubclasses.forEach { subclass ->
                registerClass(subclass)
            }
        } else {
            if (!hasObjectInstance(kClass)) {
                Plugin.logger.warning("There's no object instance for ${kClass.simpleName} in a '${name}'.")
            } else {
                register(kClass.objectInstance!!)
            }
        }
    }

    private fun hasObjectInstance(kClass: KClass<out T>): Boolean {
        return try {
            kClass.objectInstance != null
        } catch (ex: Exception) {
            false
        }
    }

    /**
     * You cannot register after this class has finished initializing. This is to keep
     * the promise of not containing any other objects than that of the sealed subclass.
     * @throws IllegalStateException if you tried to register after initialization.
     */
    final override fun register(item: T): T {
        if (!registrationsOpen) error("Tried to register to a SealedObjectRegistry after initialization.")
        return super.register(item)
    }
}