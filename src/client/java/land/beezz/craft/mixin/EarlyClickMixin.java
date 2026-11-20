package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The gear panel and the wheel can cover a screen's own clickable parts (the creative tabs, the recipe book).
 * Take those clicks before the screen's own mouseClicked handles them.
 */
@Mixin({AbstractRecipeBookScreen.class, CreativeModeInventoryScreen.class})
public abstract class EarlyClickMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (BeezzCraft.captureMouse(event.button())) {
            cir.setReturnValue(false);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean alt = event.hasAltDown() || mc.hasAltDown();
        boolean shift = event.hasShiftDown() || mc.hasShiftDown();
        boolean ctrl = event.hasControlDown() || mc.hasControlDown();
        if (!BeezzCraft.mouse((AbstractContainerScreen<?>) (Object) this, event.x(), event.y(),
                event.button(), alt, shift, ctrl)) {
            cir.setReturnValue(false);
        }
    }
}
