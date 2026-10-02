package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

/** Lets an authoritative native custom stack satisfy vanilla checks for its backing item. */
public final class ItemStackBackingIdentityAdvice {
    private ItemStackBackingIdentityAdvice() {
    }

    @Advice.OnMethodExit
    public static void exit(
        @Advice.This ItemStack stack,
        @Advice.Argument(0) Item expected,
        @Advice.Return(readOnly = false) boolean matches
    ) {
        if (!matches) {
            matches = NativeItemCodecHooks.matchesBackingItem(stack.getItem(), expected);
        }
    }
}
