package xyz.mastriel.cutapi.utils

import xyz.mastriel.cutapi.CuTAPI
import kotlin.reflect.KClass

class ServiceManager {
    private val _registeredServices = mutableMapOf<KClass<Any>, Any>()
    val registeredServices : Map<KClass<Any>, Any> = _registeredServices

    inline fun <reified T: Any> getServiceOrNull() : T? = getServiceOrNull(T::class)
    @Suppress("UNCHECKED_CAST")
    fun <T: Any> getServiceOrNull(kClass: KClass<T>) : T? {
        return _registeredServices[kClass as KClass<Any>] as? T
    }

    inline fun <reified T: Any> getService() : T = getServiceOrNull(T::class) ?: error("Service not found.")
    fun <T: Any> getService(kClass: KClass<T>) : T = getServiceOrNull(kClass) ?: error("Service not found.")

    @Suppress("UNCHECKED_CAST")
    fun <T: Any, I: T> registerService(kClass: KClass<T>, service: I) {
        kClass as KClass<Any>
        _registeredServices[kClass] = service
    }

    inline fun <reified T: Any, I: T> registerService(service: I) = registerService(T::class, service)
}

internal inline fun <reified T: Any> getCuTService() = getCuTService(T::class)
internal fun <T: Any> getCuTService(kClass: KClass<T>) = CuTAPI.serviceManager.getService(kClass)