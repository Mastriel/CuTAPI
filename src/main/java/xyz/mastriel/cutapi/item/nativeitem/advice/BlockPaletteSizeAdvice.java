package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.core.IdMap;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

/** Projects the ID map used to calculate serialized local-palette VarInt widths. */
public final class BlockPaletteSizeAdvice {
    private BlockPaletteSizeAdvice() {
    }

    @Advice.OnMethodEnter
    public static void enter(@Advice.Argument(value = 0, readOnly = false) IdMap<?> registry) {
        registry = NativeItemCodecHooks.projectBlockPaletteRegistry(registry);
    }
}
