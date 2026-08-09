package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

/**
 * Marks both halves of eager chunk-section serialization as client projection work.
 *
 * <p>Minecraft allocates an exact-size, non-growing buffer before writing the section data.
 * Projected state IDs can have a different VarInt width than their native IDs, so the size
 * calculation and the subsequent write must observe precisely the same projected palette.</p>
 */
public final class ChunkBlockStateProjectionAdvice {
    private ChunkBlockStateProjectionAdvice() {
    }

    @Advice.OnMethodEnter
    public static void enter() {
        NativeItemCodecHooks.beginBlockPacketProjection();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit() {
        NativeItemCodecHooks.endBlockPacketProjection();
    }
}
