package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.core.IdMap;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

/** Opens raw-ID projection while a global paletted container writes its packed storage. */
public final class GlobalBlockPaletteWriteAdvice {
    private GlobalBlockPaletteWriteAdvice() {
    }

    @Advice.OnMethodEnter
    public static void enter(
        @Advice.Argument(value = 1, readOnly = false) IdMap<?> registry,
        @Advice.FieldValue("palette") Palette<?> palette,
        @Advice.FieldValue("storage") BitStorage storage
    ) {
        NativeItemCodecHooks.beginBlockPaletteWrite(registry, palette, storage);
        registry = NativeItemCodecHooks.projectBlockPaletteRegistry(registry);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit() {
        NativeItemCodecHooks.endBlockPaletteWrite();
    }
}
