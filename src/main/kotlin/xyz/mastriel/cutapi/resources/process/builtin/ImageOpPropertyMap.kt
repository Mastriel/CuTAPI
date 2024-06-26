package xyz.mastriel.cutapi.resources.process.builtin

import com.jhlabs.image.*
import net.peanuuutz.tomlkt.*
import xyz.mastriel.cutapi.registry.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * @param R The reciever.
 * @param T The type of the property.
 */
private typealias Setter<R, T> = KFunction2<R, T, Unit>

/**
 * A map of [AbstractBufferedImageOp]'s setter methods and `post_process.properties` fields in a texture's .cutmeta,
 * to create easy definitions for porting [com.jhlabs.image] for use in `post_process`.
 */
public class ImageOpPropertyMap<B : AbstractBufferedImageOp> internal constructor() {
    private val setters = hashMapOf<String, Setter<*, *>>()

    /**
     * Adds a property definition to this map using a name, and a setter.
     *
     * @param name The property name used in `post_process.properties`
     * @param setter The setter for a property for this [AbstractBufferedImageOp]
     */
    public fun <R : AbstractBufferedImageOp, T : Any> property(
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
    internal fun setValues(reciever: B, properties: Map<String, TomlElement>) {
        for ((name, element) in properties) {
            val setterType = getSetter<Any>(name)!!.parameters[1]
                .type.classifier ?: continue
            if (element is TomlLiteral) {
                when {
                    setterType == Int::class && element.toIntOrNull() != null -> // int
                        getSetter<Int>(name)?.invoke(reciever, element.toInt())

                    setterType == Float::class && element.toFloatOrNull() != null -> // float
                        getSetter<Float>(name)?.invoke(reciever, element.toFloat())

                    setterType == Double::class && element.toDoubleOrNull() != null -> // double
                        getSetter<Double>(name)?.invoke(reciever, element.toDouble())

                    setterType == Long::class && element.toLongOrNull() != null -> // long
                        getSetter<Long>(name)?.invoke(reciever, element.toLong())

                    setterType == Boolean::class && element.toBooleanOrNull() != null -> // boolean
                        getSetter<Boolean>(name)?.invoke(reciever, element.toBoolean())
                }
            } else if (element is TomlArray) {
                when (setterType) {
                    IntArray::class -> // int
                        getSetter<IntArray>(name)?.invoke(
                            reciever,
                            element.map { it.asTomlLiteral().toInt() }.toIntArray()
                        )

                    FloatArray::class -> // float
                        getSetter<FloatArray>(name)?.invoke(
                            reciever,
                            element.map { it.asTomlLiteral().toFloat() }.toFloatArray()
                        )

                    DoubleArray::class -> // double
                        getSetter<DoubleArray>(name)?.invoke(
                            reciever,
                            element.map { it.asTomlLiteral().toDouble() }.toDoubleArray()
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