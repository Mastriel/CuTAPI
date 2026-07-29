package xyz.mastriel.cutapi.data

import kotlin.reflect.*

public sealed interface SchemaPropertyPresence<out T> {
    /** The serialized property must be present, even when its Kotlin constructor parameter has a default. */
    public data object Required : SchemaPropertyPresence<Nothing>

    /**
     * The serialized property may be absent.
     *
     * Without an explicit schema default, inferred construction retains a Kotlin
     * constructor or mutable instance default. Requesting the absent value from a
     * custom construction block is an error.
     */
    public class Optional<T> internal constructor(
        public val omitDefaults: Boolean,
        internal val defaultProvider: (() -> T)?
    ) : SchemaPropertyPresence<T> {
        public val hasDefault: Boolean
            get() = defaultProvider != null

        public fun defaultValue(): T =
            defaultProvider?.invoke()
                ?: error("This optional property does not define an explicit default")
    }
}

public interface SchemaPropertyBuilder<R : Any, T> : DebugPropertyBuilder<T> {
    /** Requires the property in serialized input. This is the default. */
    public fun required()

    /** Allows the property to be absent without defining a schema default. */
    public fun optional()

    /** Defines the value used when the property is absent. */
    public fun optional(
        default: T,
        omitDefaults: Boolean = false
    )

    /** Defines a lazily created value used when the property is absent. */
    public fun optional(
        omitDefaults: Boolean = false,
        default: () -> T
    )
}

/**
 * Values available to a schema's custom construction factory.
 *
 * Reading a value marks it as consumed. Decoded values that remain unconsumed
 * are applied through their schema property's setter after the factory returns.
 */
public interface SchemaConstructionContext<R : Any> {
    public fun <T> value(property: KProperty1<*, T>): T

    public fun <T> value(property: SchemaProperty<*, T>): T

    public fun wasProvided(property: KProperty1<*, *>): Boolean

    public fun wasProvided(property: SchemaProperty<*, *>): Boolean
}
