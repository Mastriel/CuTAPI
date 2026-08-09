package xyz.mastriel.cutapi.item.nativeitem.advice;

import net.bytebuddy.asm.Advice;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import xyz.mastriel.cutapi.injected.NativeItemCodecHooks;

/** Projects item stacks embedded in registry-aware values such as chat components. */
public final class RegistryCodecEncodeAdvice {
    private RegistryCodecEncodeAdvice() {
    }

    @Advice.OnMethodEnter
    public static void enter(
        @Advice.Argument(0) RegistryFriendlyByteBuf buffer,
        @Advice.Argument(value = 1, readOnly = false) Component component
    ) {
        component = NativeItemCodecHooks.encodeComponent(buffer, component);
    }
}
