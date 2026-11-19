package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import land.beezz.craft.BeezzCraftKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        boolean alt = event.hasAltDown() || mc.hasAltDown();
        boolean shift = event.hasShiftDown() || mc.hasShiftDown();
        boolean ctrl = event.hasControlDown() || mc.hasControlDown();
        if (!BeezzCraft.mouse((AbstractContainerScreen<?>) (Object) this, event.x(), event.y(),
                event.button(), alt, shift, ctrl)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (BeezzCraft.wheelOpenOn(this)) cir.setReturnValue(false);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseDragged(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
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
    private void beezzcraft$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!BeezzCraft.wheelOpen() && BeezzCraft.keyOnly()
                && BeezzCraftKeys.MODIFIER.matches(event)
                && BeezzCraft.openHovered((AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
            return;
        }
        if (BeezzCraft.wheelOpenOn(this)) {
            boolean inventory = Minecraft.getInstance().options.keyInventory.matches(event);
            BeezzCraft.key((Screen) (Object) this, event.input(), inventory);
            cir.setReturnValue(true);
        }
    }
}
