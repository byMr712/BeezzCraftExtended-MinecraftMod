import land.beezz.craft.BeezzCraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redraws the wheel at the very end of the frame, above other screens and overlays. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void beezzcraft$redrawWheel(CallbackInfo ci) {
        BeezzCraft.redrawWheel();
    }
}
