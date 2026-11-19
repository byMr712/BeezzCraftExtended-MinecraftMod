package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Top left corner of the inventory and crafting table screens: the gear (settings) and, under it, the craft queue.
 */
final class Panel {
    private static final int X = 6, Y = 6, GEAR = 20, W = 176, ROW = 18, QROW = 22;
    private static boolean settings;

    private Panel() {}

    static void openSettings() {
        settings = true;
    }

    static void close() {
        settings = false;
    }

    private record Toggle(ItemStack icon, Component label, boolean on, Runnable flip) {}

    private static List<Toggle> toggles() {
        Config c = Config.get();
        List<Toggle> t = new ArrayList<>();
        t.add(new Toggle(new ItemStack(Items.CLOCK), Component.translatable("beezzcraft.set.queue"), c.queue, () -> {
            c.queue = !c.queue;
            if (!c.queue) Crafter.jobs().removeIf(j -> j.queued);
        }));
        t.add(new Toggle(new ItemStack(Items.SPYGLASS), Component.translatable("beezzcraft.set.missing"), c.showMissing, () -> {
            c.showMissing = !c.showMissing;
            if (BeezzCraft.wheel != null) BeezzCraft.wheel.refresh();
        }));
        t.add(new Toggle(new ItemStack(Items.IRON_PICKAXE), Component.translatable("beezzcraft.set.needs"), c.showNeeds, () -> c.showNeeds = !c.showNeeds));
        t.add(new Toggle(new ItemStack(Items.NOTE_BLOCK), Component.translatable("beezzcraft.set.sound"), c.sound, () -> c.sound = !c.sound));
        return t;
    }

    // ------------------------------------------------------------------ layout

    /** The queue switch's explanation, wrapped to the panel. */
    private static List<net.minecraft.util.FormattedCharSequence> note() {
        Font font = Minecraft.getInstance().font;
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(font.split(Component.translatable("beezzcraft.set.queue.note1"), W - 16));
        lines.addAll(font.split(Component.translatable("beezzcraft.set.queue.note2"), W - 16));
        return lines;
    }

    private static int noteH() {
        return note().size() * 10 + 4;
    }

    private static int settingsH() {
        return 6 + toggles().size() * ROW + noteH() + 4;
    }

    private static int queueY() {
        return Y + GEAR + 6 + (settings ? settingsH() + 6 : 0);
    }

    private static List<Crafter.Job> queued() {
        return Crafter.jobs().stream().filter(j -> j.queued).toList();
    }

    /** Height of the queue card: its rows, or the wrapped hint when it is empty. */
    private static int queueH() {
        int shown = Math.min(queued().size(), 8);
        if (shown > 0) return 22 + shown * QROW + 4;
        return 22 + Minecraft.getInstance().font.split(Component.translatable("beezzcraft.queue.empty"), W - 14).size() * 10 + 6;
    }

    private static boolean showQueue() {
        return Config.get().queue || !queued().isEmpty();
    }

    // ------------------------------------------------------------------ drawing

    static void render(AbstractContainerScreen<?> screen, GuiGraphics g, Font font, int mx, int my) {
        boolean gh = in(mx, my, X, Y, GEAR, GEAR);
        Ui.panel(g, X, Y, GEAR, GEAR);
        if (gh || settings) Ui.round(g, X, Y, GEAR, GEAR, 0xFF3A3214);
        Ui.icon(g, Ui.GEAR, X + 3, Y + 3, 14, gh || settings ? Ui.HONEY_SOFT : Ui.HONEY);
        int count = queued().size();
        if (count > 0) {
            String n = String.valueOf(count);
            Ui.round(g, X + GEAR - 6, Y - 3, font.width(n) + 5, 10, 0xFF2563EB);
            g.drawString(font, n, X + GEAR - 3, Y - 2, 0xFFFFFFFF, false);
        }

        if (settings) renderSettings(g, font, mx, my);
        if (showQueue()) renderQueue(screen, g, font, mx, my);
        if (showQueue() && Config.get().showNeeds && !queued().isEmpty()) {
            Needs.render(g, font, Needs.of(Minecraft.getInstance().player, screen.getMenu()), X, queueY() + queueH() + 6, W, true);
        }
        if (gh) Card.text(g, font, Component.translatable("beezzcraft.gear"), mx, my, screen.width, screen.height);
    }

    private static void renderSettings(GuiGraphics g, Font font, int mx, int my) {
        int y = Y + GEAR + 6, h = settingsH();
        Ui.panel(g, X, y, W, h);
        List<Toggle> t = toggles();
        int ry = y + 4;
        for (int i = 0; i < t.size(); i++) {
            Toggle row = t.get(i);
            boolean hover = in(mx, my, X, ry, W, ROW);
            if (hover) Ui.round(g, X + 2, ry, W - 4, ROW, Ui.PANEL_HI);
            g.renderItem(row.icon, X + 5, ry + 1);
            g.drawString(font, font.plainSubstrByWidth(row.label.getString(), W - 52), X + 24, ry + 5, Ui.TEXT, false);
            Ui.toggle(g, X + W - 22, ry + 5, row.on);
            ry += ROW;
            if (i == 0) {
                // what the queue does, right under its switch
                for (var line : note()) {
                    g.drawString(font, line, X + 8, ry, Ui.DIM, false);
                    ry += 10;
                }
                ry += 4;
            }
        }
    }

    private static void renderQueue(AbstractContainerScreen<?> screen, GuiGraphics g, Font font, int mx, int my) {
        List<Crafter.Job> jobs = queued();
        int shown = Math.min(jobs.size(), 8);
        int y = queueY(), h = queueH();
        Ui.panel(g, X, y, W, h);
        Ui.icon(g, Ui.QUEUE, X + 6, y + 5, 11, Ui.BLUE);
        g.drawString(font, Component.translatable("beezzcraft.hud"), X + 21, y + 7, Ui.BLUE, false);
        int ry = y + 21;
        if (jobs.isEmpty()) {
            int ly = ry + 2;
            for (var line : font.split(Component.translatable("beezzcraft.queue.empty"), W - 14)) {
                g.drawString(font, line, X + 7, ly, Ui.DIM, false);
                ly += 10;
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < shown; i++) {
            Crafter.Job j = jobs.get(i);
            boolean hover = in(mx, my, X, ry, W, QROW);
            if (hover) Ui.round(g, X + 2, ry, W - 4, QROW, Ui.PANEL_HI);
            g.renderItem(j.result, X + 5, ry + 3);
            String name = font.plainSubstrByWidth(j.result.getHoverName().getString(), W - 74);
            g.drawString(font, name, X + 24, ry + 3, Ui.TEXT, false);
            String n = j.all ? "∞" : "×" + j.left;
            g.drawString(font, n, X + W - 22 - font.width(n), ry + 3, Ui.HONEY_SOFT, false);
            Component status;
            int color;
            if (j.needsTable && !(screen.getMenu() instanceof CraftingMenu)) {
                status = Component.translatable("beezzcraft.queue.table");
                color = Ui.MUTED;
            } else if (screen.getMenu() instanceof RecipeBookMenu<?, ?> m && Crafter.canCraft(mc.player, m, j.option)) {
                status = Component.translatable("beezzcraft.queue.ready");
                color = Ui.GREEN;
            } else {
                status = Component.translatable("beezzcraft.queue.wait");
                color = Ui.DIM;
            }
            g.drawString(font, status, X + 24, ry + 12, color, false);
            boolean xh = in(mx, my, X + W - 16, ry + 4, 12, 12);
            Ui.round(g, X + W - 16, ry + 5, 11, 11, xh ? 0xFF7F1D1D : 0xFF2C313C);
            g.drawString(font, "✕", X + W - 13, ry + 7, xh ? 0xFFFFFFFF : Ui.MUTED, false);
            ry += QROW;
        }
        if (jobs.size() > shown) g.drawString(font, "+" + (jobs.size() - shown), X + W - 20, y + 7, Ui.MUTED, false);
    }

    // ------------------------------------------------------------------ input

    /** @return true when the click was on the panel */
    static boolean click(double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return in(mx, my, X, Y, GEAR, GEAR) || settings && in(mx, my, X, Y + GEAR + 6, W, settingsH());
        }
        if (in(mx, my, X, Y, GEAR, GEAR)) {
            settings = !settings;
            return true;
        }
        if (settings) {
            int y = Y + GEAR + 6;
            if (in(mx, my, X, y, W, settingsH())) {
                List<Toggle> t = toggles();
                int ry = y + 4;
                for (int i = 0; i < t.size(); i++) {
                    if (in(mx, my, X, ry, W, ROW)) {
                        t.get(i).flip.run();
                        Config.get().save();
                        return true;
                    }
                    ry += ROW + (i == 0 ? noteH() : 0);
                }
                return true;
            }
        }
        if (showQueue()) {
            List<Crafter.Job> jobs = queued();
            int y = queueY(), shown = Math.min(jobs.size(), 8);
            if (in(mx, my, X, y, W, queueH())) {
                int ry = y + 21;
                for (int i = 0; i < shown; i++) {
                    if (in(mx, my, X + W - 16, ry + 4, 12, 12)) {
                        Crafter.jobs().remove(jobs.get(i));
                        return true;
                    }
                    ry += QROW;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
