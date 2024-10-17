package xyz.mastriel.cutapi.periodic

import com.github.shynixn.mccoroutine.bukkit.*
import kotlinx.coroutines.*
import org.bukkit.scheduler.*
import xyz.mastriel.cutapi.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

public class PeriodicManager {

    private sealed class PeriodicTask {
        abstract fun cancel()

        class BukkitTask(private val task: org.bukkit.scheduler.BukkitTask) : PeriodicTask() {
            override fun cancel() {
                task.cancel()
            }
        }

        class CoroutineTask(private val job: Job) : PeriodicTask() {
            override fun cancel() {
                job.cancel()
            }
        }
    }

    private val tasks = mutableMapOf<CuTPlugin, MutableList<PeriodicTask>>()

    public fun <T : Any> register(plugin: CuTPlugin, instance: T) {
        val periodicFunctions = getFunctions(instance::class)
        periodicFunctions.forEach { (function, ticks) ->
            if (function.isSuspend) {
                if (function.findAnnotation<Periodic>()?.asyncThread == true) {
                    return createSuspendAsyncCoroutineTask(function, instance, plugin, ticks)
                }
                return createSuspendCoroutineTask(function, instance, plugin, ticks)
            }
            createBukkitTask(function, instance, plugin, ticks)
        }
    }

    private fun createSuspendAsyncCoroutineTask(
        function: KFunction<Unit>,
        instance: Any,
        plugin: CuTPlugin,
        ticks: Int
    ) {
        val task = Plugin.launch {
            while (true) {
                withContext(Plugin.asyncDispatcher) {
                    function.callSuspend(instance)
                }
                delay(ticks.ticks)
            }
        }
        tasks.getOrPut(plugin) { mutableListOf() }.add(PeriodicTask.CoroutineTask(task))
    }

    private fun createSuspendCoroutineTask(function: KFunction<Unit>, instance: Any, plugin: CuTPlugin, ticks: Int) {
        val task = Plugin.launch {
            while (true) {
                function.callSuspend(instance)
                delay(ticks.ticks)
            }
        }

        tasks.getOrPut(plugin) { mutableListOf() }.add(PeriodicTask.CoroutineTask(task))
    }

    private fun createBukkitTask(function: KFunction<Unit>, instance: Any, plugin: CuTPlugin, ticks: Int) {
        val task = object : BukkitRunnable() {
            override fun run() {
                function.call(instance)
            }
        }.runTaskTimer(plugin.plugin, ticks.toLong(), ticks.toLong())

        tasks.getOrPut(plugin) { mutableListOf() }.add(PeriodicTask.BukkitTask(task))
    }

    public fun cancelAll(plugin: CuTPlugin) {
        tasks[plugin]?.forEach { it.cancel() }
        tasks.remove(plugin)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getFunctions(kClass: KClass<out T>): Map<KFunction<Unit>, Int> {
        return kClass.functions.filter { it.hasAnnotation<Periodic>() }
            .filter { it.parameters.size == 1 }
            .onEach { it.isAccessible = true }
            .associateWith { it.findAnnotation<Periodic>()!!.ticks }
            as Map<KFunction<Unit>, Int>
    }
}