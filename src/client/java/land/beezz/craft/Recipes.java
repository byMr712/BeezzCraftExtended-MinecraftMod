package land.beezz.craft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What can be crafted from an item. Only the recipes the player has unlocked are known to the client (the recipe
 * book), which is also what a vanilla server lets the recipe book place. Grid size: 2x2 in the inventory, 3x3 at a
 * crafting table.
 */
final class Recipes {

    /** One ingredient of a recipe: how many are needed and how many the player has. */
    record Need(ItemStack icon, int need, int have) {
        boolean missing() {
            return have < need;
        }
    }

    /** One sector of the wheel. */
    static final class Option {
        final RecipeDisplayEntry entry;
        final ItemStack result;
        final boolean needsTable;
        boolean craftable;
        List<Need> needs = List.of();

        Option(RecipeDisplayEntry entry, ItemStack result, boolean needsTable) {
            this.entry = entry;
            this.result = result;
            this.needsTable = needsTable;
        }

        String itemId() {
            return BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        }
    }

    private Recipes() {}

    static ContextMap context(Minecraft mc) {
        return SlotDisplayContext.fromLevel(mc.level);
    }

    /** Grid size of the menu: 3 at a crafting table, 2 in the inventory. */
    static int gridSize(AbstractContainerMenu menu) {
        return menu instanceof AbstractCraftingMenu c ? Math.min(c.getGridWidth(), c.getGridHeight()) : 2;
    }

    static boolean fits(RecipeDisplay display, int grid) {
        if (display instanceof ShapedCraftingRecipeDisplay s) return s.width() <= grid && s.height() <= grid;
        if (display instanceof ShapelessCraftingRecipeDisplay s) return s.ingredients().size() <= grid * grid;
        return false;
    }

    private static boolean twoByTwo(RecipeDisplay display) {
        return fits(display, 2);
    }

    /** Every crafting recipe in the recipe book that uses this item and fits the grid. */
    static List<Option> uses(Minecraft mc, ItemStack stack, int grid) {
        ContextMap ctx = context(mc);
        List<Option> out = new ArrayList<>();
        Set<Object> seen = new LinkedHashSet<>();
        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                RecipeDisplay display = entry.display();
                if (!fits(display, grid) || entry.craftingRequirements().isEmpty()) continue;
                if (entry.craftingRequirements().get().stream().noneMatch(i -> i.test(stack))) continue;
                if (!seen.add(entry.id())) continue;
                List<ItemStack> results = entry.resultItems(ctx);
                if (results.isEmpty() || results.getFirst().isEmpty()) continue;
                out.add(new Option(entry, results.getFirst(), !twoByTwo(display)));
            }
        }
        return out;
    }

    /** Fills craftable / needs for the current inventory (and the items already in the crafting grid). */
    static void refresh(Player player, AbstractContainerMenu menu, List<Option> options) {
        StackedItemContents contents = new StackedItemContents();
        player.getInventory().fillStackedContents(contents);
        if (menu instanceof AbstractCraftingMenu c) c.fillCraftSlotsStackedContents(contents);
        List<ItemStack> pool = pool(player, menu);
        for (Option o : options) {
            o.craftable = o.entry.canCraft(contents);
            o.needs = needs(o.entry, pool);
        }
    }

    /**
     * Sort: craftable first; then the grey ones that lack the fewest items (a torch short of one coal comes before a
     * fence of a wood you never had); within each, the recently crafted, then by name.
     */
    static void sort(List<Option> options) {
        List<String> recent = Config.get().recent;
        options.sort(Comparator.<Option>comparingInt(o -> o.craftable ? 0 : 1)
                .thenComparingInt(Recipes::missing)
                .thenComparingInt(o -> {
                    int i = recent.indexOf(o.itemId());
                    return i < 0 ? Integer.MAX_VALUE : i;
                })
                .thenComparing(o -> o.result.getHoverName().getString()));
    }

    /** How many times the recipe can be crafted from the inventory and the grid right now (by the scarcest ingredient). */
    static int timesPossible(Player player, AbstractContainerMenu menu, RecipeDisplayEntry entry) {
        int times = Integer.MAX_VALUE;
        for (Need n : needs(entry, pool(player, menu))) times = Math.min(times, n.have() / n.need());
        return times == Integer.MAX_VALUE ? 0 : times;
    }

    /** How many items short this recipe is; ingredients the player has none of at all count double. */
    static int missing(Option o) {
        int n = 0;
        for (Need need : o.needs) {
            if (need.missing()) n += (need.need() - need.have()) * (need.have() == 0 ? 2 : 1);
        }
        return n;
    }

    /** Copies of the stacks the player can craft with: the main inventory and the crafting grid. */
    private static List<ItemStack> pool(Player player, AbstractContainerMenu menu) {
        List<ItemStack> pool = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (!inv.getItem(i).isEmpty()) pool.add(inv.getItem(i).copy());
        }
        if (menu instanceof AbstractCraftingMenu c) {
            for (Slot s : c.getInputGridSlots()) if (s.hasItem()) pool.add(s.getItem().copy());
        }
        return pool;
    }

    /** Ingredients grouped (the same ingredient in several cells counts together), with what the player has. */
    private static List<Need> needs(RecipeDisplayEntry entry, List<ItemStack> pool) {
        List<Ingredient> reqs = entry.craftingRequirements().orElse(List.of());
        List<Ingredient> kinds = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Ingredient ing : reqs) {
            int i = kinds.indexOf(ing);
            if (i < 0) {
                kinds.add(ing);
                counts.add(1);
            } else {
                counts.set(i, counts.get(i) + 1);
            }
        }
        List<Need> out = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            Ingredient ing = kinds.get(i);
            int have = 0;
            ItemStack icon = ItemStack.EMPTY;
            for (ItemStack s : pool) {
                if (ing.test(s)) {
                    have += s.getCount();
                    if (icon.isEmpty()) icon = s.copyWithCount(1);
                }
            }
            if (icon.isEmpty()) {
                icon = ing.items().findFirst().map(h -> new ItemStack(h.value())).orElse(ItemStack.EMPTY);
            }
            out.add(new Need(icon, counts.get(i), have));
        }
        return out;
    }

    static Component name(ItemStack stack) {
        return stack.getHoverName();
    }
}
