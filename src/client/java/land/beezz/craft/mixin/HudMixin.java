package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Fabric API's HUD element registry: the craft queue card is drawn on top of the vanilla HUD. */
@Mixin(Hud.class)
public class HudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void beezzcraft$hud(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        BeezzCraft.hud(g);
    }
}
