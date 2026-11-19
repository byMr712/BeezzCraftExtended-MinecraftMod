package land.beezz.craft.mixin;

import land.beezz.craft.BeezzCraftKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/** Adds the BeezzCraft wheel key to Options so it appears in Controls and is saved in options.txt. */
@Mixin(value = Options.class, priority = 900)
public class OptionsMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void beezzcraft$addKey(CallbackInfo ci) {
        OptionsAccessor self = (OptionsAccessor) (Object) this;
        KeyMapping[] old = self.beezzcraft$keyMappings();
        for (KeyMapping key : old) if (key == BeezzCraftKeys.MODIFIER) return;
        KeyMapping[] all = Arrays.copyOf(old, old.length + 1);
        all[old.length] = BeezzCraftKeys.MODIFIER;
        self.beezzcraft$setKeyMappings(all);
    }
}
