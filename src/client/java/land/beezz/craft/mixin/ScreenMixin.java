package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The wheel is drawn after the whole screen but before the deferred elements (tooltips): so it covers the inventory and
 * its own tooltip still shows.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderWithTooltipAndSubtitles",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderDeferredElements()V"))
    private void beezzcraft$wheel(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        BeezzCraft.capture(g, mx, my);
        BeezzCraft.renderWheel((Screen) (Object) this, g, mx, my);
    }
}
