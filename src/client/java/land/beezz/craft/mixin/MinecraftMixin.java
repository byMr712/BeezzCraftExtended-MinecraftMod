package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Fabric API's client tick and disconnect events. */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void beezzcraft$tick(CallbackInfo ci) {
        BeezzCraft.onTick((Minecraft) (Object) this);
    }

    @Inject(method = "disconnectFromWorld", at = @At("HEAD"))
    private void beezzcraft$disconnect(Component message, CallbackInfo ci) {
        BeezzCraft.onDisconnect();
    }
}
