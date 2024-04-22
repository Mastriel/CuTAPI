package xyz.mastriel.cutapi.periodic

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.bukkit.scheduler.BukkitRunnable
import xyz.mastriel.cutapi.Plugin
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.isAccessible

class PeriodicManager {


    fun <T: Any> register(instance: T) {
        val periodicFunctions = getFunctions(instance::class)
        periodicFunctions.forEach { (function, ticks) ->
            if (function.isSuspend) {
                if (function.findAnnotation<Periodic>()?.asyncThread == true) {
                    Plugin.launch {
                        while (true) {
                            withContext(Plugin.asyncDispatcher) {
                                function.callSuspend(instance)
                            }
                            delay(ticks.ticks)
                        }
                    }
                }
                Plugin.launch {
                    function.callSuspend(instance)
                }
                return
            }
            object : BukkitRunnable() {
                override fun run() {
                    function.call(instance)
                }
            }.runTaskTimer(Plugin, ticks.toLong(), ticks.toLong())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T: Any> getFunctions(kClass: KClass<out T>) : Map<KFunction<Unit>, Int> {
        return kClass.functions.filter { it.hasAnnotation<Periodic>() }
            .filter { it.parameters.size == 1 }
            .map { it.isAccessible = true; it }
            .associateWith { it.findAnnotation<Periodic>()!!.ticks }
            as Map<KFunction<Unit>, Int>
    }
}