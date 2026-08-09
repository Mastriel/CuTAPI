package xyz.mastriel.cutapi.injected;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Injected into the system classloader so instrumented Minecraft classes never link directly to
 * CuTAPI's plugin classloader. Delegates are installed by the runtime plugin during onEnable.
 */
public final class NativeItemCodecHooks {
    private static boolean instrumentationInstalled;
    private static volatile Function<ItemStack, ItemStack> stackEncoder;
    private static volatile Function<ItemStack, ItemStack> stackDecoder;
    private static volatile Function<Holder<Item>, Holder<Item>> holderEncoder;
    private static volatile BiConsumer<Registry<?>, TagNetworkSerialization.NetworkPayload> tagProjector;
    private static volatile BiFunction<RegistryFriendlyByteBuf, Component, Component> componentEncoder;

    private NativeItemCodecHooks() {
    }

    public static synchronized boolean claimInstrumentation() {
        if (instrumentationInstalled) {
            return false;
        }
        instrumentationInstalled = true;
        return true;
    }

    public static void bind(
        Function<ItemStack, ItemStack> encoder,
        Function<ItemStack, ItemStack> decoder,
        Function<Holder<Item>, Holder<Item>> holder,
        BiConsumer<Registry<?>, TagNetworkSerialization.NetworkPayload> tags,
        BiFunction<RegistryFriendlyByteBuf, Component, Component> components
    ) {
        stackEncoder = encoder;
        stackDecoder = decoder;
        holderEncoder = holder;
        tagProjector = tags;
        componentEncoder = components;
    }

    public static void clear() {
        stackEncoder = null;
        stackDecoder = null;
        holderEncoder = null;
        tagProjector = null;
        componentEncoder = null;
    }

    public static ItemStack encodeStack(ItemStack stack) {
        var encoder = stackEncoder;
        return encoder == null ? stack : encoder.apply(stack);
    }

    public static ItemStack decodeStack(ItemStack stack) {
        var decoder = stackDecoder;
        return decoder == null ? stack : decoder.apply(stack);
    }

    public static Holder<Item> encodeHolder(Holder<Item> holder) {
        var encoder = holderEncoder;
        return encoder == null ? holder : encoder.apply(holder);
    }

    public static void projectTags(Registry<?> registry, TagNetworkSerialization.NetworkPayload payload) {
        var projector = tagProjector;
        if (projector != null) {
            projector.accept(registry, payload);
        }
    }

    public static Component encodeComponent(RegistryFriendlyByteBuf buffer, Component component) {
        var encoder = componentEncoder;
        return encoder == null ? component : encoder.apply(buffer, component);
    }
}
