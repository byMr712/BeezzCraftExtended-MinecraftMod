package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import land.beezz.craft.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** The modifier that opens the wheel: hold it and right-click an item. Rebindable in Controls. */
public final class BeezzCraftKeys {
    public static final KeyMapping MODIFIER = new KeyMapping(
            "key.beezzcraft.wheel",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LCONTROL,
            "key.category.beezzcraft.beezz_craft_extended");

    public static boolean matchesKey(int keyCode, int scanCode) {
        return MODIFIER.matches(keyCode, scanCode);
    }

    public static boolean modifierDown() {
        InputConstants.Key key = ((KeyMappingAccessor) MODIFIER).beezzcraft$key();
        Minecraft mc = Minecraft.getInstance();
        return MODIFIER.isDown() || InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    private BeezzCraftKeys() {}
}
