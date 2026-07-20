package xyz.mastriel.cutapi.resources.process.builtin

import com.jhlabs.image.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.data.*
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
    internal fun setValues(reciever: B, properties: Map<String, ResourceConfigValue>) {
        for ((name, element) in properties) {
            val setterType = getSetter<Any>(name)!!.parameters[1]
                .type.classifier ?: continue
            if (element is ResourceConfigScalar) {
                val number = element.value as? Number
                when {
                    setterType == Int::class && number != null ->
                        getSetter<Int>(name)?.invoke(reciever, number.toInt())

                    setterType == Float::class && number != null ->
                        getSetter<Float>(name)?.invoke(reciever, number.toFloat())

                    setterType == Double::class && number != null ->
                        getSetter<Double>(name)?.invoke(reciever, number.toDouble())

                    setterType == Long::class && number != null ->
                        getSetter<Long>(name)?.invoke(reciever, number.toLong())

                    setterType == Boolean::class && element.value is Boolean ->
                        getSetter<Boolean>(name)?.invoke(reciever, element.value)
                }
            } else if (element is ResourceConfigList) {
                val numbers = element.map { it.asConfigScalar().value as Number }
                when (setterType) {
                    IntArray::class ->
                        getSetter<IntArray>(name)?.invoke(
                            reciever,
                            numbers.map(Number::toInt).toIntArray()
                        )

                    FloatArray::class ->
                        getSetter<FloatArray>(name)?.invoke(
                            reciever,
                            numbers.map(Number::toFloat).toFloatArray()
                        )

                    DoubleArray::class ->
                        getSetter<DoubleArray>(name)?.invoke(
                            reciever,
                            numbers.map(Number::toDouble).toDoubleArray()
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
