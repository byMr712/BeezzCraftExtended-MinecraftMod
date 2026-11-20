package land.beezz.craft.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The slot under the mouse: the functional key + click on it opens the wheel. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot beezzcraft$hoveredSlot();

    @Accessor("leftPos")
    int beezzcraft$leftPos();

    @Accessor("topPos")
    int beezzcraft$topPos();

    @Accessor("imageWidth")
    int beezzcraft$imageWidth();
}
