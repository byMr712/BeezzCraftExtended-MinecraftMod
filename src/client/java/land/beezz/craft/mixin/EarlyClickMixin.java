package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The gear panel and the wheel can cover a screen's own clickable parts (the creative tabs, the recipe book).
 * Take those clicks before the screen's own mouseClicked handles them.
 */
@Mixin({InventoryScreen.class, CraftingScreen.class, CreativeModeInventoryScreen.class})
public abstract class EarlyClickMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$mouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (BeezzCraft.captureMouse(button)) {
            cir.setReturnValue(false);
            return;
        }
        if (!BeezzCraft.mouse((AbstractContainerScreen<?>) (Object) this, mx, my, button,
                Screen.hasAltDown(), Screen.hasShiftDown(), Screen.hasControlDown())) {
            cir.setReturnValue(false);
        }
    }
}
