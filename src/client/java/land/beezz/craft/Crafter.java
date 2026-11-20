package land.beezz.craft;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Crafts a recipe, one batch per tick, on any server.
 * <ul>
 *   <li>Recipes in the recipe book: through the book, like clicking it there (the server lays the recipe out), then the
 *       result is shift-clicked into the inventory.</li>
 *   <li>Other recipes (a torch before you ever held a stone pickaxe): laid out with ordinary clicks, taking the items
 *       from the inventory top left first, like a player would.</li>
 * </ul>
 * Queued jobs wait until the inventory has the items; they run only in the player's own inventory (or at a crafting
 * table for 3x3 recipes), never while a chest or another container is open.
 */
final class Crafter {
    /** Hand-laid crafts per click at most: each costs a few clicks, servers limit how many clicks per second. */
    private static final int HAND_MAX = 16;

    static final class Job {
        final Recipes.Option option;
        final ItemStack result;
        final boolean needsTable;
        /** Crafts left. */
        int left;
        /** As many as the resources allow. */
        boolean all;
        /** Waits for resources instead of giving up. */
        final boolean queued;

        Job(Recipes.Option o, int count, boolean all, boolean queued) {
            this.option = o;
            this.result = o.result;
            this.needsTable = o.needsTable;
            this.left = count;
            this.all = all;
            this.queued = queued;
        }

        boolean same(Recipes.Option o) {
            return option.id().equals(o.id());
        }
    }

    private static final List<Job> jobs = new ArrayList<>();
    private static Job current;
    private static int cooldown;

    private Crafter() {}

    static List<Job> jobs() {
        return jobs;
    }

    static int queued(Recipes.Option o) {
        int n = 0;
        for (Job j : jobs) if (j.queued && j.same(o)) n += j.all ? 64 : j.left;
        return n;
    }

    private static boolean creative() {
        return BeezzCraft.screen(Minecraft.getInstance()) instanceof CreativeModeInventoryScreen;
    }

    private static void creativeMessage() {
        BeezzCraft.showNotice(Tr.t("beezzcraft.creative"));
    }

    static void craft(Recipes.Option o, int count, boolean all) {
        if (creative()) {
            creativeMessage();
            return;
        }
        jobs.add(new Job(o, count, all, false));
    }

    /** Queues a recipe; the same recipe already waiting just gets more (one line «×3», not three «×1»). */
    static void enqueue(Recipes.Option o, int count, boolean all) {
        if (creative()) {
            creativeMessage();
            return;
        }
        for (Job j : jobs) {
            if (j.queued && j != current && j.same(o)) {
                j.left += count;
                j.all |= all;
                return;
            }
        }
        jobs.add(new Job(o, count, all, true));
    }

    static boolean unqueue(Recipes.Option o) {
        return jobs.removeIf(j -> j.queued && j.same(o) && j != current);
    }

    static void clear() {
        jobs.clear();
        current = null;
        cooldown = 0;
    }

    /** The menu crafting may use right now: the open crafting table, or the inventory (open or closed). */
    static AbstractContainerMenu menu(Minecraft mc) {
        Screen s = BeezzCraft.screen(mc);
        if (s instanceof CraftingScreen cs) return cs.getMenu();
        if (s == null || s instanceof InventoryScreen) return mc.player.inventoryMenu;
        return null; // a chest or another container is open: leave it alone
    }

    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            clear();
            return;
        }
        if (jobs.isEmpty()) return;
        AbstractContainerMenu menu = menu(mc);
        if (!(menu instanceof RecipeBookMenu<?, ?> crafting) || !player.containerMenu.equals(menu)) return;
        if (!player.containerMenu.getCarried().isEmpty()) return; // something on the cursor: wait
        if (cooldown > 0) {
            cooldown--; // let the server's answer to the last batch arrive before counting the inventory again
            return;
        }
        current = pick(player, crafting);
        if (current == null) return;
        Recipes.Option o = current.option;
        if (!Recipes.canCraft(player, crafting, o)) {
            finish(player, current.queued ? null : Tr.t("beezzcraft.missing", current.result.getHoverName()));
            return;
        }
        if (player.getInventory().getFreeSlot() < 0 && !hasRoomFor(player, current.result)) {
            finish(player, Tr.t("beezzcraft.full"));
            return;
        }
        int possible = Recipes.timesPossible(player, crafting, o);
        boolean book = o.entry != null;
        int times;
        if (book) {
            // the whole batch in one tick: the server handles the packets in order (lay out, take, lay out, take...)
            times = current.all ? 1 : Math.max(1, Math.min(current.left, possible));
            Slot result = crafting.getSlot(crafting.getResultSlotIndex());
            for (int i = 0; i < times; i++) {
                mc.gameMode.handlePlaceRecipe(menu.containerId, o.entry, current.all);
                mc.gameMode.handleInventoryMouseClick(menu.containerId, result.index, 0, ClickType.QUICK_MOVE, player);
            }
        } else if (o.width >= 0) {
            times = Math.max(1, Math.min(current.all ? possible : Math.min(current.left, possible), HAND_MAX));
            byHand(mc, player, crafting, o, times);
        } else {
            finish(player, Tr.t("beezzcraft.missing", current.result.getHoverName()));
            return;
        }
        Config.get().crafted(o.itemId());
        if (Config.get().sound) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
        current.left -= times;
        if (current.all || current.left <= 0 || !current.queued) jobs.remove(current); // a click does what it can, once
        current = null;
        cooldown = 2;
    }

    /**
     * Lays the recipe out with clicks, the same as a player: clears the grid, then for each cell picks up a matching
     * stack (inventory top left first), right-clicks «times» items into the cell, puts the rest back; then shift-clicks
     * the result. The client plays every click locally too, so the screen stays right.
     */
    private static void byHand(Minecraft mc, LocalPlayer player, RecipeBookMenu<?, ?> menu, Recipes.Option o, int times) {
        int id = menu.containerId;
        List<Slot> grid = gridSlots(menu);
        int gw = menu.getGridWidth();
        for (Slot s : grid) if (s.hasItem()) mc.gameMode.handleInventoryMouseClick(id, s.index, 0, ClickType.QUICK_MOVE, player);

        // which grid slot gets which ingredient
        List<Slot> targets = new ArrayList<>();
        List<Ingredient> wants = new ArrayList<>();
        if (o.width > 0) {
            for (int r = 0; r < o.height; r++) {
                for (int c = 0; c < o.width; c++) {
                    Optional<Ingredient> ing = o.cells.get(r * o.width + c);
                    if (ing.isEmpty()) continue;
                    targets.add(grid.get(r * gw + c));
                    wants.add(ing.get());
                }
            }
        } else {
            for (int i = 0; i < o.reqs.size(); i++) {
                targets.add(grid.get(i));
                wants.add(o.reqs.get(i));
            }
        }

        // the player's own slots as the screen shows them: top row first, the hotbar last
        List<Slot> sources = new ArrayList<>();
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory && s.getContainerSlot() < Inventory.INVENTORY_SIZE) sources.add(s);
        }
        sources.sort(Comparator.<Slot>comparingInt(s -> s.y).thenComparingInt(s -> s.x));
        int[] left = new int[sources.size()];
        for (int i = 0; i < sources.size(); i++) left[i] = sources.get(i).getItem().getCount();

        for (int t = 0; t < targets.size(); t++) {
            int need = times;
            for (int i = 0; i < sources.size() && need > 0; i++) {
                ItemStack st = sources.get(i).getItem();
                if (left[i] <= 0 || st.isEmpty() || !wants.get(t).test(st)) continue;
                int put = Math.min(need, left[i]);
                Slot src = sources.get(i);
                mc.gameMode.handleInventoryMouseClick(id, src.index, 0, ClickType.PICKUP, player); // take the stack
                for (int k = 0; k < put; k++) {
                    mc.gameMode.handleInventoryMouseClick(id, targets.get(t).index, 1, ClickType.PICKUP, player); // one item
                }
                mc.gameMode.handleInventoryMouseClick(id, src.index, 0, ClickType.PICKUP, player); // the rest back
                left[i] -= put;
                need -= put;
            }
        }
        mc.gameMode.handleInventoryMouseClick(id, menu.getSlot(menu.getResultSlotIndex()).index, 0, ClickType.QUICK_MOVE, player);
    }

    private static void finish(LocalPlayer player, Component message) {
        if (current != null && !current.queued) jobs.remove(current);
        current = null;
        if (message != null) player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }

    /** The first job that can run here: in the inventory only 2x2 recipes, queued ones only when they can be crafted. */
    private static Job pick(LocalPlayer player, RecipeBookMenu<?, ?> menu) {
        for (Job j : jobs) {
            if (j.needsTable && !(menu instanceof CraftingMenu)) continue;
            if (!j.queued || Recipes.canCraft(player, menu, j.option)) return j;
        }
        return null;
    }

    static boolean canCraft(LocalPlayer player, RecipeBookMenu<?, ?> menu, Recipes.Option o) {
        return Recipes.canCraft(player, menu, o);
    }

    /** The grid slots of a crafting menu: they follow the result slot. */
    private static List<Slot> gridSlots(RecipeBookMenu<?, ?> menu) {
        int first = menu.getResultSlotIndex() + 1, n = menu.getGridWidth() * menu.getGridHeight();
        List<Slot> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(menu.getSlot(first + i));
        return out;
    }

    private static boolean hasRoomFor(LocalPlayer player, ItemStack result) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(s, result) && s.getCount() + result.getCount() <= s.getMaxStackSize()) return true;
        }
        return false;
    }
}
