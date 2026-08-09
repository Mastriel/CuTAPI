package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.world.item.ItemStack;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

public final class ItemStackDecodeAdvice {
    private ItemStackDecodeAdvice() {
    }

    @Advice.OnMethodExit
    public static void exit(@Advice.Return(readOnly = false) ItemStack stack) {
        stack = NativeItemCodecHooks.decodeStack(stack);
    }
}
