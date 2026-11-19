package land.beezz.craft.mixin;

import land.beezz.craft.AutoTest;
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
        Minecraft mc = (Minecraft) (Object) this;
        BeezzCraft.onTick(mc);
        AutoTest.tick(mc);
    }

    @Inject(method = "disconnectFromWorld", at = @At("HEAD"))
    private void beezzcraft$disconnect(Component message, CallbackInfo ci) {
        BeezzCraft.onDisconnect();
    }
}
