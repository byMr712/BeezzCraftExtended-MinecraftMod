package land.beezz.craft;

import land.beezz.craft.mixin.AbstractContainerScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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
 * BeezzCraft: in the inventory or at a crafting table, the functional key + click on an item opens an octagon wheel of what can
 * be crafted from it. Click crafts once, Shift + click as much as possible, Ctrl + click ten times; clicking a grey
 * recipe puts it in the craft queue (when switched on behind the gear).
 * <p>
 * Loader-agnostic: both the Fabric entrypoint and the NeoForge entrypoint call {@link #init()}; the game hooks are
 * vanilla mixins, so neither Fabric API nor the NeoForge event bus is needed.
 */
public final class BeezzCraft {
    public static final Logger LOG = LoggerFactory.getLogger("BeezzCraft");
    /** The wheel that is open right now (one at a time), or null. */
    static Wheel wheel;
    /** The "nothing can be crafted" notice above the inventory panel, or null. */
    private static Component notice;
    private static long noticeUntil;

    private BeezzCraft() {}

    public static void init() {
        // Do not touch Minecraft.getInstance() here: NeoForge constructs the mod before the client exists.
        // Config is loaded lazily on the client thread when the wheel or the HUD first needs it.
        LOG.info("BeezzCraft ready: hold the functional key and click an item in the inventory or at a crafting table");
    }

    /** Called once per client tick. */
    public static void onTick(Minecraft mc) {
        Config.get(); // first call on the client thread: config paths need Minecraft.getInstance()
        Crafter.tick(mc);
    }

    public static void onDisconnect() {
        Crafter.clear();
        Recipes.forget();
        wheel = null;
        notice = null;
    }

    /** Called when a container screen is initialised: a new screen never keeps the old wheel. */
    public static void onScreenInit(Screen screen) {
        if (screen instanceof InventoryScreen || screen instanceof CraftingScreen
                || screen instanceof CreativeModeInventoryScreen) {
            wheel = null;
            notice = null;
        }
    }

    public static void onScreenRemoved(Screen screen) {
        // Keep the wheel when another screen (a recipe book overlay) opens on top of it;
        // a new inventory/crafting screen resets it in onScreenInit.
        Panel.close();
        notice = null;
    }

    /** Called from the screen's own tick. */
    public static void onScreenTick(Screen screen) {
        if (wheel != null && wheel.screen == screen) wheel.tick();
    }

    /** Called by ScreenMixin after the screen and before its tooltips. */
    public static void renderWheel(Screen screen, GuiGraphics g, int mx, int my) {
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CraftingScreen)
                && !(screen instanceof CreativeModeInventoryScreen)) {
            // A recipe book or another screen opened on top: keep the open wheel visible above it.
            if (wheel != null) wheel.render(g, Minecraft.getInstance().font, mx, my);
            return;
        }
        Font font = screen.getFont();
        if (wheel != null && wheel.screen == screen) wheel.render(g, font, mx, my);
        else Panel.render((AbstractContainerScreen<?>) screen, g, font, mx, my);
        g.nextStratum();
        drawNotice((AbstractContainerScreen<?>) screen, g, font);
    }

    /** Shows the "nothing can be crafted" notice above the inventory panel for three seconds. */
    static void showNotice(Component text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 3000;
    }

    private static void drawNotice(AbstractContainerScreen<?> screen, GuiGraphics g, Font font) {
        if (notice == null) return;
        if (System.currentTimeMillis() >= noticeUntil) {
            notice = null;
            return;
        }
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        int w = font.width(notice);
        int x = Math.max(2, Math.min(screen.width - w - 2, (screen.width - w) / 2));
        int y = Math.max(2, a.beezzcraft$topPos() - 12);
        g.drawString(font, notice, x, y, 0xFFFFFFFF, true);
    }

    public static boolean wheelOpenOn(Object screen) {
        return wheel != null && wheel.screen == screen;
    }

    public static boolean wheelOpen() {
        return wheel != null;
    }

    public static boolean keyOnly() {
        return Config.get().keyOnly;
    }

    public static String rawTranslation(String key) {
        return Tr.raw(key);
    }

    /** @return true to let the screen handle the click as usual */
    public static boolean mouse(AbstractContainerScreen<?> screen, double mx, double my, int button, boolean alt, boolean shift, boolean ctrl) {
        if (wheel != null) {
            if (!wheel.click(mx, my, button, shift, ctrl)) wheel = null;
            return false;
        }
        boolean panel = Panel.click(mx, my, button); // the gear, its settings and the queue (top left)
        if (Boolean.getBoolean("beezzcraft.debug")) LOG.info("click button={} alt={} slot={}", button, alt, ((AbstractContainerScreenAccessor) screen).beezzcraft$hoveredSlot());
        if (BeezzCraftKeys.modifierDown()
                && button == (Config.get().functionalClick == 0 ? InputConstants.MOUSE_BUTTON_LEFT : InputConstants.MOUSE_BUTTON_RIGHT)) {
            Slot slot = ((AbstractContainerScreenAccessor) screen).beezzcraft$hoveredSlot();
            if (slot != null && slot.hasItem()) {
                open(screen, slot.getItem(), mx, my);
                return false;
            }
        }
        return !panel;
    }

    /** The mouse wheel over the wheel: variants under the cursor, otherwise pages. */
    public static void scroll(double amount) {
        if (wheel != null) wheel.scroll(amount);
    }

    /** A key while the wheel is open: Escape or the inventory key closes it, everything else is swallowed. */
    public static void key(Screen screen, int key, boolean inventoryKey) {
        if (wheel == null) return;
        if (key == InputConstants.KEY_ESCAPE || inventoryKey) wheel = null;
    }

    static void open(AbstractContainerScreen<?> screen, ItemStack stack, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        List<Recipes.Option> options = Recipes.uses(mc, stack, Recipes.gridSize(screen.getMenu()));
        if (Boolean.getBoolean("beezzcraft.debug")) {
            LOG.info("open {}: {} options, {} recipe collections", stack, options.size(), mc.player.getRecipeBook().getCollections().size());
        }
        if (options.isEmpty()) {
            showNotice(Tr.t(Recipes.gridSize(screen.getMenu()) >= 3 ? "beezzcraft.nothing" : "beezzcraft.nothing.inv",
                    stack.getHoverName()));
            return;
        }
        notice = null;
        wheel = new Wheel(screen, stack, options, mx, my);
    }

    public static boolean openHovered(AbstractContainerScreen<?> screen) {
        Slot slot = ((AbstractContainerScreenAccessor) screen).beezzcraft$hoveredSlot();
        if (slot == null || !slot.hasItem()) return false;
        open(screen, slot.getItem(), screen.width / 2f, screen.height / 2f);
        return true;
    }


    /** The craft queue on the HUD while playing: a small card with what waits for resources. */
    public static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || screen(mc) != null) return;
        List<Crafter.Job> queued = Crafter.jobs().stream().filter(j -> j.queued).toList();
        if (queued.isEmpty()) return;
        Font font = mc.font;
        int shown = Math.min(queued.size(), 6), w = 22 + shown * 26, x = 4, y = 4;
        Ui.panel(g, x, y, w, 22);
        Ui.icon(g, Ui.QUEUE, x + 5, y + 5, 12, Ui.BLUE);
        for (int i = 0; i < shown; i++) {
            Crafter.Job j = queued.get(i);
            int ix = x + 20 + i * 26;
            g.renderItem(j.result, ix, y + 3);
            String n = j.all ? "∞" : String.valueOf(j.left);
            g.drawString(font, n, ix + 17 - font.width(n), y + 12, 0xFFFFFFFF, true);
        }
        if (queued.size() > shown) g.drawString(font, "+" + (queued.size() - shown), x + w + 3, y + 7, Ui.MUTED, true);
        if (Config.get().showNeeds) Needs.render(g, font, Needs.of(mc.player, mc.player.inventoryMenu), x, y + 26, 0, false);
    }

    /** The open screen. */
    public static Screen screen(Minecraft mc) {
        return mc.screen;
    }
}
