package xyz.mastriel.cutapi.resources.data.minecraft

import kotlinx.serialization.*

@Serializable
public data class AnimationMcMeta(
    val animation: Animation
)

@Serializable
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
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class AnimationFrame(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val index: Int,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val time: Int
)

