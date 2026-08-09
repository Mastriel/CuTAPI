package xyz.mastriel.cutapi.injected;

import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Injected into the system classloader so instrumented Minecraft classes never link directly to
 * CuTAPI's plugin classloader. Delegates are installed by the runtime plugin during onEnable.
 */
public final class NativeItemCodecHooks implements IdMap<Object> {
    private static boolean instrumentationInstalled;
    private static volatile Function<ItemStack, ItemStack> stackEncoder;
    private static volatile Function<ItemStack, ItemStack> stackDecoder;
    private static volatile Function<Holder<Item>, Holder<Item>> holderEncoder;
    private static volatile BiConsumer<Registry<?>, TagNetworkSerialization.NetworkPayload> tagProjector;
    private static volatile BiFunction<RegistryFriendlyByteBuf, Component, Component> componentEncoder;
    private static volatile Function<Object, Object> blockStateEncoder;
    private static volatile Function<Object, Object> blockStateProjector;
    private static volatile int clientBlockStateCount = -1;
    private static final ThreadLocal<Integer> blockPacketProjectionDepth = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> rawBlockStateIdLookup = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<long[]> globalBlockPaletteProjection = new ThreadLocal<>();
    private static final NativeItemCodecHooks projectingBlockStateIdMap = new NativeItemCodecHooks();

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
        BiFunction<RegistryFriendlyByteBuf, Component, Component> components,
        Function<Object, Object> blockStates,
        Function<Object, Object> projectedBlockStates,
        int vanillaBlockStateCount
    ) {
        stackEncoder = encoder;
        stackDecoder = decoder;
        holderEncoder = holder;
        tagProjector = tags;
        componentEncoder = components;
        blockStateEncoder = blockStates;
        blockStateProjector = projectedBlockStates;
        clientBlockStateCount = vanillaBlockStateCount;
    }

    public static void clear() {
        stackEncoder = null;
        stackDecoder = null;
        holderEncoder = null;
        tagProjector = null;
        componentEncoder = null;
        blockStateEncoder = null;
        blockStateProjector = null;
        clientBlockStateCount = -1;
        blockPacketProjectionDepth.remove();
        rawBlockStateIdLookup.remove();
        globalBlockPaletteProjection.remove();
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

    public static Object encodeBlockState(Object state) {
        if (rawBlockStateIdLookup.get()) {
            return state;
        }
        if (blockPacketProjectionDepth.get() > 0) {
            return projectClientBlockState(state);
        }
        var encoder = blockStateEncoder;
        return encoder == null ? state : encoder.apply(state);
    }

    public static void beginBlockPacketProjection() {
        blockPacketProjectionDepth.set(blockPacketProjectionDepth.get() + 1);
    }

    /**
     * Wraps the block-state ID map at the palette call boundary. Palette implementations invoke
     * the {@link IdMap} interface directly, so projecting here covers implementations that do not
     * dispatch through the concrete {@code IdMapper.getId} method.
     */
    public static IdMap<?> projectBlockPaletteRegistry(IdMap<?> registry) {
        return blockPacketProjectionDepth.get() > 0 &&
            registry == Block.BLOCK_STATE_REGISTRY &&
            blockStateProjector != null
            ? projectingBlockStateIdMap
            : registry;
    }

    public static void endBlockPacketProjection() {
        int depth = blockPacketProjectionDepth.get() - 1;
        if (depth <= 0) {
            blockPacketProjectionDepth.remove();
        } else {
            blockPacketProjectionDepth.set(depth);
        }
    }

    /** Projects the raw registry IDs used when a block-state section has outgrown local palettes. */
    public static void beginBlockPaletteWrite(IdMap<?> registry, Palette<?> palette, BitStorage storage) {
        globalBlockPaletteProjection.remove();
        if (blockPacketProjectionDepth.get() <= 0 ||
            registry != Block.BLOCK_STATE_REGISTRY ||
            !(palette instanceof GlobalPalette<?>) ||
            blockStateProjector == null) {
            return;
        }

        globalBlockPaletteProjection.set(projectGlobalBlockPalette(storage));
    }

    static long[] projectGlobalBlockPalette(BitStorage storage) {
        var projectedStorage = new SimpleBitStorage(
            storage.getBits(),
            storage.getSize(),
            storage.getRaw().clone()
        );
        for (int index = 0; index < storage.getSize(); index++) {
            BlockState authoritative = Block.BLOCK_STATE_REGISTRY.byId(storage.get(index));
            if (authoritative == null) {
                throw new IllegalStateException("Global block palette contained an unknown server state ID");
            }
            Object projectedValue = projectClientBlockState(authoritative);
            if (!(projectedValue instanceof BlockState projected)) {
                throw new IllegalStateException("Block-state projector returned " + projectedValue);
            }
            int projectedId = rawBlockStateId(projected);
            if (projectedId < 0) {
                throw new IllegalStateException("Projected block state is absent from the state registry: " + projected);
            }
            projectedStorage.set(index, projectedId);
        }
        return projectedStorage.getRaw();
    }

    public static long[] projectFixedSizeLongArray(long[] values) {
        long[] projection = globalBlockPaletteProjection.get();
        return projection == null ? values : projection;
    }

    public static void endBlockPaletteWrite() {
        globalBlockPaletteProjection.remove();
    }

    private static Object projectClientBlockState(Object value) {
        var projector = blockStateProjector;
        Object projected = projector == null ? value : projector.apply(value);
        if (projected == value && value instanceof BlockState state) {
            int id = rawBlockStateId(state);
            if (clientBlockStateCount >= 0 && id >= clientBlockStateCount) {
                throw new IllegalStateException(
                    "Native block state " + state + " (wire ID " + id + ") has no client visual projection " +
                        "within the vanilla state count " + clientBlockStateCount
                );
            }
        }
        return projected;
    }

    @Override
    public int getId(Object value) {
        Object projected = projectClientBlockState(value);
        if (!(projected instanceof BlockState state)) {
            throw new IllegalStateException("Block-state projector returned " + projected);
        }
        return rawBlockStateId(state);
    }

    private static int rawBlockStateId(BlockState state) {
        boolean previous = rawBlockStateIdLookup.get();
        rawBlockStateIdLookup.set(true);
        try {
            return Block.BLOCK_STATE_REGISTRY.getId(state);
        } finally {
            if (previous) {
                rawBlockStateIdLookup.set(true);
            } else {
                rawBlockStateIdLookup.remove();
            }
        }
    }

    @Override
    public Object byId(int id) {
        return Block.BLOCK_STATE_REGISTRY.byId(id);
    }

    @Override
    public int size() {
        return Block.BLOCK_STATE_REGISTRY.size();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Iterator<Object> iterator() {
        return (Iterator) Block.BLOCK_STATE_REGISTRY.iterator();
    }
}
