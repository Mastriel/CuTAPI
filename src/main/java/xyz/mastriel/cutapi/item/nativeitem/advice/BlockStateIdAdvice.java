package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.Block;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

/** Projects native custom block states exactly where Minecraft resolves their client wire ID. */
public final class BlockStateIdAdvice {
    private BlockStateIdAdvice() {
    }

    @Advice.OnMethodEnter
    public static void enter(
        @Advice.This IdMapper<?> mapper,
        @Advice.Argument(value = 0, readOnly = false) Object value
    ) {
        if (mapper == Block.BLOCK_STATE_REGISTRY) {
            value = NativeItemCodecHooks.encodeBlockState(value);
        }
    }
}
