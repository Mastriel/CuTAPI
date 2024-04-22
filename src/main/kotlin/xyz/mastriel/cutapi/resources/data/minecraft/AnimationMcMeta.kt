package xyz.mastriel.cutapi.resources.data.minecraft

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnimationMcMeta(
    val animation: Animation
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class Animation(
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
data class AnimationFrame(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val index: Int,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val time: Int
)