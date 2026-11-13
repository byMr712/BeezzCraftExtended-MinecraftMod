package land.beezz.craft;

import land.beezz.craft.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * BeezzCraft: in the inventory or at a crafting table, Alt + right click on an item opens an octagon wheel of what can
 * be crafted from it. Click crafts once, Shift + click as much as possible, Ctrl + click ten times; clicking a grey
 * recipe puts it in the craft queue (when switched on behind the gear).
 */
public final class BeezzCraft implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("BeezzCraft");
    /** The wheel that is open right now (one at a time), or null. */
    static Wheel wheel;

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen) && !(screen instanceof CraftingScreen)) return;
            AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) screen;
            wheel = null;
            ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> {
                // the click's own modifier flags, or the keys held right now (some inputs don't fill the flags)
                Minecraft mc2 = Minecraft.getInstance();
                return mouse(cs, e.x(), e.y(), e.button(), e.hasAltDown() || mc2.hasAltDown(),
                        e.hasShiftDown() || mc2.hasShiftDown(), e.hasControlDown() || mc2.hasControlDown());
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> wheel == null);
            ScreenMouseEvents.allowMouseDrag(screen).register((s, e, dx, dy) -> wheel == null);
            ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, hx, vy) -> {
                if (wheel == null) return true;
                wheel.scroll(vy);
                return false;
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> {
                if (wheel == null) return true;
                int key = e.input();
                if (key == InputConstants.KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(e)) wheel = null;
                return false; // keys do nothing under the wheel
            });
            ScreenEvents.afterTick(screen).register(s -> {
                if (wheel != null && wheel.screen == s) wheel.tick();
            });
            ScreenEvents.remove(screen).register(s -> {
                if (wheel != null && wheel.screen == s) wheel = null;
                Panel.close();
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(Crafter::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            Crafter.clear();
            wheel = null;
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("beezzcraft", "queue"), (g, delta) -> hud(g));
        Config.get();
        LOG.info("BeezzCraft ready: Alt + right click on an item in the inventory or at a crafting table");
    }

    /** Called by ScreenMixin after the screen and before its tooltips. */
    public static void renderWheel(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CraftingScreen)) return;
        Font font = Screens.getFont(screen);
        if (wheel != null && wheel.screen == screen) wheel.render(g, font, mx, my);
        Panel.render((AbstractContainerScreen<?>) screen, g, font, mx, my);
    }

    public static boolean wheelOpenOn(Object screen) {
        return wheel != null && wheel.screen == screen;
    }

    /** @return true to let the screen handle the click as usual */
    private static boolean mouse(AbstractContainerScreen<?> screen, double mx, double my, int button, boolean alt, boolean shift, boolean ctrl) {
        if (Panel.click(mx, my, button)) return false; // the gear, its settings and the queue (top left)
        if (wheel != null) {
            if (!wheel.click(mx, my, button, shift, ctrl)) wheel = null;
            return false;
        }
        if (Boolean.getBoolean("beezzcraft.debug")) LOG.info("click button={} alt={} slot={}", button, alt, ((AbstractContainerScreenAccessor) screen).beezzcraft$hoveredSlot());
        if (!alt || button != InputConstants.MOUSE_BUTTON_RIGHT) return true;
        Slot slot = ((AbstractContainerScreenAccessor) screen).beezzcraft$hoveredSlot();
        if (slot == null || !slot.hasItem()) return true;
        open(screen, slot.getItem(), mx, my);
        return false;
    }

    static void open(AbstractContainerScreen<?> screen, ItemStack stack, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        List<Recipes.Option> options = Recipes.uses(mc, stack, Recipes.gridSize(screen.getMenu()));
        if (Boolean.getBoolean("beezzcraft.debug")) {
            LOG.info("open {}: {} options, {} recipe collections", stack, options.size(), mc.player.getRecipeBook().getCollections().size());
        }
        if (options.isEmpty()) {
            mc.player.sendOverlayMessage(Component.translatable(Recipes.gridSize(screen.getMenu()) >= 3 ? "beezzcraft.nothing" : "beezzcraft.nothing.inv",
                    stack.getHoverName()).withStyle(ChatFormatting.GRAY));
            return;
        }
        wheel = new Wheel(screen, stack, options, mx, my);
    }

    /** The craft queue on the HUD while playing: a small card with what waits for resources. */
    private static void hud(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;
        List<Crafter.Job> queued = Crafter.jobs().stream().filter(j -> j.queued).toList();
        if (queued.isEmpty()) return;
        Font font = mc.font;
        int shown = Math.min(queued.size(), 6), w = 22 + shown * 26, x = 4, y = 4;
        Ui.panel(g, x, y, w, 22);
        Ui.icon(g, Ui.QUEUE, x + 5, y + 5, 12, Ui.BLUE);
        for (int i = 0; i < shown; i++) {
            Crafter.Job j = queued.get(i);
            int ix = x + 20 + i * 26;
            g.item(j.result, ix, y + 3);
            String n = j.all ? "∞" : String.valueOf(j.left);
            g.text(font, n, ix + 17 - font.width(n), y + 12, 0xFFFFFFFF, true);
        }
        if (queued.size() > shown) g.text(font, "+" + (queued.size() - shown), x + w + 3, y + 7, Ui.MUTED, true);
    }

    static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }
}
