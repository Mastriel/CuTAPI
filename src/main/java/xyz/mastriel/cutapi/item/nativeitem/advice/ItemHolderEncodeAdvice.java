package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

public final class ItemHolderEncodeAdvice {
    private ItemHolderEncodeAdvice() {
    }

    @Advice.OnMethodEnter
    @SuppressWarnings("unchecked")
    public static void enter(
        @Advice.FieldValue("val$registryKey") ResourceKey<?> registryKey,
        @Advice.Argument(value = 1, readOnly = false) Holder<?> holder
    ) {
        if (registryKey == Registries.ITEM) {
            holder = NativeItemCodecHooks.encodeHolder((Holder<Item>) holder);
        }
    }
}
