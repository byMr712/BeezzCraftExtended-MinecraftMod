package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The creative item picker draws its own item grid, outside renderSlots/renderSlot. Hide the whole picker while the
 * wheel is open, otherwise the creative blocks paint over the wheel and its "close" card.
 */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeInventoryMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$hideCreative(GuiGraphics g, int mx, int my, float delta, CallbackInfo ci) {
        if (BeezzCraft.wheelOpenOn(this)) ci.cancel();
    }
}
