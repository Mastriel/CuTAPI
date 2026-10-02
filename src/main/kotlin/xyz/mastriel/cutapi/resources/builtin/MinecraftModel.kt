@file:OptIn(ExperimentalSerializationApi::class)

package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import kotlinx.serialization.Serializable as KotlinSerializable
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

/** A JSON model stored in Minecraft's `assets/<namespace>/models` namespace. */
public open class MinecraftModel(
    override val ref: ResourceRef<MinecraftModel>,
    public val json: JsonObject,
    override val metadata: Metadata = Metadata(),
) : Resource(ref, metadata), ByteArraySerializable {

    public constructor(
        ref: ResourceRef<MinecraftModel>,
        data: MinecraftModelData,
        metadata: Metadata = Metadata(),
    ) : this(ref, data.toJsonObject(), metadata)

    /** Typed view for programmatic model construction; [json] remains authoritative. */
    public val data: MinecraftModelData by lazy {
        CuTApiJson.decodeFromJsonElement(json)
    }

    init {
        inspector.map("Textures") { data.textures }
    }

    public class Metadata : CuTMeta() {
        public companion object : Schema<Metadata> by schema(id("cutapi:minecraft_model"), {
            extends { CuTMeta }
            constructs { Metadata() }
        })
    }

    /** JSON ready for the pack, with CuTAPI resource refs projected to Minecraft IDs. */
    public fun toPackData(): JsonObject = JsonObject(json.toMutableMap().also { output ->
        (json["parent"] as? JsonPrimitive)?.let { parent ->
            if (parent.isString) output["parent"] = JsonPrimitive(parent.content.toMinecraftModelReference())
        }
        (json["textures"] as? JsonObject)?.let { textures ->
            output["textures"] = JsonObject(textures.mapValues { (_, value) ->
                val primitive = value as? JsonPrimitive
                    ?: throw IllegalArgumentException("Model texture entries must be strings in $ref.")
                JsonPrimitive(primitive.content.toMinecraftTextureReference())
            })
        }
    })

    override fun toBytes(): ByteArray = CuTApiJson.encodeToString(json).toByteArray(Charsets.UTF_8)
}

public val MinecraftModelResourceLoader: ResourceFileLoader<MinecraftModel> = resourceLoader(
    extensions = listOf("model.json"),
    metadataSchema = MinecraftModel.Metadata,
    dependencies = listOf(Texture2DResourceLoader),
) {
    try {
        val parsed = CuTApiJson.parseToJsonElement(dataAsString).jsonObject
        CuTApiJson.decodeFromJsonElement<MinecraftModelData>(parsed)
        success(MinecraftModel(ref, parsed, metadata ?: MinecraftModel.Metadata()))
    } catch (exception: Exception) {
        failure(exception)
    }
}

/** Converts a model resource ref to the identifier used inside a Minecraft pack. */
public fun ResourceRef<MinecraftModel>.toMinecraftModelLocator(): String =
    "$minecraftNamespace:${sanitizeResourcePath(path(withExtension = false))}"

private fun String.toMinecraftModelReference(): String = when {
    "://" !in this -> this
    else -> ref<MinecraftModel>(this).also {
        require(it.extension == "model.json") { "Minecraft model ref $it must end in .model.json." }
    }.toMinecraftModelLocator()
}

private fun String.toMinecraftTextureReference(): String = when {
    startsWith("#") || "://" !in this -> this
    else -> ref<Texture2D>(this).also {
        require(it.extension == "png") { "Texture ref $it must end in .png." }
    }.toMinecraftLocator()
}

@KotlinSerializable
public enum class MinecraftModelDisplayType {
    @SerialName("thirdperson_righthand") ThirdPersonRightHand,
    @SerialName("thirdperson_lefthand") ThirdPersonLeftHand,
    @SerialName("firstperson_righthand") FirstPersonRightHand,
    @SerialName("firstperson_lefthand") FirstPersonLeftHand,
    @SerialName("head") Head,
    @SerialName("gui") Gui,
    @SerialName("ground") Ground,
    @SerialName("fixed") Fixed,
}

@KotlinSerializable
public data class MinecraftModelDisplay(
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val scale: VoxelVector? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val rotation: VoxelVector? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val translation: VoxelVector? = null,
)

/** The complete top-level shape of a Minecraft 1.21.11 JSON model. */
@KotlinSerializable
public data class MinecraftModelData(
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val parent: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val textures: Map<String, String> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val elements: List<MinecraftModelElement> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("ambientocclusion") public val ambientOcclusion: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("gui_light") public val guiLight: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val display: Map<MinecraftModelDisplayType, MinecraftModelDisplay> = emptyMap(),
) {
    public fun toJsonObject(): JsonObject = CuTApiJson.encodeToJsonElement(this).jsonObject
}

@KotlinSerializable
public data class MinecraftModelElementFace(
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val uv: List<Float>? = null,
    public val texture: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val cullface: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val rotation: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val tintindex: Int? = null,
)

@KotlinSerializable
public data class MinecraftModelElement(
    public val from: VoxelVector,
    public val to: VoxelVector,
    public val faces: Map<String, MinecraftModelElementFace>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val rotation: MinecraftModelRotation? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val shade: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("light_emission") public val lightEmission: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val name: String? = null,
)

@KotlinSerializable
public enum class MinecraftModelRotationAxis {
    @SerialName("x") X,
    @SerialName("y") Y,
    @SerialName("z") Z,
}

/** Supports both the legacy axis/angle form and the 1.21.11 x/y/z rotation form. */
@KotlinSerializable
public data class MinecraftModelRotation(
    public val origin: VoxelVector,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val axis: MinecraftModelRotationAxis? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val angle: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val x: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val y: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val z: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val rescale: Boolean? = null,
)

@KotlinSerializable(with = VoxelVector.Serializer::class)
public data class VoxelVector(public val x: Float, public val y: Float, public val z: Float) {
    public data object Serializer : KSerializer<VoxelVector> {
        override val descriptor: SerialDescriptor = SerialDescriptor(
            serialName = "VoxelPosition",
            original = ListSerializer(Float.serializer()).descriptor,
        )

        override fun deserialize(decoder: Decoder): VoxelVector {
            val list = ListSerializer(Float.serializer()).deserialize(decoder)
            require(list.size == 3) { "A voxel vector must contain exactly three values." }
            return VoxelVector(list[0], list[1], list[2])
        }

        override fun serialize(encoder: Encoder, value: VoxelVector) {
            encoder.encodeSerializableValue(ListSerializer(Float.serializer()), listOf(value.x, value.y, value.z))
        }
    }
}
