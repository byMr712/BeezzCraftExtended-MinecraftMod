package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void beezzcraft$disconnect(Screen screen, CallbackInfo ci) {
        BeezzCraft.onDisconnect();
    }
}
