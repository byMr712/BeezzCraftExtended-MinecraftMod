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
 * The gear (settings) and, under it, the craft queue. The gear can sit in any corner; the queue stays in the
 * top-left corner so it never jumps around while the gear is moved.
 */
final class Panel {
    private static final int X = 6, Y = 6, GEAR = 20, W = 176, ROW = 18, QROW = 22;
    private static boolean settings;
    private static boolean listeningKey;

    private Panel() {}

    static void openSettings() {
        settings = true;
    }

    static void close() {
        settings = false;
        listeningKey = false;
    }

    static boolean captureKey(InputConstants.Key key) {
        if (!listeningKey) return false;
        if (key.getValue() != InputConstants.KEY_ESCAPE) {
            BeezzCraftKeys.MODIFIER.setKey(key);
            Minecraft.getInstance().options.save();
        }
        listeningKey = false;
        return true;
    }

    static boolean captureMouse(int button) {
        if (!listeningKey) return false;
        BeezzCraftKeys.MODIFIER.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
        Minecraft.getInstance().options.save();
        listeningKey = false;
        return true;
    }

    private static String[] tips(String... keys) {
        return keys;
    }

    private interface Row {
        void render(GuiGraphics g, Font font, int x, int y, boolean hover);

        void click();

        String[] tips();
    }

    private record Toggle(ItemStack icon, Component label, boolean on, Runnable flip, String[] tips) implements Row {
        public void render(GuiGraphics g, Font font, int x, int y, boolean hover) {
            if (hover) Ui.round(g, x + 2, y, W - 4, ROW, Ui.PANEL_HI);
            g.renderItem(icon, x + 5, y + 1);
            g.drawString(font, font.plainSubstrByWidth(label.getString(), W - 52), x + 24, y + 5, Ui.TEXT, false);
            Ui.toggle(g, x + W - 22, y + 5, on);
        }

        public void click() {
            flip.run();
        }
    }

    private record Choice(ItemStack icon, Component label, Component value, Runnable next, String[] tips) implements Row {
        public void render(GuiGraphics g, Font font, int x, int y, boolean hover) {
            if (hover) Ui.round(g, x + 2, y, W - 4, ROW, Ui.PANEL_HI);
            g.renderItem(icon, x + 5, y + 1);
            g.drawString(font, font.plainSubstrByWidth(label.getString(), 72), x + 24, y + 5, Ui.TEXT, false);
            String v = font.plainSubstrByWidth(value.getString(), 66);
            g.drawString(font, v, x + W - 8 - font.width(v), y + 5, Ui.HONEY_SOFT, false);
        }

        public void click() {
            next.run();
        }
    }

    private static List<Row> rows() {
        Config c = Config.get();
        List<Row> rows = new ArrayList<>();
        rows.add(new Toggle(new ItemStack(Items.CLOCK), Tr.t("beezzcraft.set.queue"), c.queue, () -> {
            c.queue = !c.queue;
            if (!c.queue) Crafter.jobs().removeIf(j -> j.queued);
        }, tips("beezzcraft.set.queue.note1", "beezzcraft.set.queue.note2")));
        rows.add(new Toggle(new ItemStack(Items.SPYGLASS), Tr.t("beezzcraft.set.missing"), c.showMissing, () -> {
            c.showMissing = !c.showMissing;
            if (BeezzCraft.wheel != null) BeezzCraft.wheel.refresh();
        }, tips("beezzcraft.config.missing.tip1", "beezzcraft.config.missing.tip2", "beezzcraft.config.missing.tip3")));
        rows.add(new Toggle(new ItemStack(Items.IRON_PICKAXE), Tr.t("beezzcraft.set.needs"), c.showNeeds,
                () -> c.showNeeds = !c.showNeeds,
                tips("beezzcraft.config.needs.tip1", "beezzcraft.config.needs.tip2", "beezzcraft.config.needs.tip3")));
        rows.add(new Toggle(new ItemStack(Items.NOTE_BLOCK), Tr.t("beezzcraft.set.sound"), c.sound,
                () -> c.sound = !c.sound,
                tips("beezzcraft.config.sound.tip1", "beezzcraft.config.sound.tip2")));
        rows.add(new Toggle(new ItemStack(Items.NAME_TAG), Tr.t("beezzcraft.set.key_only"), c.keyOnly,
                () -> c.keyOnly = !c.keyOnly,
                tips("beezzcraft.config.key_only.line1", "beezzcraft.config.key_only.line2",
                        "beezzcraft.config.key_only.line3")));
        String[] clickTips = c.keyOnly ? tips("beezzcraft.config.click.off")
                : c.functionalClick == 0
                ? tips("beezzcraft.config.click.left.line1", "beezzcraft.config.click.left.line2")
                : tips("beezzcraft.config.click.right.line1", "beezzcraft.config.click.right.line2");
        rows.add(new Choice(new ItemStack(Items.STONE_BUTTON), Tr.t("beezzcraft.set.click"),
                Tr.t(c.functionalClick == 0 ? "beezzcraft.set.click.left" : "beezzcraft.set.click.right"),
                () -> c.functionalClick = c.functionalClick == 0 ? 1 : 0, clickTips));
        rows.add(new Choice(new ItemStack(Items.TRIPWIRE_HOOK), Tr.t("beezzcraft.set.key"),
                listeningKey ? Tr.t("beezzcraft.config.press") : BeezzCraftKeys.MODIFIER.getTranslatedKeyMessage(),
                () -> listeningKey = true,
                listeningKey ? tips("beezzcraft.config.key.press.tip") : tips("beezzcraft.config.key.tip")));
        rows.add(new Choice(new ItemStack(Items.COMPASS), Tr.t("beezzcraft.set.position"),
                Tr.t(Config.gearPositionKey(c.gearPosition)),
                () -> c.gearPosition = (c.gearPosition + 1) & 3,
                tips("beezzcraft.config.position.tip")));
        return rows;
    }

    // ------------------------------------------------------------------ layout

    private static boolean right() {
        int p = Config.get().gearPosition;
        return p == Config.GEAR_RIGHT_TOP || p == Config.GEAR_RIGHT_BOTTOM;
    }

    private static boolean bottom() {
        int p = Config.get().gearPosition;
        return p == Config.GEAR_LEFT_BOTTOM || p == Config.GEAR_RIGHT_BOTTOM;
    }

    private static int gearX(AbstractContainerScreen<?> screen) {
        return right() ? screen.width - GEAR - 6 : 6;
    }

    private static int gearY(AbstractContainerScreen<?> screen) {
        return bottom() ? screen.height - GEAR - 6 : 6;
    }

    private static int panelX(AbstractContainerScreen<?> screen) {
        return right() ? screen.width - W - 6 : 6;
    }

    private static int panelY(AbstractContainerScreen<?> screen) {
        return bottom() ? gearY(screen) - 6 - settingsH() : gearY(screen) + GEAR + 6;
    }

    /** The queue switch's explanation, wrapped to the panel. */
    private static List<net.minecraft.util.FormattedCharSequence> note() {
        Font font = Minecraft.getInstance().font;
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(font.split(Tr.t("beezzcraft.set.queue.note1"), W - 16));
        lines.addAll(font.split(Tr.t("beezzcraft.set.queue.note2"), W - 16));
        return lines;
    }

    private static int noteH() {
        return note().size() * 10 + 4;
    }

    private static int settingsH() {
        return 6 + rows().size() * ROW + noteH() + 4;
    }

    /** The queue stays in the top-left corner; only the default top-left gear pushes it down when open. */
    private static int queueY(AbstractContainerScreen<?> screen) {
        if (Config.get().gearPosition == Config.GEAR_LEFT_TOP) {
            return Y + GEAR + 6 + (settings ? settingsH() + 6 : 0);
        }
        return Y + GEAR + 6;
    }

    private static List<Crafter.Job> queued() {
        return Crafter.jobs().stream().filter(j -> j.queued).toList();
    }

    /** Height of the queue card: its rows, or the wrapped hint when it is empty. */
    private static int queueH() {
        int shown = Math.min(queued().size(), 8);
        if (shown > 0) return 22 + shown * QROW + 4;
        return 22 + Minecraft.getInstance().font.split(Tr.t("beezzcraft.queue.empty"), W - 14).size() * 10 + 6;
    }

    private static boolean showQueue() {
        return Config.get().queue || !queued().isEmpty();
    }

    // ------------------------------------------------------------------ drawing

    static void render(AbstractContainerScreen<?> screen, GuiGraphics g, Font font, int mx, int my) {
        int gx = gearX(screen), gy = gearY(screen);
        boolean gh = in(mx, my, gx, gy, GEAR, GEAR);
        Ui.panel(g, gx, gy, GEAR, GEAR);
        if (gh || settings) Ui.round(g, gx, gy, GEAR, GEAR, 0xFF3A3214);
        Ui.icon(g, Ui.GEAR, gx + 3, gy + 3, 14, gh || settings ? Ui.HONEY_SOFT : Ui.HONEY);
        int count = queued().size();
        if (count > 0) {
            String n = String.valueOf(count);
            Ui.round(g, gx + GEAR - 6, gy - 3, font.width(n) + 5, 10, 0xFF2563EB);
            g.drawString(font, n, gx + GEAR - 3, gy - 2, 0xFFFFFFFF, false);
        }

        if (settings) renderSettings(screen, g, font, mx, my);
        if (showQueue()) renderQueue(screen, g, font, mx, my);
        if (showQueue() && Config.get().showNeeds && !queued().isEmpty()) {
            Needs.render(g, font, Needs.of(Minecraft.getInstance().player, screen.getMenu()), X, queueY(screen) + queueH() + 6, W, true);
        }
        if (gh) Card.text(g, font, Tr.t("beezzcraft.gear"), mx, my, screen.width, screen.height);
    }

    private static void renderSettings(AbstractContainerScreen<?> screen, GuiGraphics g, Font font, int mx, int my) {
        int x = panelX(screen), y = panelY(screen), h = settingsH();
        Ui.panel(g, x, y, W, h);
        List<Row> rows = rows();
        Row hovered = null;
        int ry = y + 4;
        for (int i = 0; i < rows.size(); i++) {
            boolean hover = in(mx, my, x, ry, W, ROW);
            rows.get(i).render(g, font, x, ry, hover);
            if (hover) hovered = rows.get(i);
            ry += ROW;
            if (i == 0) {
                // what the queue does, right under its switch
                for (var line : note()) {
                    g.drawString(font, line, x + 8, ry, Ui.DIM, false);
                    ry += 10;
                }
                ry += 4;
            }
        }
        if (hovered != null) drawTooltip(g, font, hovered.tips(), mx, my);
    }

    private static void drawTooltip(GuiGraphics g, Font font, String[] keys, int mx, int my) {
        if (keys.length == 0) return;
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>(keys.length);
        for (String key : keys) lines.add(Tr.t(key).getVisualOrderText());
        g.renderTooltip(font, lines, mx, my);
    }

    private static void renderQueue(AbstractContainerScreen<?> screen, GuiGraphics g, Font font, int mx, int my) {
        List<Crafter.Job> jobs = queued();
        int shown = Math.min(jobs.size(), 8);
        int y = queueY(screen), h = queueH();
        Ui.panel(g, X, y, W, h);
        Ui.icon(g, Ui.QUEUE, X + 6, y + 5, 11, Ui.BLUE);
        g.drawString(font, Tr.t("beezzcraft.hud"), X + 21, y + 7, Ui.BLUE, false);
        int ry = y + 21;
        if (jobs.isEmpty()) {
            int ly = ry + 2;
            for (var line : font.split(Tr.t("beezzcraft.queue.empty"), W - 14)) {
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
                status = Tr.t("beezzcraft.queue.table");
                color = Ui.MUTED;
            } else if (screen.getMenu() instanceof RecipeBookMenu<?, ?> m && Crafter.canCraft(mc.player, m, j.option)) {
                status = Tr.t("beezzcraft.queue.ready");
                color = Ui.GREEN;
            } else {
                status = Tr.t("beezzcraft.queue.wait");
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
    static boolean click(AbstractContainerScreen<?> screen, double mx, double my, int button) {
        int gx = gearX(screen), gy = gearY(screen), px = panelX(screen), py = panelY(screen);
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return in(mx, my, gx, gy, GEAR, GEAR) || settings && in(mx, my, px, py, W, settingsH());
        }
        if (in(mx, my, gx, gy, GEAR, GEAR)) {
            settings = !settings;
            return true;
        }
        if (settings) {
            if (in(mx, my, px, py, W, settingsH())) {
                List<Row> rows = rows();
                int ry = py + 4;
                for (int i = 0; i < rows.size(); i++) {
                    if (in(mx, my, px, ry, W, ROW)) {
                        rows.get(i).click();
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
            int y = queueY(screen), shown = Math.min(jobs.size(), 8);
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
