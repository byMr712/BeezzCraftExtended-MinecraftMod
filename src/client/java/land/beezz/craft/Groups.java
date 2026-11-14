package land.beezz.craft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a long list of recipes into a tidy wheel:
 * <ul>
 *   <li>variants of the same thing become one button: a fence of each wood, a banner of each colour, and the tiers of a
 *       tool (wooden, stone, copper, iron, golden, diamond pickaxe). The mouse wheel over it switches between them;</li>
 *   <li>the button shows the variant you crafted last, else one you can make (from the wood further up-left in the
 *       inventory), else the one you lack the least for;</li>
 *   <li>what is popular or what you craft often comes first.</li>
 * </ul>
 */
final class Groups {

    /** Material tiers, in the order they stand on the wheel. */
    private static final String[] TIERS = {"wooden", "leather", "stone", "chainmail", "copper", "iron", "golden", "diamond", "netherite"};
    /** Variant prefixes (longest first, so dark_oak is not read as oak). */
    private static final String[] VARIANTS = {
            "dark_oak", "pale_oak", "oak", "spruce", "birch", "jungle", "acacia", "mangrove", "cherry", "bamboo", "crimson", "warped",
            "light_gray", "light_blue", "white", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green", "cyan", "blue",
            "purple", "magenta", "pink"};
    /**
     * Families everybody makes all the time, most wanted first (tools before decorations). What the player crafts
     * often is added on top of this (Config.counts).
     */
    private static final List<String> POPULAR = List.of(
            "pickaxe", "axe", "sword", "shovel", "torch", "crafting_table", "chest", "furnace", "ladder", "bed", "door", "trapdoor",
            "planks", "stick", "slab", "stairs", "fence", "fence_gate", "sign", "boat", "bow", "arrow", "shield", "bucket", "hoe",
            "spear", "helmet", "chestplate", "leggings", "boots", "fishing_rod", "crossbow", "shears", "flint_and_steel", "rail",
            "powered_rail", "lever", "button", "pressure_plate", "barrel", "smoker", "blast_furnace", "campfire", "lantern",
            "bread", "cake", "banner", "carpet", "wool", "bundle");

    /** One button: the variants of one thing, with the one shown right now. */
    static final class Group {
        final List<Recipes.Option> members = new ArrayList<>();
        final String key, family;
        final int tier;
        int sel;

        Group(String key, String family, int tier) {
            this.key = key;
            this.family = family;
            this.tier = tier;
        }

        Recipes.Option current() {
            return members.get(sel);
        }

        boolean craftable() {
            for (Recipes.Option o : members) if (o.craftable) return true;
            return false;
        }

        /** Shows the next (or previous) variant. */
        void cycle(int step) {
            sel = Math.floorMod(sel + step, members.size());
        }

        /** The variant to show first: the last crafted, else one you can craft (top left wood first), else the closest. */
        void pickBest() {
            String last = Config.get().last.get(family);
            for (int i = 0; i < members.size(); i++) {
                if (members.get(i).itemId().equals(last)) {
                    sel = i;
                    return;
                }
            }
            int best = 0;
            for (int i = 1; i < members.size(); i++) {
                Recipes.Option a = members.get(i), b = members.get(best);
                boolean better = a.craftable != b.craftable ? a.craftable
                        : a.craftable ? a.rank < b.rank : Recipes.missing(a) < Recipes.missing(b);
                if (better) best = i;
            }
            sel = best;
        }
    }

    private Groups() {}

    private static String path(Recipes.Option o) {
        String id = o.itemId();
        return id.substring(id.indexOf(':') + 1);
    }

    /** The id without its wood or colour: oak_fence and birch_fence share "fence". */
    static String variantKey(String path) {
        for (String v : VARIANTS) {
            if (path.startsWith(v + "_")) return path.substring(v.length() + 1);
        }
        return path;
    }

    /** -1 when the item has no material tier. */
    static int tier(String key) {
        for (int i = 0; i < TIERS.length; i++) if (key.startsWith(TIERS[i] + "_")) return i;
        return -1;
    }

    /** The thing itself, without wood, colour or material: iron_pickaxe → pickaxe. */
    static String family(String path) {
        String key = variantKey(path);
        int t = tier(key);
        return t < 0 ? key : key.substring(TIERS[t].length() + 1);
    }

    /** Groups the recipes and orders the buttons. Call after Recipes.refresh so the best variants can be picked. */
    static List<Group> build(List<Recipes.Option> options) {
        Map<String, Group> byKey = new LinkedHashMap<>();
        for (Recipes.Option o : options) {
            String family = family(path(o));
            Group g = byKey.computeIfAbsent(family, k -> new Group(k, k, -1));
            g.members.add(o);
        }
        List<Group> groups = new ArrayList<>(byKey.values());
        for (Group g : groups) {
            // tiers in order (wooden → diamond), then the woods / colours by name
            g.members.sort(Comparator.<Recipes.Option>comparingInt(o -> tier(variantKey(path(o)))).thenComparing(o -> path(o)));
            g.pickBest();
        }
        // a family is as wanted as its most wanted member: any craftable, how often you made it, how popular it is
        Map<String, Boolean> familyCraftable = new HashMap<>();
        for (Group g : groups) familyCraftable.merge(g.family, g.craftable(), Boolean::logicalOr);
        Map<String, Integer> counts = Config.get().counts;
        groups.sort(Comparator.<Group>comparingInt(g -> familyCraftable.get(g.family) ? 0 : 1)
                .thenComparingInt(g -> -counts.getOrDefault(g.family, 0))
                .thenComparingInt(g -> {
                    int i = POPULAR.indexOf(g.family);
                    return i < 0 ? POPULAR.size() : i;
                })
                .thenComparing(g -> g.family)
                .thenComparingInt(g -> g.tier)
                .thenComparing(g -> g.key));
        return groups;
    }
}
