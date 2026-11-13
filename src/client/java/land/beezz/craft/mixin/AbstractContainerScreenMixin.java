package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No item tooltip of the slot under the wheel: the wheel shows its own. */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$noSlotTooltip(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        if (BeezzCraft.wheelOpenOn(this)) ci.cancel();
    }
}
