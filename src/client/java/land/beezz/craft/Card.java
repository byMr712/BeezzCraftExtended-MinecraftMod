package land.beezz.craft;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The card next to the mouse in the wheel: the result, its ingredients as icons (green tick = enough, red = how many
 * are missing) and what the clicks do, drawn with mouse and key pictures instead of a wall of text.
 */
final class Card {
    private static final int PAD = 6, CELL = 22;

    private Card() {}

    static void recipe(GuiGraphics g, Font font, Groups.Group group, int mx, int my, int sw, int sh) {
        Recipes.Option o = group.current();
        int variants = group.members.size();
        Component other = Tr.t("beezzcraft.card.variant", group.sel + 1, variants);
        Component name = o.result.getHoverName();
        String count = o.result.getCount() > 1 ? " ×" + o.result.getCount() : "";
        List<Recipes.Need> needs = o.needs;
        int queued = Crafter.queued(o);
        boolean queueOn = Config.get().queue;

        Component lmb = Tr.t(o.craftable ? "beezzcraft.card.once" : queueOn ? "beezzcraft.card.queue" : "beezzcraft.card.missing");
        Component max = Tr.t("beezzcraft.card.max");
        String ten = "×" + Config.get().ctrlCount;
        int hintsW = o.craftable
                ? 12 + font.width(lmb) + 8 + keyW(font, "Ctrl") + 2 + font.width(ten) + 8 + keyW(font, "Shift") + 2 + font.width(max)
                : (queueOn ? 12 : 0) + font.width(lmb) + (queueOn ? 8 + keyW(font, "Ctrl") + 2 + font.width(ten) : 0);
        Component queuedText = Tr.t("beezzcraft.card.queued", queued), remove = Tr.t("beezzcraft.card.remove");
        int queuedW = queued > 0 ? 11 + font.width(queuedText) + 8 + 12 + font.width(remove) : 0;
        int stripW = variants > 1 ? Math.min(variants, 12) * 13 + 4 + 12 + font.width(other) : 0;
        int w = Math.max(Math.max(Math.max(20 + font.width(name) + font.width(count), needs.size() * CELL), Math.max(hintsW, queuedW)), stripW) + PAD * 2;
        int h = PAD + 18 + 4 + 16 + 21 + 6 + 12 + (queued > 0 ? 13 : 0) + (variants > 1 ? 16 : 0) + PAD - 4;

        int x = mx + 14, y = my - 8;
        if (x + w > sw - 4) x = mx - 14 - w;
        y = Math.max(4, Math.min(sh - h - 4, y));
        g.nextStratum();
        Ui.backdrop(g, x - 6, y - 6, w + 12, h + 12);
        Ui.panel(g, x, y, w, h);
        if (o.craftable) Ui.rect(g, x, y, w, 1, 0x90FACC15);

        // result
        int cx = x + PAD, cy = y + PAD;
        g.renderItem(o.result, cx, cy);
        g.drawString(font, name, cx + 20, cy + 4, o.craftable ? Ui.HONEY_SOFT : Ui.TEXT, false);
        if (!count.isEmpty()) g.drawString(font, count, cx + 20 + font.width(name), cy + 4, Ui.MUTED, false);
        cy += 18 + 4;

        // the variants inside this button (woods, colours): the shown one underlined, the craftable ones bright
        if (variants > 1) {
            int vx = cx;
            for (int i = 0; i < Math.min(variants, 12); i++) {
                Recipes.Option v = group.members.get(i);
                g.pose().pushMatrix();
                g.pose().translate(vx, cy - 2);
                g.pose().scale(0.75f, 0.75f);
                g.renderItem(v.result, 0, 0);
                g.pose().popMatrix();
                if (i == group.sel) Ui.rect(g, vx, cy + 11, 12, 1, Ui.HONEY);
                else if (!v.craftable) Ui.rect(g, vx, cy - 2, 12, 12, 0x8014161C);
                vx += 13;
            }
            vx += 4;
            Ui.icon(g, Ui.MOUSE_WHEEL, vx, cy - 1, 12, 0xFFFFFFFF);
            g.drawString(font, other, vx + 12, cy + 1, Ui.MUTED, false);
            cy += 16;
        }

        // ingredients: icon, how many are needed, and a tick or what is missing
        Ui.rect(g, x + PAD, cy - 3, w - PAD * 2, 1, 0x40FFFFFF);
        for (int i = 0; i < needs.size(); i++) {
            Recipes.Need n = needs.get(i);
            int ix = cx + i * CELL;
            g.renderItem(n.icon(), ix, cy);
            String c = String.valueOf(n.need());
            g.drawString(font, c, ix + 17 - font.width(c), cy + 9, 0xFFFFFFFF, true);
            String s = n.missing() ? "-" + (n.need() - n.have()) : "✔";
            g.drawString(font, s, ix + 1, cy + 17, n.missing() ? Ui.RED : Ui.GREEN, false);
        }
        cy += 16 + 21 + 6;

        // what the clicks do
        int tx = cx;
        if (o.craftable) {
            Ui.icon(g, Ui.MOUSE_LEFT, tx, cy - 1, 12, 0xFFFFFFFF);
            tx += 12;
            g.drawString(font, lmb, tx, cy + 1, Ui.TEXT, false);
            tx += font.width(lmb) + 8;
            tx += Ui.key(g, font, "Ctrl", tx, cy) + 2;
            g.drawString(font, ten, tx, cy + 1, Ui.TEXT, false);
            tx += font.width(ten) + 8;
            tx += Ui.key(g, font, "Shift", tx, cy) + 2;
            g.drawString(font, max, tx, cy + 1, Ui.TEXT, false);
        } else if (queueOn) {
            Ui.icon(g, Ui.MOUSE_LEFT, tx, cy - 1, 12, 0xFFFFFFFF);
            tx += 12;
            g.drawString(font, lmb, tx, cy + 1, Ui.BLUE, false);
            tx += font.width(lmb) + 8;
            tx += Ui.key(g, font, "Ctrl", tx, cy) + 2;
            g.drawString(font, ten, tx, cy + 1, Ui.BLUE, false);
        } else {
            g.drawString(font, lmb, tx, cy + 1, Ui.RED, false);
        }
        if (queued > 0) {
            cy += 13;
            Ui.icon(g, Ui.QUEUE, cx, cy, 10, Ui.BLUE);
            g.drawString(font, queuedText, cx + 11, cy + 1, Ui.BLUE, false);
            int rx = cx + 11 + font.width(queuedText) + 8;
            Ui.icon(g, Ui.MOUSE_RIGHT, rx, cy - 1, 12, 0xFFFFFFFF);
            g.drawString(font, remove, rx + 12, cy + 1, Ui.TEXT, false);
        }
    }

    private static int keyW(Font font, String name) {
        return font.width(name) + 6;
    }

    static void text(GuiGraphics g, Font font, Component text, int mx, int my, int sw, int sh) {
        int w = font.width(text) + 10, h = 14;
        int x = Math.min(sw - w - 4, mx + 12), y = Math.max(4, Math.min(sh - h - 4, my - 7));
        g.nextStratum();
        Ui.backdrop(g, x - 6, y - 6, w + 12, h + 12);
        Ui.panel(g, x, y, w, h);
        g.drawString(font, text, x + 5, y + 3, Ui.TEXT, false);
    }

    static void item(GuiGraphics g, ItemStack s, int x, int y) {
        g.renderItem(s, x, y);
    }
}
