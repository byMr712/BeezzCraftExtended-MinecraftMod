package land.beezz.craft;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Crafts through the vanilla recipe book, exactly like clicking a recipe in it: the server lays the recipe out in the
 * grid from the inventory, then the result is shift-clicked into the inventory. One step per tick, so it works on any
 * server. Queued jobs wait until the inventory has the items; they run only in the player's own inventory (or at a
 * crafting table for 3x3 recipes), never while a chest or another container is open.
 */
final class Crafter {

    static final class Job {
        final RecipeDisplayEntry entry;
        final ItemStack result;
        final boolean needsTable;
        /** Crafts left; ALL = as many as the resources allow. */
        int left;
        boolean all;
        /** Waits for resources instead of giving up. */
        final boolean queued;

        Job(Recipes.Option o, int count, boolean all, boolean queued) {
            this.entry = o.entry;
            this.result = o.result;
            this.needsTable = o.needsTable;
            this.left = count;
            this.all = all;
            this.queued = queued;
        }
    }

    private static final List<Job> jobs = new ArrayList<>();
    private static Job current;
    private static boolean placed;
    private static int wait;

    private Crafter() {}

    static List<Job> jobs() {
        return jobs;
    }

    static int queued(RecipeDisplayEntry entry) {
        int n = 0;
        for (Job j : jobs) if (j.queued && j.entry.id().equals(entry.id())) n += j.all ? 64 : j.left;
        return n;
    }

    static void craft(Recipes.Option o, int count, boolean all) {
        jobs.add(new Job(o, count, all, false));
    }

    /** Queues a recipe; the same recipe already waiting just gets more (one line «×3», not three «×1»). */
    static void enqueue(Recipes.Option o, int count, boolean all) {
        for (Job j : jobs) {
            if (j.queued && j != current && j.entry.id().equals(o.entry.id())) {
                j.left += count;
                j.all |= all;
                return;
            }
        }
        jobs.add(new Job(o, count, all, true));
    }

    static boolean unqueue(RecipeDisplayEntry entry) {
        return jobs.removeIf(j -> j.queued && j.entry.id().equals(entry.id()) && j != current);
    }

    static void clear() {
        jobs.clear();
        current = null;
        placed = false;
    }

    /** The menu crafting may use right now: the open crafting table, or the inventory (open or closed). */
    static AbstractContainerMenu menu(Minecraft mc) {
        Screen s = mc.gui.screen();
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
        if (!(menu instanceof AbstractCraftingMenu crafting) || !player.containerMenu.equals(menu)) return;
        if (!player.containerMenu.getCarried().isEmpty()) return; // something on the cursor: wait

        if (current == null) {
            current = pick(player, crafting);
            placed = false;
            wait = 0;
            if (current == null) return;
        }
        if (!jobs.contains(current) || (current.needsTable && !(menu instanceof CraftingMenu))) {
            current = null;
            return;
        }
        Slot result = crafting.getResultSlot();
        if (!placed) {
            if (!canCraft(player, crafting, current.entry)) {
                finish(player, current.queued ? null : Component.translatable("beezzcraft.missing", current.result.getHoverName()));
                return;
            }
            if (player.getInventory().getFreeSlot() < 0 && !hasRoomFor(player, current.result)) {
                finish(player, Component.translatable("beezzcraft.full"));
                return;
            }
            mc.gameMode.handlePlaceRecipe(menu.containerId, current.entry.id(), current.all);
            placed = true;
            wait = 0;
            return;
        }
        if (result.hasItem()) {
            mc.gameMode.handleContainerInput(menu.containerId, result.index, 0, ContainerInput.QUICK_MOVE, player);
            Config.get().crafted(new Recipes.Option(current.entry, current.result, current.needsTable).itemId());
            if (Config.get().sound) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4f));
            placed = false;
            if (current.all || --current.left <= 0) {
                jobs.remove(current);
                current = null;
            }
        } else if (++wait > 20) {
            placed = false; // the server did not place it (missing items after all, lag): try again later
            if (!current.queued) finish(player, Component.translatable("beezzcraft.missing", current.result.getHoverName()));
            else current = null;
        }
    }

    private static void finish(LocalPlayer player, Component message) {
        if (current != null && !current.queued) jobs.remove(current);
        current = null;
        placed = false;
        if (message != null) player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.RED));
    }

    /** The first job that can run here: in the inventory only 2x2 recipes, queued ones only when they can be crafted. */
    private static Job pick(LocalPlayer player, AbstractCraftingMenu menu) {
        for (Job j : jobs) {
            if (j.needsTable && !(menu instanceof CraftingMenu)) continue;
            if (!j.queued || canCraft(player, menu, j.entry)) return j;
        }
        return null;
    }

    static boolean canCraft(LocalPlayer player, AbstractCraftingMenu menu, RecipeDisplayEntry entry) {
        StackedItemContents contents = new StackedItemContents();
        player.getInventory().fillStackedContents(contents);
        menu.fillCraftSlotsStackedContents(contents);
        return entry.canCraft(contents);
    }

    private static boolean hasRoomFor(LocalPlayer player, ItemStack result) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(s, result) && s.getCount() + result.getCount() <= s.getMaxStackSize()) return true;
        }
        return false;
    }
}
