package xyz.mastriel.cutapi.resourcepack.postprocess.builtin

import com.jhlabs.image.AbstractBufferedImageOp
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.registry.Identifier
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.reflect

/**
 * @param R The reciever.
 * @param T The type of the property.
 */
private typealias Setter<R, T> = R.(T) -> Unit

/**
 * A map of [AbstractBufferedImageOp]'s setter methods and `post_process.properties` fields in a texture's .cutmeta,
 * to create easy definitions for porting [com.jhlabs.image] for use in `post_process`.
 */
class ImageOpPropertyMap<B : AbstractBufferedImageOp> internal constructor() {
    private val setters = hashMapOf<String, Setter<*, *>>()

    /**
     * Adds a property definition to this map using a name, and a setter.
     *
     * @param name The property name used in `post_process.properties`
     * @param setter The setter for a property for this [AbstractBufferedImageOp]
     */
    fun <R : AbstractBufferedImageOp, T : Any> property(
        name: String,
        setter: Setter<R, T>
    ) {
        setters[name] = setter
    }

    /**
     * Sets the values from the [properties] map to the [reciever].
     *
     * @param reciever the [AbstractBufferedImageOp] being altered.
     * @param properties the map of properties from a texture's post process properties for B.
     */
    @OptIn(ExperimentalReflectionOnLambdas::class)
    fun setValues(reciever: B, properties: Map<String, JsonElement>) {
        for ((name, element) in properties) {
            val setterType = getSetter<Any>(name)?.reflect()?.parameters?.first()?.type?.classifier ?: continue
            if (element is JsonPrimitive) {
                when {
                    setterType == Int::class && element.intOrNull != null -> // int
                        getSetter<Int>(name)?.invoke(reciever, element.int)

                    setterType == Float::class && element.floatOrNull != null -> // float
                        getSetter<Float>(name)?.invoke(reciever, element.float)

                    setterType == Double::class && element.doubleOrNull != null -> // double
                        getSetter<Double>(name)?.invoke(reciever, element.double)

                    setterType == Long::class && element.longOrNull != null -> // long
                        getSetter<Long>(name)?.invoke(reciever, element.long)

                    setterType == Boolean::class && element.booleanOrNull != null -> // boolean
                        getSetter<Boolean>(name)?.invoke(reciever, element.boolean)
                }
            } else if (element is JsonArray) {
                when (setterType) {
                    IntArray::class -> // int
                        getSetter<IntArray>(name)?.invoke(reciever,
                            element.map { it.jsonPrimitive.int }.toIntArray()
                        )

                    FloatArray::class -> // float
                        getSetter<FloatArray>(name)?.invoke(reciever,
                            element.map { it.jsonPrimitive.float }.toFloatArray()
                        )

                    DoubleArray::class -> // double
                        getSetter<DoubleArray>(name)?.invoke(reciever,
                            element.map { it.jsonPrimitive.double }.toDoubleArray()
                        )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getSetter(name: String): Setter<in B, in T>? {
        return setters[name] as? Setter<in B, in T>
    }
}

/**
 * @param id The ID for this post processor.
 * @param imageOp The [AbstractBufferedImageOp] being used for this [BufferedImageOpPostProcessor].
 * @param block A builder for the [ImageOpPropertyMap] used to convert `post_process.properties` to
 *              actual fields for the [imageOp]
 */
internal fun <T : AbstractBufferedImageOp> builtinPostProcessor(
    id: Identifier,
    imageOp: T,
    block: ImageOpPropertyMap<T>.() -> Unit
): BufferedImageOpPostProcessor<T> {
    val map = ImageOpPropertyMap<T>().apply(block)

    return BufferedImageOpPostProcessor(id, imageOp, map)
}