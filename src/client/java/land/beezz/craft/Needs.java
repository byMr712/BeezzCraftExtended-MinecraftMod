package land.beezz.craft;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What to go and get: every ingredient of the craft queue added up (a torch ×12 needs 12 coal), minus what the
 * inventory already has. Shown under the queue in the inventory and on the screen while playing.
 */
final class Needs {

    /** One resource: how many the whole queue needs, how many you have. */
    record Item(Ingredient ingredient, ItemStack icon, int need, int have) {
        int missing() {
            return Math.max(0, need - have);
        }
    }

    private Needs() {}

    static List<Item> of(LocalPlayer player, AbstractContainerMenu menu) {
        List<Ingredient> kinds = new ArrayList<>();
        List<Integer> totals = new ArrayList<>();
        List<Recipes.Need> seen = new ArrayList<>();
        for (Crafter.Job j : Crafter.jobs()) {
            if (!j.queued) continue;
            int times = j.all ? 1 : j.left;
            List<Ingredient> kindsOfJob = new ArrayList<>();
            List<Integer> countsOfJob = new ArrayList<>();
            for (Ingredient ing : j.option.reqs) {
                int i = kindsOfJob.indexOf(ing);
                if (i < 0) {
                    kindsOfJob.add(ing);
                    countsOfJob.add(1);
                } else {
                    countsOfJob.set(i, countsOfJob.get(i) + 1);
                }
            }
            List<Recipes.Need> needs = Recipes.needsOf(player, menu, j.option.reqs);
            for (int i = 0; i < kindsOfJob.size(); i++) {
                Ingredient ing = kindsOfJob.get(i);
                int k = kinds.indexOf(ing);
                int add = countsOfJob.get(i) * times;
                if (k < 0) {
                    kinds.add(ing);
                    totals.add(add);
                    seen.add(needs.get(i));
                } else {
                    totals.set(k, totals.get(k) + add);
                }
            }
        }
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            Recipes.Need n = seen.get(i);
            out.add(new Item(kinds.get(i), n.icon(), totals.get(i), n.have()));
        }
        out.sort(Comparator.comparingInt(Item::missing).reversed());
        return out;
    }

    /**
     * The card: a pickaxe, «what to get», then the resources as icons with how many are missing (red) or a tick.
     * compact: only what is missing, in one row (the HUD while playing).
     * @return its height
     */
    static int render(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, List<Item> items,
                      int x, int y, int w, boolean full) {
        List<Item> show = full ? items : items.stream().filter(i -> i.missing() > 0).limit(9).toList();
        if (show.isEmpty() && !full) return 0;
        int cols = full ? Math.max(1, (w - 10) / 22) : Math.min(show.size(), 9);
        int rows = show.isEmpty() ? 1 : (show.size() + cols - 1) / cols;
        int width = full ? w : 24 + cols * 22;
        int h = full ? 20 + rows * 26 + 2 : 22;
        Ui.panel(g, x, y, width, h);
        if (full) {
            g.renderItem(new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE), x + 4, y + 2);
            g.drawString(font, net.minecraft.network.chat.Component.translatable("beezzcraft.needs.title"), x + 22, y + 6, Ui.HONEY_SOFT, false);
            if (show.isEmpty() || !anyMissing(show)) {
                g.drawString(font, net.minecraft.network.chat.Component.translatable("beezzcraft.needs.ok"), x + 7, y + 26, Ui.GREEN, false);
                return h;
            }
        } else {
            g.renderItem(new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE), x + 3, y + 3);
        }
        for (int i = 0; i < show.size(); i++) {
            Item it = show.get(i);
            int cx = full ? x + 6 + (i % cols) * 22 : x + 22 + i * 22, cy = full ? y + 20 + (i / cols) * 26 : y + 3;
            g.renderItem(it.icon(), cx, cy);
            if (full) {
                String s = it.missing() > 0 ? "-" + it.missing() : "✔";
                g.drawString(font, s, cx + 8 - font.width(s) / 2, cy + 17, it.missing() > 0 ? Ui.RED : Ui.GREEN, false);
            } else {
                String s = String.valueOf(it.missing());
                g.drawString(font, s, cx + 17 - font.width(s), cy + 9, Ui.RED, true);
            }
        }
        return h;
    }

    static boolean anyMissing(List<Item> items) {
        for (Item i : items) if (i.missing() > 0) return true;
        return false;
    }
}
