# Resource pack resources

CuTAPI selects the pack format internally. Currently all packs use format 75 for Minecraft 1.21.11; there is no `pack-version` configuration setting.

CuTAPI keeps one resource system, but textures, Minecraft models, and client item definitions are separate resource types. Folders remain entirely project-defined; the compound extension identifies the type.

```yaml
Texture2D:      my_plugin://item/hammer.png
MinecraftModel: my_plugin://item/hammer.model.json
ItemModel:      my_plugin://item/hammer.item_model.json
```

Pack generation removes the complete CuTAPI type extension and projects each runtime type into its Minecraft directory:

```text
my_plugin://item/hammer.png             -> assets/my_plugin/textures/item/hammer.png
my_plugin://item/hammer.model.json      -> assets/my_plugin/models/item/hammer.json
my_plugin://item/hammer.item_model.json -> assets/my_plugin/items/item/hammer.json
```

The logical path is not required to begin with `item/`, `block/`, or any other folder. Those names are conventions a plugin may choose.

## Generate the common item chain

A texture can explicitly request the two resources normally needed to render an item:

```yaml
!<cutapi:texture2d>
generate:
  - !<cutapi:texture_model>
    generate:
      - !<cutapi:item_model> {}
```

For `my_plugin://item/hammer.png`, this registers exactly:

```text
my_plugin://item/hammer.png
my_plugin://item/hammer.model.json
my_plugin://item/hammer.item_model.json
```

`cutapi:texture_model` defaults to `minecraft:item/generated` and supplies the source texture as `textures.layer0`. Its remaining fields are model JSON fields, written with Minecraft names. Maps merge recursively, while lists and scalar values replace their defaults.

```yaml
!<cutapi:texture2d>
generate:
  - !<cutapi:texture_model>
    parent: minecraft:item/handheld
    gui_light: front
    textures:
      layer1: my_plugin://item/hammer_overlay.png
    display:
      gui:
        rotation: [30, 225, 0]
    generate:
      - !<cutapi:item_model>
        hand_animation_on_swap: false
        oversized_in_gui: true
        swap_animation_scale: 1.5
```

The source `layer0` may be overridden deliberately:

```yaml
!<cutapi:texture2d>
generate:
  - !<cutapi:texture_model>
    textures:
      layer0: my_plugin://item/alternate_hammer.png
```

A `subId` is inserted before the destination compound extension. `output` instead names the complete destination; the two controls cannot be combined.

```yaml
generate:
  - !<cutapi:texture_model>
    subId: large
```

```text
my_plugin://item/hammer.png -> my_plugin://item/hammer^large.model.json
```

```yaml
generate:
  - !<cutapi:texture_model>
    output: my_plugin://generated/large_hammer.model.json
```

## Author resources directly

An authored model is ordinary Minecraft model JSON. CuTAPI refs are allowed where Minecraft expects model or texture IDs and are converted only while writing the pack.

`item/hammer.model.json`:

```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "my_plugin://item/hammer.png"
  }
}
```

`item/hammer.model.json.meta`:

```yaml
!<cutapi:minecraft_model>
generate:
  - !<cutapi:item_model>
    hand_animation_on_swap: false
```

An item definition can also be authored without a generator.

`item/hammer.item_model.json`:

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "my_plugin://item/hammer.model.json"
  },
  "hand_animation_on_swap": false
}
```

`ItemModel` supports the Minecraft 1.21.11 node families: `model`, `special`, `composite`, `condition`, `select`, `range_dispatch`, `empty`, `bundle/selected_item`, and `spear_in_hand`. Vanilla JSON remains authoritative, so fields unknown to CuTAPI are preserved rather than discarded.

## Access resources from Kotlin

References retain their resource type:

```kotlin
val hammerTexture = ref<Texture2D>(plugin, "item/hammer.png")
val hammerModel = ref<MinecraftModel>(plugin, "item/hammer.model.json")
val hammerItemModel = ref<ItemModel>(plugin, "item/hammer.item_model.json")

val texture: Texture2D = requireNotNull(hammerTexture.getResource())
val model: MinecraftModel = requireNotNull(hammerModel.getResource())
val itemDefinition: ItemModel = requireNotNull(hammerItemModel.getResource())
```

Generated references are deterministic and do not require the resources to be loaded first:

```kotlin
val texture = ref<Texture2D>(plugin, "item/hammer.png")
val model = texture.generatedMinecraftModelRef()
val itemModel = model.generatedItemModelRef()
val largeModel = texture.generatedMinecraftModelRef(subId = "large")
```

Item displays consume the final item definition directly:

```kotlin
itemDescriptor {
    display {
        itemModel = ref(plugin, "item/hammer.item_model.json")
    }
}
```

Programmatic resources use the same boundaries:

```kotlin
val model = MinecraftModel(
    ref = ref(plugin, "item/hammer.model.json"),
    data = MinecraftModelData(
        parent = "minecraft:item/handheld",
        textures = mapOf("layer0" to "my_plugin://item/hammer.png"),
    ),
)

val itemModel = ItemModel(
    ref = ref(plugin, "item/hammer.item_model.json"),
    data = ItemModelData(
        model = ModelItemModelNode(model.ref).toJson(),
        handAnimationOnSwap = false,
    ),
)

CuTAPI.resourceManager.register(model)
CuTAPI.resourceManager.register(itemModel)
```

## Breaking migration

1. Rename `*.model3d.json` to `*.model.json` and change the metadata tag from `cutapi:model3d` to `cutapi:minecraft_model`.
2. Move texture `itemModelData`, `modelFile`, and material-driven model generation into `cutapi:texture_model` blocks.
3. Move `oversizedInGui`, swap behavior, and composite backgrounds into an explicit `ItemModel` or `cutapi:item_model` block.
4. Replace `ItemTexture`, `itemTexture(...)`, and model-based `itemModel(...)` calls with `ResourceRef<ItemModel>` assigned to `ItemDisplayBuilder.itemModel`.
5. Use snake_case Minecraft field names in generator YAML. There are no compatibility aliases or legacy runtime loaders.
