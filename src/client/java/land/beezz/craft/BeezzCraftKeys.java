package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** The modifier that opens the wheel: hold it and right-click an item. Rebindable in Controls. */
public final class BeezzCraftKeys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("beezzcraft", "beezz_craft_extended"));

    public static final KeyMapping MODIFIER = new KeyMapping(
            "key.beezzcraft.modifier",
            InputConstants.KEY_LCONTROL,
            CATEGORY);

    private BeezzCraftKeys() {}
}
