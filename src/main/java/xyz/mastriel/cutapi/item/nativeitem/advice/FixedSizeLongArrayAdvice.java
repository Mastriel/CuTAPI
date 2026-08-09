package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

/** Substitutes projected global-palette storage at the final buffer write boundary. */
public final class FixedSizeLongArrayAdvice {
    private FixedSizeLongArrayAdvice() {
    }

    @Advice.OnMethodEnter
    public static void enter(@Advice.Argument(value = 0, readOnly = false) long[] values) {
        values = NativeItemCodecHooks.projectFixedSizeLongArray(values);
    }
}
