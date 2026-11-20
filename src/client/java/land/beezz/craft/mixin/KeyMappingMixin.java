package land.beezz.craft.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * The Controls screen sorts key mappings by category through a fixed map of vanilla categories. The mod's own
 * category is added here; otherwise {@link KeyMapping#compareTo} dereferences a null sort value and the screen crashes.
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @Shadow
    @Final
    private static Map<String, Integer> CATEGORY_SORT_ORDER;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void beezzcraft$addCategory(CallbackInfo ci) {
        CATEGORY_SORT_ORDER.put("key.category.beezzcraft.beezz_craft_extended", 8);
    }
}
