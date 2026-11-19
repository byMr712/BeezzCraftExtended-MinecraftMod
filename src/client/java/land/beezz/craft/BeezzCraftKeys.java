package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import land.beezz.craft.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** The modifier that opens the wheel: hold it and right-click an item. Rebindable in Controls. */
public final class BeezzCraftKeys {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("beezzcraft", "beezz_craft_extended"));

    public static final KeyMapping MODIFIER = new KeyMapping(
            "key.beezzcraft.wheel",
            InputConstants.KEY_LCONTROL,
            CATEGORY);

    public static boolean modifierDown() {
        InputConstants.Key key = ((KeyMappingAccessor) MODIFIER).beezzcraft$key();
        return MODIFIER.isDown() || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
    }

    private BeezzCraftKeys() {}
}
