package land.beezz.craft;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What can be crafted from an item.
 * <p>
 * Every crafting recipe of the game is known, not only the ones the recipe book has unlocked (a torch only unlocks
 * once you hold a stone pickaxe, yet you can craft it by hand from the start): in singleplayer straight from the
 * server, in multiplayer from the vanilla data inside the game. Recipes the recipe book knows are crafted through it;
 * the others are laid out in the grid with ordinary clicks, like a player would.
 */
final class Recipes {

    /** One ingredient of a recipe: how many are needed and how many the player has. */
    record Need(ItemStack icon, int need, int have) {
        boolean missing() {
            return have < need;
        }
    }

    /** A crafting recipe as the mod sees it. */
    static final class Option {
        final ItemStack result;
        /** Every non-empty cell's ingredient (what is needed). */
        final List<Ingredient> reqs;
        /** Grid layout for clicking it in by hand: shaped = width x height cells (empty = none); shapeless: width 0. */
        final List<Optional<Ingredient>> cells;
        final int width, height;
        final boolean needsTable;
        /** The recipe itself, for the craftable check (1.21.1 StackedContents works on recipes, not ingredients). */
        final Recipe<?> recipe;
        /** The recipe book entry, when the book knows this recipe (then it is crafted through the book). */
        RecipeHolder<?> entry;
        boolean craftable;
        List<Need> needs = List.of();
        /** Where its ingredients sit in the inventory, top left first: the variant made from the first wood wins. */
        int rank;

        Option(Recipe<?> recipe, ItemStack result, List<Ingredient> reqs, List<Optional<Ingredient>> cells, int width, int height, boolean needsTable) {
            this.recipe = recipe;
            this.result = result;
            this.reqs = reqs;
            this.cells = cells;
            this.width = width;
            this.height = height;
            this.needsTable = needsTable;
        }

        String itemId() {
            return BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        }

        /** Same recipe? (result item, how many, how many ingredients) */
        String key() {
            return itemId() + "*" + result.getCount() + "/" + reqs.size();
        }

        /** Something to tell this recipe apart in the queue (book id when there is one). */
        Object id() {
            return entry != null ? entry.id() : key();
        }
    }

    private Recipes() {}

    /** Grid size of the menu: 3 at a crafting table, 2 in the inventory. */
    static int gridSize(AbstractContainerMenu menu) {
        return menu instanceof RecipeBookMenu<?, ?> c ? Math.min(c.getGridWidth(), c.getGridHeight()) : 2;
    }

    /** Shaped and shapeless recipes, if they fit the grid. */
    static boolean fits(Recipe<?> r, int grid) {
        if (r instanceof ShapedRecipe s) return s.getWidth() <= grid && s.getHeight() <= grid;
        if (r instanceof ShapelessRecipe s) return s.getIngredients().size() <= grid * grid;
        return false;
    }

    // ------------------------------------------------------------------ all recipes of the game

    private static List<Option> catalog;
    private static Object catalogFor;

    /** Every shaped and shapeless crafting recipe, read once per world. */
    static List<Option> catalog(Minecraft mc) {
        if (catalog != null && catalogFor == mc.getConnection()) return catalog;
        List<Option> out = new ArrayList<>();
        HolderLookup.Provider registries = mc.level.registryAccess();
        IntegratedServer server = mc.getSingleplayerServer();
        boolean fromServer = server != null && !Boolean.getBoolean("beezzcraft.vanillaData"); // the flag: test the multiplayer way
        try {
            if (fromServer) {
                for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) add(out, h.value(), registries);
            } else {
                RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
                var files = mc.getResourceManager().listResources("recipe", id -> true);
                for (var entry : files.entrySet()) {
                    try (InputStream in = entry.getValue().open()) {
                        Recipe.CODEC.parse(ops, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                                .ifSuccess(r -> add(out, r, registries));
                    } catch (Exception ignored) {
                        // one odd recipe must not hide the others
                    }
                }
            }
        } catch (Exception e) {
            BeezzCraft.LOG.warn("BeezzCraft: cannot read the recipes: {}", e.toString());
        }
        BeezzCraft.LOG.info("BeezzCraft: {} crafting recipes known ({})", out.size(), fromServer ? "singleplayer" : "vanilla data");
        catalog = out;
        catalogFor = mc.getConnection();
        return out;
    }

    private static void add(List<Option> out, Recipe<?> r, HolderLookup.Provider registries) {
        if (r instanceof ShapedRecipe s) {
            ItemStack result = r.getResultItem(registries);
            if (result.isEmpty()) return;
            List<Ingredient> reqs = new ArrayList<>();
            List<Optional<Ingredient>> cells = new ArrayList<>();
            for (Ingredient ing : s.getIngredients()) {
                if (ing.isEmpty()) {
                    cells.add(Optional.empty());
                } else {
                    reqs.add(ing);
                    cells.add(Optional.of(ing));
                }
            }
            out.add(new Option(r, result, reqs, cells, s.getWidth(), s.getHeight(), s.getWidth() > 2 || s.getHeight() > 2));
        } else if (r instanceof ShapelessRecipe s) {
            ItemStack result = r.getResultItem(registries);
            if (result.isEmpty()) return;
            List<Ingredient> reqs = new ArrayList<>();
            for (Ingredient ing : s.getIngredients()) if (!ing.isEmpty()) reqs.add(ing);
            out.add(new Option(r, result, reqs, reqs.stream().map(Optional::of).toList(), 0, 0, reqs.size() > 4));
        }
    }

    static void forget() {
        catalog = null;
    }

    // ------------------------------------------------------------------ what this item makes

    /** Every crafting recipe that uses this item and fits the grid; the recipe book's entries attached where known. */
    static List<Option> uses(Minecraft mc, ItemStack stack, int grid) {
        HolderLookup.Provider registries = mc.level.registryAccess();
        Map<String, Option> byKey = new HashMap<>();
        List<Option> out = new ArrayList<>();
        for (Option o : catalog(mc)) {
            if (o.needsTable && grid < 3) continue;
            if (o.reqs.stream().noneMatch(i -> i.test(stack))) continue;
            Option copy = new Option(o.recipe, o.result, o.reqs, o.cells, o.width, o.height, o.needsTable);
            out.add(copy);
            byKey.putIfAbsent(copy.key(), copy);
        }
        // the recipe book: attach its entries (crafting through the book is cheaper); recipes only it knows are added
        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeHolder<?> holder : collection.getRecipes()) {
                if (!mc.player.getRecipeBook().contains(holder)) continue;
                Recipe<?> r = holder.value();
                if (!fits(r, grid)) continue;
                NonNullList<Ingredient> list = r.getIngredients();
                List<Ingredient> reqs = new ArrayList<>();
                for (Ingredient ing : list) if (!ing.isEmpty()) reqs.add(ing);
                if (reqs.isEmpty()) continue;
                if (reqs.stream().noneMatch(i -> i.test(stack))) continue;
                ItemStack result = r.getResultItem(registries);
                if (result.isEmpty()) continue;
                Option book = new Option(r, result, reqs, List.of(), -1, -1, !fits(r, 2));
                Option known = byKey.get(book.key());
                if (known != null) {
                    if (known.entry == null) known.entry = holder;
                } else {
                    book.entry = holder;
                    out.add(book);
                    byKey.put(book.key(), book);
                }
            }
        }
        return out;
    }

    /** Fills craftable / needs / rank for the current inventory (and the items already in the crafting grid). */
    static void refresh(Player player, AbstractContainerMenu menu, List<Option> options) {
        StackedContents contents = stacked(player, menu);
        List<ItemStack> pool = pool(player, menu);
        for (Option o : options) {
            o.craftable = contents.canCraft(o.recipe, null);
            o.needs = needs(o.reqs, pool);
            o.rank = rank(player, o.reqs);
        }
    }

    /** How many times the recipe can be crafted from the inventory and the grid right now (by the scarcest ingredient). */
    static int timesPossible(Player player, AbstractContainerMenu menu, Option o) {
        int times = Integer.MAX_VALUE;
        for (Need n : needs(o.reqs, pool(player, menu))) times = Math.min(times, n.have() / n.need());
        return times == Integer.MAX_VALUE ? 0 : times;
    }

    static boolean canCraft(Player player, AbstractContainerMenu menu, Option o) {
        return stacked(player, menu).canCraft(o.recipe, null);
    }

    /** 1.21.1 counts craftability against the recipe object; the inventory and the crafting grid are the stock. */
    private static StackedContents stacked(Player player, AbstractContainerMenu menu) {
        StackedContents contents = new StackedContents();
        player.getInventory().fillStackedContents(contents);
        if (menu instanceof RecipeBookMenu<?, ?> c) c.fillCraftSlotsStackedContents(contents);
        return contents;
    }

    /** The grid slots of a crafting menu: they follow the result slot. */
    private static List<Slot> gridSlots(RecipeBookMenu<?, ?> menu) {
        int first = menu.getResultSlotIndex() + 1, n = menu.getGridWidth() * menu.getGridHeight();
        List<Slot> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(menu.getSlot(first + i));
        return out;
    }

    /** How many items short this recipe is; ingredients the player has none of at all count double. */
    static int missing(Option o) {
        int n = 0;
        for (Need need : o.needs) {
            if (need.missing()) n += (need.need() - need.have()) * (need.have() == 0 ? 2 : 1);
        }
        return n;
    }

    /**
     * Inventory order as seen on screen: the top row left to right, down to the hotbar at the bottom.
     * @return 0.. for main slots 9..35, then the hotbar 0..8
     */
    static int screenOrder(int inventoryIndex) {
        return inventoryIndex >= 9 ? inventoryIndex - 9 : 27 + inventoryIndex;
    }

    /** Sum of the screen positions of the first stack matching each ingredient: lower = taken from further up-left. */
    private static int rank(Player player, List<Ingredient> reqs) {
        Inventory inv = player.getInventory();
        int sum = 0;
        for (Ingredient ing : reqs) {
            int best = 100;
            for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
                if (!inv.getItem(i).isEmpty() && ing.test(inv.getItem(i))) best = Math.min(best, screenOrder(i));
            }
            sum += best;
        }
        return sum;
    }

    /** The ingredients of a recipe with what the player has (for the «what to get» list). */
    static List<Need> needsOf(Player player, AbstractContainerMenu menu, List<Ingredient> reqs) {
        return needs(reqs, pool(player, menu));
    }

    /** Copies of the stacks the player can craft with: the main inventory and the crafting grid. */
    private static List<ItemStack> pool(Player player, AbstractContainerMenu menu) {
        List<ItemStack> pool = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (!inv.getItem(i).isEmpty()) pool.add(inv.getItem(i).copy());
        }
        if (menu instanceof RecipeBookMenu<?, ?> c) {
            for (Slot s : gridSlots(c)) if (s.hasItem()) pool.add(s.getItem().copy());
        }
        return pool;
    }

    /** Ingredients grouped (the same ingredient in several cells counts together), with what the player has. */
    private static List<Need> needs(List<Ingredient> reqs, List<ItemStack> pool) {
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
                ItemStack[] items = ing.getItems();
                icon = items.length > 0 ? items[0].copyWithCount(1) : ItemStack.EMPTY;
            }
            out.add(new Need(icon, counts.get(i), have));
        }
        return out;
    }

    static Component name(ItemStack stack) {
        return stack.getHoverName();
    }
}
