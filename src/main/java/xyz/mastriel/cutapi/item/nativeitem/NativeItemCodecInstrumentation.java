package xyz.mastriel.cutapi.item.nativeitem;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.utility.JavaModule;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import xyz.mastriel.cutapi.item.nativeitem.NativeItemClientBridge;
import xyz.mastriel.cutapi.item.nativeitem.advice.ItemHolderEncodeAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.ItemStackBackingIdentityAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.ItemStackDecodeAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.ItemStackEncodeAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.ItemTagPayloadAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.RegistryCodecEncodeAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.BlockStateIdAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.BlockPaletteSizeAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.ChunkBlockStateProjectionAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.FixedSizeLongArrayAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.GlobalBlockPaletteWriteAdvice;
import xyz.mastriel.cutapi.item.nativeitem.advice.StartupPluginFinalizerAdvice;
import xyz.mastriel.cutapi.block.nativeblock.NativeBlockClientBridge;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

/** Installs the codec bridge during Paper bootstrap, before network traffic can begin. */
public final class NativeItemCodecInstrumentation {
    private static final String HOOK_CLASS = "xyz.mastriel.cutapi.injected.NativeItemCodecHooks";
    private static final Set<String> EXPECTED_TYPES = Set.of(
        "net.minecraft.world.item.ItemStack",
        "net.minecraft.world.item.ItemStack$2",
        "net.minecraft.network.chat.ComponentSerialization$1",
        "net.minecraft.network.codec.ByteBufCodecs$34",
        "net.minecraft.tags.TagNetworkSerialization",
        "net.minecraft.core.IdMapper",
        "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData",
        "net.minecraft.world.level.chunk.PalettedContainer$Data",
        "net.minecraft.network.FriendlyByteBuf",
        "org.bukkit.craftbukkit.CraftServer"
    );
    private static final Set<String> transformedTypes = ConcurrentHashMap.newKeySet();
    private static final Map<String, Throwable> transformationErrors = new ConcurrentHashMap<>();
    private static boolean installed;

    private NativeItemCodecInstrumentation() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }

        var instrumentation = ByteBuddyAgent.install();
        var hookClass = injectHookClass();
        if (!(boolean) invoke(hookClass, "claimInstrumentation", new Class<?>[0])) {
            installed = true;
            return;
        }

        // Loading an as-yet undefined target while a retransformation-capable transformer is being
        // installed can make Paper's shared URLClassLoader attempt to define that target twice.
        // Resolve every target first, then let installOn retransform this stable loaded set.
        for (String typeName : EXPECTED_TYPES) {
            try {
                Class.forName(typeName, false, ItemStack.class.getClassLoader());
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException("Required native item codec type is missing: " + typeName, exception);
            }
        }

        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new AgentBuilder.Listener.Adapter() {
                @Override
                public void onTransformation(
                    TypeDescription typeDescription,
                    ClassLoader classLoader,
                    JavaModule module,
                    boolean loaded,
                    DynamicType dynamicType
                ) {
                    transformedTypes.add(typeDescription.getName());
                }

                @Override
                public void onError(
                    String typeName,
                    ClassLoader classLoader,
                    JavaModule module,
                    boolean loaded,
                    Throwable throwable
                ) {
                    transformationErrors.put(typeName, throwable);
                }
            })
            .type(named("net.minecraft.world.item.ItemStack"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(ItemStackBackingIdentityAdvice.class).on(
                    named("is")
                        .and(takesArguments(Item.class))
                        .and(returns(boolean.class)))))
            .type(named("net.minecraft.world.item.ItemStack$2"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(ItemStackEncodeAdvice.class).on(
                    named("encode").and(takesArguments(RegistryFriendlyByteBuf.class, ItemStack.class))))
                .visit(Advice.to(ItemStackDecodeAdvice.class).on(
                    named("decode").and(takesArguments(RegistryFriendlyByteBuf.class)))))
            .type(named("net.minecraft.network.codec.ByteBufCodecs$34"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(ItemHolderEncodeAdvice.class).on(
                    named("encode").and(takesArguments(RegistryFriendlyByteBuf.class, Holder.class)))))
            .type(named("net.minecraft.network.chat.ComponentSerialization$1"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(RegistryCodecEncodeAdvice.class).on(
                    named("encode")
                        .and(takesArguments(2))
                        .and(takesArgument(0, RegistryFriendlyByteBuf.class))
                        .and(takesArgument(1, net.minecraft.network.chat.Component.class)))))
            .type(named("net.minecraft.tags.TagNetworkSerialization"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(ItemTagPayloadAdvice.class).on(
                    named("serializeToNetwork").and(takesArguments(net.minecraft.core.Registry.class)))))
            .type(named("net.minecraft.core.IdMapper"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(BlockStateIdAdvice.class).on(
                    named("getId").and(takesArguments(Object.class)))))
            .type(named("net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(ChunkBlockStateProjectionAdvice.class).on(
                    named("calculateChunkSize").and(takesArguments(
                        net.minecraft.world.level.chunk.LevelChunk.class
                    ))))
                .visit(Advice.to(ChunkBlockStateProjectionAdvice.class).on(
                    named("extractChunkData").and(takesArguments(
                        net.minecraft.network.FriendlyByteBuf.class,
                        net.minecraft.world.level.chunk.LevelChunk.class,
                        io.papermc.paper.antixray.ChunkPacketInfo.class
                    )))))
            .type(named("net.minecraft.world.level.chunk.PalettedContainer$Data"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(BlockPaletteSizeAdvice.class).on(
                    named("getSerializedSize").and(takesArguments(net.minecraft.core.IdMap.class))))
                .visit(Advice.to(GlobalBlockPaletteWriteAdvice.class).on(
                    named("write").and(takesArguments(
                        net.minecraft.network.FriendlyByteBuf.class,
                        net.minecraft.core.IdMap.class,
                        io.papermc.paper.antixray.ChunkPacketInfo.class,
                        int.class
                    )))))
            .type(named("net.minecraft.network.FriendlyByteBuf"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(FixedSizeLongArrayAdvice.class).on(
                    named("writeFixedSizeLongArray").and(takesArguments(long[].class)))))
            .type(named("org.bukkit.craftbukkit.CraftServer"))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(StartupPluginFinalizerAdvice.class).on(
                    named("enablePlugins").and(takesArguments(1)))))
            .installOn(instrumentation);

        if (!transformationErrors.isEmpty()) {
            var first = transformationErrors.entrySet().iterator().next();
            throw new IllegalStateException("Failed to instrument " + first.getKey(), first.getValue());
        }
        if (!transformedTypes.containsAll(EXPECTED_TYPES)) {
            var missing = EXPECTED_TYPES.stream().filter(type -> !transformedTypes.contains(type)).toList();
            throw new IllegalStateException("Native item codec instrumentation did not transform " + missing);
        }

        installed = true;
    }

    public static synchronized void bindStartupFinalizer(Runnable finalizer) {
        invoke(
            loadHookClass(),
            "bindStartupPluginFinalizer",
            new Class<?>[]{Runnable.class},
            finalizer
        );
    }

    public static synchronized void bind() {
        var hookClass = loadHookClass();
        Function<ItemStack, ItemStack> encoder = NativeItemClientBridge::encode;
        Function<ItemStack, ItemStack> decoder = NativeItemClientBridge::decode;
        Function<Holder<Item>, Holder<Item>> holder = NativeItemClientBridge::encodeHolder;
        BiConsumer<Registry<?>, TagNetworkSerialization.NetworkPayload> tags = NativeItemClientBridge::projectTags;
        BiFunction<RegistryFriendlyByteBuf, net.minecraft.network.chat.Component, net.minecraft.network.chat.Component>
            components = NativeItemClientBridge::encodeComponent;
        BiPredicate<Item, Item> backingItems = (actual, expected) ->
            actual instanceof NativeBackedItem backed && backed.getBacking() == expected;
        Function<Object, Object> blockStates = NativeBlockClientBridge::encodeState;
        Function<Object, Object> projectedBlockStates = NativeBlockClientBridge::projectValue;
        int vanillaBlockStateCount = xyz.mastriel.cutapi.block.nativeblock.NativeBlockRegistry.INSTANCE.getVanillaBlockStateCount$CuTAPI();
        invoke(
            hookClass,
            "bind",
            new Class<?>[]{
                Function.class,
                Function.class,
                Function.class,
                BiConsumer.class,
                BiFunction.class,
                BiPredicate.class,
                Function.class,
                Function.class,
                int.class
            },
            encoder,
            decoder,
            holder,
            tags,
            components,
            backingItems,
            blockStates,
            projectedBlockStates,
            vanillaBlockStateCount
        );
    }

    public static synchronized void clear() {
        invoke(loadHookClass(), "clear", new Class<?>[0]);
    }

    private static Class<?> injectHookClass() {
        var minecraftClassLoader = ItemStack.class.getClassLoader();
        try {
            return Class.forName(HOOK_CLASS, false, minecraftClassLoader);
        } catch (ClassNotFoundException ignored) {
            // Inject below.
        }

        var resourceName = "/" + HOOK_CLASS.replace('.', '/') + ".class";
        try (InputStream input = NativeItemCodecInstrumentation.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalStateException("Missing injected native item hook resource " + resourceName);
            }
            var injected = new ClassInjector.UsingUnsafe(
                minecraftClassLoader,
                ItemStack.class.getProtectionDomain()
            )
                .injectRaw(Map.of(HOOK_CLASS, input.readAllBytes()));
            return injected.get(HOOK_CLASS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not inject native item codec hooks", exception);
        }
    }

    private static Class<?> loadHookClass() {
        try {
            return Class.forName(HOOK_CLASS, true, ItemStack.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Native item codec hooks were not installed during bootstrap", exception);
        }
    }

    private static Object invoke(Class<?> target, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = target.getMethod(name, parameterTypes);
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not call native item codec hook " + name, exception);
        }
    }
}
