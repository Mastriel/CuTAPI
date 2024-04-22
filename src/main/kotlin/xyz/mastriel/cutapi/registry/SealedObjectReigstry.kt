package xyz.mastriel.cutapi.registry

import xyz.mastriel.cutapi.Plugin
import kotlin.reflect.KClass

/**
 * Used specifically to have a registry of sealed objects to consistency. Do not use on a sealed class
 * that has subclasses that are not singleton objects. This registry will ONLY contain objects of a sealed class.
 */
open class SealedObjectRegistry<T : Identifiable>(name: String, sealedClass: KClass<T>) : IdentifierRegistry<T>("$name (sealed)") {

    private var registrationsOpen = true

    init {
        if (!sealedClass.isSealed) {
            Plugin.logger.warning("${sealedClass.simpleName} is not a sealed class, and it's being used in a Sealed Object Registry!")
        }
        for (kClass in sealedClass.sealedSubclasses) {
            val instance = kClass.objectInstance
            if (instance == null) {
                Plugin.logger.warning("There's no object instance for ${kClass.simpleName} in a sealed object registry.")
                continue
            }
            register(instance)
        }
        registrationsOpen = false
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