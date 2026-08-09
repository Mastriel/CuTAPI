package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.world.item.ItemStack;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

public final class ItemStackEncodeAdvice {
    private ItemStackEncodeAdvice() {
    }

    @Advice.OnMethodEnter
    public static void enter(@Advice.Argument(value = 1, readOnly = false) ItemStack stack) {
        stack = NativeItemCodecHooks.encodeStack(stack);
    }
}
