package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** The modifier that opens the wheel: hold it and right-click an item. Rebindable in Controls. */
public final class BeezzCraftKeys {
    public static final KeyMapping MODIFIER = new KeyMapping(
            "key.beezzcraft.modifier",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LCONTROL,
            "key.category.beezzcraft.beezz_craft_extended");

    private BeezzCraftKeys() {}
}
