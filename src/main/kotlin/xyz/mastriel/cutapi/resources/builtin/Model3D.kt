@file:OptIn(ExperimentalSerializationApi::class)

package xyz.mastriel.cutapi.resources.builtin

import kotlinx.serialization.*
import kotlinx.serialization.Serializable as KotlinSerializable
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import xyz.mastriel.cutapi.*
import xyz.mastriel.cutapi.block.*
import xyz.mastriel.cutapi.data.*
import xyz.mastriel.cutapi.registry.*
import xyz.mastriel.cutapi.resources.*
import xyz.mastriel.cutapi.resources.data.*

public open class Model3D(
    override val ref: ResourceRef<Model3D>,
    public val modelJson: Model3DJsonStructure,
    override val metadata: Metadata
) : Resource(ref), TextureLike, ByteArraySerializable {

    init {
        inspector.single("Materials") { materials.joinToString() }
        inspector.single("Block Strategies") { metadata.blockStrategies.joinToString() }
        inspector.map("Textures") { metadata.textures.mapValues { (_, v) -> v.toString() } }
    }

    public data class Metadata(
        val blockStrategies: List<AllowedBlockStrategy> = AllowedBlockStrategy.entries.toList(),
        val materials: List<String> = listOf(),
        val textures: Map<String, ResourceRef<Texture2D>> = mapOf(),
        val oversizedInGui: Boolean = false,
    ) : CuTMeta() {
        public companion object : Schema<Metadata> by schema(id("cutapi:model3d"), {
            extends { CuTMeta }
            property(Metadata::blockStrategies, VariantSerializer.ListOf(VariantSerializer.Enum<AllowedBlockStrategy>())) {
                optional(omitDefaults = true) { AllowedBlockStrategy.entries.toList() }
            }
            property(Metadata::materials, VariantSerializer.ListOf(VariantSerializer.String)) {
                optional(omitDefaults = true) { emptyList() }
            }
            property(Metadata::textures, VariantSerializer.MapOf(VariantSerializer.ResourceRef<Texture2D>())) {
                optional(omitDefaults = true) { emptyMap() }
            }
            property(Metadata::oversizedInGui, VariantSerializer.Boolean) {
                optional(omitDefaults = true) { false }
            }
        })
    }


    override fun createItemModelData(): JsonObject = modelJson.toJsonObject()

    override fun getItemModel(): VanillaItemModel {
        return VanillaItemModel(
            "${ref.namespace}:${
                ref.path(
                    withExtension = false,
                    withNamespaceAsFolder = false,
                    fixInvalids = true
                )
            }"
        )
    }

    override val materials: List<String>
        get() = metadata.materials

    override val oversizedInGui: Boolean
        get() = metadata.oversizedInGui

    override val resource: Resource
        get() = this

    override fun toBytes(): ByteArray {
        return CuTAPI.json.encodeToString(modelJson).toByteArray(Charsets.UTF_8)
    }
}


public val Model3DResourceLoader: ResourceFileLoader<Model3D> = resourceLoader(
    extensions = listOf("model3d.json"),
    metadataSchema = Model3D.Metadata,
    // we need to know how to remap the textures.
    dependencies = listOf(Texture2DResourceLoader),
) {
    try {
        val metadata = metadata ?: Model3D.Metadata().also { Plugin.warn("No metadata for $ref") }
        val jsonObject = CuTAPI.json.decodeFromString<JsonObject>(dataAsString)

        val textures = jsonObject["textures"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        for ((key, location) in metadata.textures) {
            textures[key] = JsonPrimitive(location.toMinecraftLocator())
        }
        val json = jsonObject.toMutableMap()
        json["textures"] = JsonObject(textures)

        val structure = CuTAPI.json.decodeFromJsonElement<Model3DJsonStructure>(JsonObject(json))
        success(Model3D(ref, structure, metadata))
    } catch (ex: Exception) {
        failure(ex)
    }
}

@KotlinSerializable
public enum class Model3DDisplayType {
    @SerialName("thirdperson_righthand")
    ThirdPersonRightHand,

    @SerialName("thirdperson_lefthand")
    ThirdPersonLeftHand,

    @SerialName("firstperson_righthand")
    FirstPersonRightHand,

    @SerialName("firstperson_lefthand")
    FirstPersonLeftHand,

    @SerialName("head")
    Head,

    @SerialName("gui")
    Gui,

    @SerialName("ground")
    Ground,

    @SerialName("fixed")
    Fixed
}

@KotlinSerializable
public data class Model3DDisplay(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val scale: VoxelVector? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val rotation: VoxelVector? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val translation: VoxelVector? = null,
)

@KotlinSerializable
public data class Model3DJsonStructure(
    public val textures: Map<String, String>,
    public val elements: List<Model3DElement> = listOf(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("gui_light")
    public val guiLight: String? = null,
    public val display: Map<Model3DDisplayType, Model3DDisplay> = mapOf()
) {
    public fun toJsonObject(): JsonObject {
        return CuTAPI.json.encodeToJsonElement(this).jsonObject
    }
}

@KotlinSerializable
public data class Model3DCubeFaces(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val north: Model3DElementFace? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val east: Model3DElementFace? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val south: Model3DElementFace? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val west: Model3DElementFace? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val up: Model3DElementFace? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val down: Model3DElementFace? = null
)

@KotlinSerializable
public data class Model3DElementFace(
    public val uv: List<Float>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val texture: JsonPrimitive? = null
)

@KotlinSerializable
public data class Model3DElement(
    public val from: VoxelVector,
    public val to: VoxelVector,
    public val faces: Map<String, Model3DElementFace>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val rotation: Model3DRotation? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    public val name: String? = null
)

@KotlinSerializable
public enum class Model3DRotationAxis {
    @SerialName("x")
    X,

    @SerialName("y")
    Y,

    @SerialName("z")
    Z
}

@KotlinSerializable
public data class Model3DRotation(
    public val origin: VoxelVector,
    public val axis: Model3DRotationAxis,
    public val angle: Float
)


@KotlinSerializable(with = VoxelVector.Serializer::class)
public data class VoxelVector(
    public val x: Float,
    public val y: Float,
    public val z: Float
) {
    public data object Serializer : KSerializer<VoxelVector> {
        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor = SerialDescriptor(
            serialName = "VoxelPosition",
            original = ListSerializer(
                Float.serializer()
            ).descriptor
        )

        override fun deserialize(decoder: Decoder): VoxelVector {
            val list = ListSerializer(Float.serializer()).deserialize(decoder)
            return VoxelVector(list[0], list[1], list[2])
        }

        override fun serialize(encoder: Encoder, value: VoxelVector) {
            encoder.encodeSerializableValue(
                ListSerializer(Float.serializer()),
                listOf(value.x, value.y, value.z)
            )
        }

    }
}


public enum class AllowedBlockStrategy(public val strategy: BlockStrategy) {
    NoteBlock(BlockStrategy.NoteBlock),
    Mushroom(BlockStrategy.Mushroom),
    FakeEntity(BlockStrategy.FakeEntity)
}
