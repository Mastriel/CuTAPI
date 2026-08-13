package xyz.mastriel.cutapi.resources.data.minecraft

import kotlinx.serialization.*
import kotlinx.serialization.Serializable as KotlinSerializable
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*

@KotlinSerializable
public data class AnimationMcMeta(
    val animation: Animation
)

@KotlinSerializable
@OptIn(ExperimentalSerializationApi::class)
public data class Animation(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val interpolate: Boolean? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val width: Int? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val height: Int? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val frametime: Int? = null,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val frames: List<AnimationFrame> = listOf()
) {
    public companion object : Schema<Animation> by schema(id("cutapi:resource/animation"), {
        untagged = true
        property(Animation::interpolate, VariantSerializer.Boolean.nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(Animation::width, VariantSerializer.Int.nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(Animation::height, VariantSerializer.Int.nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(Animation::frametime, VariantSerializer.Int.nullable()) {
            optional(omitDefaults = true) { null }
        }
        property(Animation::frames, VariantSerializer.ListOf(AnimationFrame)) {
            optional(omitDefaults = true) { emptyList() }
        }
    })
}

@KotlinSerializable
@OptIn(ExperimentalSerializationApi::class)
public data class AnimationFrame(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val index: Int,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val time: Int
) {
    public companion object : Schema<AnimationFrame> by schema(id("cutapi:resource/animation_frame"), {
        untagged = true
        property(AnimationFrame::index, VariantSerializer.Int)
        property(AnimationFrame::time, VariantSerializer.Int)
    })
}

