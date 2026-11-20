package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No recipe-book tooltip while the wheel is open: the wheel's own card is the tooltip. */
@Mixin(RecipeBookComponent.class)
abstract class RecipeBookTooltipMixin {
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void beezzcraft$noRecipeBookTooltip(GuiGraphics g, int mx, int my, int slotX, int slotY, CallbackInfo ci) {
        if (BeezzCraft.wheelOpen()) ci.cancel();
    }
}
