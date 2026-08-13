package xyz.mastriel.cutapi.gui

import kotlinx.coroutines.ThreadContextElement
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import xyz.mastriel.cutapi.block.CuTPlacedTileEntity
import xyz.mastriel.cutapi.isPluginInitialized
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class GuiDefinitionToken(val debugName: String)

internal class GuiStateKey<T>(
    val ordinal: Long,
    val propertyName: String,
)

internal class GuiSharedStateKey<T>(
    val ordinal: Long,
    val propertyName: String,
)

internal data class GuiStateDeclaration<C, T>(
    val key: GuiStateKey<T>,
    val initialize: GuiStateInitializationContext<C>.() -> T,
)

public class GuiStateInitializationContext<C> internal constructor(
    public val viewer: Player,
    public val subject: GuiSubject,
    public val context: C,
) {
    public val tileEntityOrNull: CuTPlacedTileEntity?
        get() = (subject as? GuiSubject.Block)?.tile

    public fun requireTileEntity(): CuTPlacedTileEntity =
        tileEntityOrNull ?: error("This GUI session is not attached to a custom tile entity.")
}

internal enum class GuiExecutionPhase {
    Initializing,
    Rendering,
    Handling,
    Closing,
}

internal data class GuiExecutionContext(
    val session: GuiSession<*, *>,
    val phase: GuiExecutionPhase,
)

internal object GuiExecution {
    private val currentContext: ThreadLocal<GuiExecutionContext?> = ThreadLocal()

    fun currentOrNull(): GuiExecutionContext? = currentContext.get()

    fun require(token: GuiDefinitionToken): GuiExecutionContext {
        requirePrimaryThread()
        val context = currentContext.get()
            ?: error("GUI delegated state may only be accessed while a GUI callback or renderer is executing.")
        check(context.session.definition.token === token) {
            "GUI delegated state for ${token.debugName} was accessed while ${context.session.definition.id} was executing."
        }
        check(!context.session.isClosed) {
            "GUI delegated state for ${token.debugName} was accessed after session ${context.session.id} closed."
        }
        check(context.phase != GuiExecutionPhase.Initializing) {
            "A GUI state initializer may not read or assign another delegated state value."
        }
        return context
    }

    inline fun <T> with(context: GuiExecutionContext, action: () -> T): T {
        val previous = currentContext.get()
        currentContext.set(context)
        return try {
            action()
        } finally {
            currentContext.set(previous)
        }
    }

    fun install(context: GuiExecutionContext?): GuiExecutionContext? {
        val previous = currentContext.get()
        currentContext.set(context)
        return previous
    }

    fun requirePrimaryThread() {
        check(!isPluginInitialized() || Bukkit.isPrimaryThread()) {
            "GUI state and inventory access is restricted to the server primary thread."
        }
    }
}

internal class GuiExecutionContextElement(
    private val execution: GuiExecutionContext,
) : ThreadContextElement<GuiExecutionContext?>,
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GuiExecutionContextElement>

    override fun updateThreadContext(context: CoroutineContext): GuiExecutionContext? =
        GuiExecution.install(execution)

    override fun restoreThreadContext(context: CoroutineContext, oldState: GuiExecutionContext?) {
        GuiExecution.install(oldState)
    }
}

public class GuiStateDelegateProvider<C, T> internal constructor(
    private val token: GuiDefinitionToken,
    private val register: (String, GuiStateInitializationContext<C>.() -> T) -> GuiStateKey<T>,
    private val initialize: GuiStateInitializationContext<C>.() -> T,
) {
    public operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadWriteProperty<Any?, T> {
        val key = register(property.name, initialize)
        return GuiStateDelegate(token, key)
    }
}

private class GuiStateDelegate<T>(
    private val token: GuiDefinitionToken,
    private val key: GuiStateKey<T>,
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val context = GuiExecution.require(token)
        return context.session.readState(key)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val context = GuiExecution.require(token)
        context.session.writeState(key, value)
        context.session.invalidate()
    }
}

public class GuiSharedStateDelegateProvider<T> internal constructor(
    private val token: GuiDefinitionToken,
    private val register: (String, T) -> GuiSharedStateKey<T>,
    private val initialize: () -> T,
) {
    public operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadWriteProperty<Any?, T> {
        val key = register(property.name, initialize())
        return GuiSharedStateDelegate(token, key)
    }
}

private class GuiSharedStateDelegate<T>(
    private val token: GuiDefinitionToken,
    private val key: GuiSharedStateKey<T>,
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        val context = GuiExecution.require(token)
        return context.session.definition.readSharedState(key)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val context = GuiExecution.require(token)
        context.session.definition.writeSharedState(key, value)
        context.session.manager.invalidate(context.session.definition)
    }
}

internal object GuiStateKeySequence {
    private val next: AtomicLong = AtomicLong()

    fun next(): Long = next.getAndIncrement()
}
