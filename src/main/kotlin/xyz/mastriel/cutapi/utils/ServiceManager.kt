package xyz.mastriel.cutapi.utils

import xyz.mastriel.cutapi.*
import kotlin.reflect.*

public class ServiceManager {
    private val _registeredServices = mutableMapOf<KClass<Any>, Any>()
    public val registeredServices: Map<KClass<Any>, Any> = _registeredServices

    public inline fun <reified T : Any> getServiceOrNull(): T? = getServiceOrNull(T::class)

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> getServiceOrNull(kClass: KClass<T>): T? {
        return _registeredServices[kClass as KClass<Any>] as? T
    }

    public inline fun <reified T : Any> getService(): T = getServiceOrNull(T::class) ?: error("Service not found.")
    public fun <T : Any> getService(kClass: KClass<T>): T = getServiceOrNull(kClass) ?: error("Service not found.")

    @Suppress("UNCHECKED_CAST")
    public fun <T : Any, I : T> registerService(kClass: KClass<T>, service: I) {
        kClass as KClass<Any>
        _registeredServices[kClass] = service
    }

    public inline fun <reified T : Any, I : T> registerService(service: I): Unit = registerService(T::class, service)
}

internal inline fun <reified T : Any> getCuTService() = getCuTService(T::class)
internal fun <T : Any> getCuTService(kClass: KClass<T>) = CuTAPI.serviceManager.getService(kClass)