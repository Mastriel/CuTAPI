package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

/** Finalizes native block registration at the pre-world STARTUP plugin boundary. */
public final class StartupPluginFinalizerAdvice {
    private StartupPluginFinalizerAdvice() {
    }

    @Advice.OnMethodExit
    public static void exit(@Advice.Argument(0) Object loadOrder) {
        NativeItemCodecHooks.finishStartupPluginLoading(loadOrder);
    }
}
