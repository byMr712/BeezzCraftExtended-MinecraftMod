package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Fabric API's screen events: the wheel takes the mouse and the keys while it is open, and the gear, the
 * settings and the queue take their clicks before the slots do.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void beezzcraft$init(CallbackInfo ci) {
        BeezzCraft.onScreenInit((Screen) (Object) this);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void beezzcraft$removed(CallbackInfo ci) {
        BeezzCraft.onScreenRemoved((Screen) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void beezzcraft$tick(CallbackInfo ci) {
        BeezzCraft.onScreenTick((Screen) (Object) this);
    }

    @Inject(method = "renderSlots", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$hideSlots(GuiGraphics g, CallbackInfo ci) {
        if (BeezzCraft.wheelOpenOn(this)) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!BeezzCraft.mouse((AbstractContainerScreen<?>) (Object) this, mx, my, button,
                Screen.hasAltDown(), Screen.hasShiftDown(), Screen.hasControlDown())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseReleased(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (BeezzCraft.wheelOpenOn(this)) cir.setReturnValue(false);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseDragged(double mx, double my, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (BeezzCraft.wheelOpenOn(this)) cir.setReturnValue(false);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseScrolled(double mx, double my, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (BeezzCraft.wheelOpenOn(this)) {
            BeezzCraft.scroll(scrollY);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (BeezzCraft.wheelOpenOn(this)) {
            boolean inventory = Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode);
            BeezzCraft.key((Screen) (Object) this, keyCode, inventory);
            cir.setReturnValue(true);
        }
    }
}
