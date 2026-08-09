package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagNetworkSerialization;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

public final class ItemTagPayloadAdvice {
    private ItemTagPayloadAdvice() {
    }

    @Advice.OnMethodExit
    public static void exit(
        @Advice.Argument(0) Registry<?> registry,
        @Advice.Return TagNetworkSerialization.NetworkPayload payload
    ) {
        NativeItemCodecHooks.projectTags(registry, payload);
    }
}
